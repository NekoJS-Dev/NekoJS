package com.tkisor.nekojs.core.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class NekoSourceMapBuilder {
    private static final String VLQ_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private final Path file;
    private final String source;
    private final List<Mapping> mappings = new ArrayList<>();

    private NekoSourceMapBuilder(Path file, String source) {
        this.file = file;
        this.source = source == null ? "" : source;
    }

    static String identity(Path file, String source, String generated) {
        NekoSourceMapBuilder builder = new NekoSourceMapBuilder(file, source);
        String text = generated == null ? "" : generated;
        int generatedLine = 0;
        int originalLine = 0;
        builder.add(0, 0, 0, 0);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                generatedLine++;
                originalLine++;
                builder.add(generatedLine, 0, originalLine, 0);
            }
        }
        return builder.build();
    }

    static Emitter emitter(Path file, String source) {
        return new Emitter(new NekoSourceMapBuilder(file, source));
    }

    private void add(int generatedLine, int generatedColumn, int originalLine, int originalColumn) {
        mappings.add(new Mapping(generatedLine, generatedColumn, originalLine, originalColumn));
    }

    private String build() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 3);
        root.addProperty("file", displayName(file));

        JsonArray sources = new JsonArray();
        sources.add(displayName(file));
        root.add("sources", sources);

        JsonArray sourcesContent = new JsonArray();
        sourcesContent.add(source);
        root.add("sourcesContent", sourcesContent);

        root.add("names", new JsonArray());
        root.addProperty("mappings", encodeMappings());
        return root.toString();
    }

    private String encodeMappings() {
        mappings.sort(Comparator
                .comparingInt(Mapping::generatedLine)
                .thenComparingInt(Mapping::generatedColumn)
                .thenComparingInt(Mapping::originalLine)
                .thenComparingInt(Mapping::originalColumn));

        StringBuilder out = new StringBuilder();
        int currentGeneratedLine = 0;
        int previousGeneratedColumn = 0;
        int previousSourceIndex = 0;
        int previousOriginalLine = 0;
        int previousOriginalColumn = 0;
        boolean firstInLine = true;
        Mapping previous = null;

        for (Mapping mapping : mappings) {
            if (previous != null
                    && previous.generatedLine == mapping.generatedLine
                    && previous.generatedColumn == mapping.generatedColumn
                    && previous.originalLine == mapping.originalLine
                    && previous.originalColumn == mapping.originalColumn) {
                continue;
            }
            while (currentGeneratedLine < mapping.generatedLine) {
                out.append(';');
                currentGeneratedLine++;
                previousGeneratedColumn = 0;
                firstInLine = true;
            }
            if (!firstInLine) {
                out.append(',');
            }
            encodeVlq(out, mapping.generatedColumn - previousGeneratedColumn);
            encodeVlq(out, -previousSourceIndex);
            encodeVlq(out, mapping.originalLine - previousOriginalLine);
            encodeVlq(out, mapping.originalColumn - previousOriginalColumn);

            previousGeneratedColumn = mapping.generatedColumn;
            previousSourceIndex = 0;
            previousOriginalLine = mapping.originalLine;
            previousOriginalColumn = mapping.originalColumn;
            firstInLine = false;
            previous = mapping;
        }
        return out.toString();
    }

    private static void encodeVlq(StringBuilder out, int value) {
        int vlq = value < 0 ? ((-value) << 1) + 1 : value << 1;
        do {
            int digit = vlq & 31;
            vlq >>>= 5;
            if (vlq > 0) {
                digit |= 32;
            }
            out.append(VLQ_CHARS.charAt(digit));
        } while (vlq > 0);
    }

    private static String displayName(Path path) {
        if (path == null) {
            return "unknown.js";
        }
        try {
            return NekoJSPaths.ROOT.relativize(path.normalize().toAbsolutePath()).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return path.toString().replace('\\', '/');
        }
    }

    static final class Emitter {
        private final NekoSourceMapBuilder builder;
        private final StringBuilder out = new StringBuilder();
        private int generatedLine;
        private int generatedColumn;

        private Emitter(NekoSourceMapBuilder builder) {
            this.builder = builder;
        }

        void addMapping(int originalOffset) {
            Position original = position(builder.source, originalOffset);
            builder.add(generatedLine, generatedColumn, original.line(), original.column());
        }

        void append(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            out.append(text);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    generatedLine++;
                    generatedColumn = 0;
                } else {
                    generatedColumn++;
                }
            }
        }

        void appendOriginalRange(int start, int end) {
            if (end <= start) {
                return;
            }
            addMapping(start);
            for (int i = start; i < end; i++) {
                char c = builder.source.charAt(i);
                append(String.valueOf(c));
                if (c == '\n' && i + 1 < end) {
                    addMapping(i + 1);
                }
            }
        }

        void appendMapped(String text, List<MappingPoint> mappings) {
            if (text == null || text.isEmpty()) {
                return;
            }
            List<MappingPoint> sorted = mappings == null ? List.of() : mappings.stream()
                    .sorted(Comparator.comparingInt(MappingPoint::generatedOffset))
                    .toList();
            int index = 0;
            for (MappingPoint mapping : sorted) {
                int offset = Math.max(0, Math.min(mapping.generatedOffset(), text.length()));
                if (offset < index) {
                    continue;
                }
                append(text.substring(index, offset));
                addMapping(mapping.originalOffset());
                index = offset;
            }
            append(text.substring(index));
        }

        String code() {
            return out.toString();
        }

        String sourceMap() {
            return builder.build();
        }
    }

    private static Position position(String text, int offset) {
        int safeOffset = Math.max(0, Math.min(offset, text.length()));
        int line = 0;
        int column = 0;
        for (int i = 0; i < safeOffset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new Position(line, column);
    }

    record MappingPoint(int generatedOffset, int originalOffset) {}

    private record Mapping(int generatedLine, int generatedColumn, int originalLine, int originalColumn) {}

    private record Position(int line, int column) {}
}
