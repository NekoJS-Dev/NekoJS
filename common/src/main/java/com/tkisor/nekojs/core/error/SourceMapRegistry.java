package com.tkisor.nekojs.core.error;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SourceMapRegistry {
    private static final String VLQ_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final Map<String, NormalizedSourceMap> MAPPINGS_MAP = new ConcurrentHashMap<>();

    public static void register(String scriptPath, String sourceMapJson) {
        register(scriptPath, sourceMapJson, 0);
    }

    public static void register(String scriptPath, String sourceMapJson, int prependedLineCount) {
        if (scriptPath == null) return;
        String generatedPath = normalizeLookupPath(scriptPath);
        NormalizedSourceMap sourceMap = parse(generatedPath, sourceMapJson, prependedLineCount);
        MAPPINGS_MAP.put(generatedPath, sourceMap);
        if (sourceMap.file != null && !sourceMap.file.isBlank()) {
            MAPPINGS_MAP.put(sourceMap.file, sourceMap);
        }
    }

    public static OriginalPosition getMappedPosition(String scriptPath, int jsLine, int jsColumn) {
        OriginalPosition fallback = new OriginalPosition(jsLine, jsColumn, null);
        if (scriptPath == null || MAPPINGS_MAP.isEmpty()) return fallback;

        String query = normalizeLookupPath(scriptPath);
        NormalizedSourceMap mapping = MAPPINGS_MAP.get(query);

        if (mapping == null) {
            for (Map.Entry<String, NormalizedSourceMap> entry : MAPPINGS_MAP.entrySet()) {
                if (entry.getKey().endsWith(query) || query.endsWith(entry.getKey())) {
                    mapping = entry.getValue();
                    break;
                }
            }
        }

        return mapping == null ? fallback : mapping.map(jsLine, jsColumn);
    }

    public static void clear() {
        MAPPINGS_MAP.clear();
    }

    public static void clear(String scriptPath) {
        if (scriptPath == null) return;
        String query = normalizeLookupPath(scriptPath);
        MAPPINGS_MAP.entrySet().removeIf(entry -> entry.getKey().equals(query) || entry.getValue().matchesGeneratedPath(query));
    }

    public static void clearByPathPrefix(String pathPrefix) {
        if (pathPrefix == null) return;
        String lower = normalizeLookupPath(pathPrefix).toLowerCase();
        MAPPINGS_MAP.entrySet().removeIf(entry -> entry.getKey().toLowerCase().contains(lower)
                || entry.getValue().generatedPath.toLowerCase().contains(lower));
    }

    private static NormalizedSourceMap parse(String generatedPath, String sourceMapJson, int prependedLineCount) {
        if (sourceMapJson == null || sourceMapJson.isBlank()) {
            return NormalizedSourceMap.empty(generatedPath, prependedLineCount);
        }
        try {
            JsonElement rootElement = JsonParser.parseString(sourceMapJson);
            if (!rootElement.isJsonObject()) {
                return NormalizedSourceMap.empty(generatedPath, prependedLineCount);
            }
            JsonObject root = rootElement.getAsJsonObject();
            if (root.has("sections")) {
                return NormalizedSourceMap.empty(generatedPath, prependedLineCount);
            }

            String file = normalizeSourcePath(generatedPath, null, stringMember(root, "file"));
            String sourceRoot = stringMember(root, "sourceRoot");
            List<String> sources = stringArray(root, "sources").stream()
                    .map(source -> normalizeSourcePath(generatedPath, sourceRoot, source))
                    .toList();
            List<String> sourcesContent = nullableStringArray(root, "sourcesContent");
            List<String> names = stringArray(root, "names");
            String mappings = stringMember(root, "mappings");

            if (mappings == null || mappings.isEmpty()) {
                return new NormalizedSourceMap(generatedPath, file, prependedLineCount, List.of(), sources, sourcesContent, names);
            }
            return new NormalizedSourceMap(generatedPath, file, prependedLineCount, parseMappings(mappings, sources.size(), names.size()), sources, sourcesContent, names);
        } catch (Exception e) {
            return NormalizedSourceMap.empty(generatedPath, prependedLineCount);
        }
    }

    private static List<List<MappingEntry>> parseMappings(String mappings, int sourceCount, int nameCount) {
        String[] generatedLines = mappings.split(";", -1);
        List<List<MappingEntry>> lineMappings = new ArrayList<>(generatedLines.length);

        int sourceIndex = 0;
        int originalLine = 0;
        int originalColumn = 0;
        int nameIndex = 0;

        for (String generatedLine : generatedLines) {
            List<MappingEntry> entries = new ArrayList<>();
            lineMappings.add(entries);
            if (generatedLine.isEmpty()) {
                continue;
            }

            int generatedColumn = 0;
            for (String segment : generatedLine.split(",")) {
                if (segment.isEmpty()) continue;
                List<Integer> values = decodeVlq(segment);
                if (values.isEmpty()) continue;

                generatedColumn += values.get(0);
                if (values.size() < 4) {
                    continue;
                }

                sourceIndex += values.get(1);
                originalLine += values.get(2);
                originalColumn += values.get(3);

                int entryNameIndex = -1;
                if (values.size() >= 5) {
                    nameIndex += values.get(4);
                    if (nameIndex >= 0 && nameIndex < nameCount) {
                        entryNameIndex = nameIndex;
                    }
                }

                if (sourceIndex >= 0 && sourceIndex < sourceCount && originalLine >= 0 && originalColumn >= 0) {
                    entries.add(new MappingEntry(generatedColumn, sourceIndex, originalLine, originalColumn, entryNameIndex));
                }
            }
        }
        return lineMappings;
    }

    private static List<Integer> decodeVlq(String str) {
        List<Integer> result = new ArrayList<>();
        int value = 0;
        int shift = 0;
        for (int i = 0; i < str.length(); i++) {
            int c = VLQ_CHARS.indexOf(str.charAt(i));
            if (c < 0) {
                result.clear();
                return result;
            }
            value |= (c & 31) << shift;
            if ((c & 32) == 0) {
                int decoded = value >> 1;
                result.add((value & 1) != 0 ? -decoded : decoded);
                value = 0;
                shift = 0;
            } else {
                shift += 5;
            }
        }
        return result;
    }

    private static String stringMember(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    private static List<String> stringArray(JsonObject object, String member) {
        JsonElement value = object.get(member);
        if (value == null || !value.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        JsonArray array = value.getAsJsonArray();
        for (JsonElement element : array) {
            if (element != null && !element.isJsonNull() && element.isJsonPrimitive()) {
                result.add(element.getAsString());
            }
        }
        return result;
    }

    private static List<String> nullableStringArray(JsonObject object, String member) {
        JsonElement value = object.get(member);
        if (value == null || !value.isJsonArray()) return List.of();
        List<String> result = new ArrayList<>();
        JsonArray array = value.getAsJsonArray();
        for (JsonElement element : array) {
            result.add(element == null || element.isJsonNull() || !element.isJsonPrimitive() ? null : element.getAsString());
        }
        return result;
    }

    private static String normalizeLookupPath(String path) {
        String normalized = path.replace('\\', '/');
        String rootUri = NekoJSPaths.get().root().toUri().toString();
        if (normalized.startsWith(rootUri)) {
            normalized = normalized.substring(rootUri.length());
        }
        return trimLeadingSlash(normalized);
    }

    private static String normalizeSourcePath(String generatedPath, String sourceRoot, String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String sourceText = source.replace('\\', '/');
        if (sourceRoot != null && !sourceRoot.isBlank() && !isAbsoluteOrUri(sourceText)) {
            sourceText = trimTrailingSlash(sourceRoot.replace('\\', '/')) + "/" + sourceText;
        }

        String pathFromUri = normalizeFileUri(sourceText);
        if (pathFromUri != null) {
            return pathFromUri;
        }

        if (isAbsolutePath(sourceText)) {
            return normalizeAbsolutePath(sourceText);
        }

        String normalized = sourceText;
        try {
            Path sourcePath = Path.of(sourceText).normalize();
            normalized = sourcePath.toString().replace('\\', '/');
        } catch (Exception ignored) { // Path.of parse may fail for non-path source text; keep raw
        }

        if (isRootRelative(normalized)) {
            return normalized;
        }

        String generatedDir = generatedDirectory(generatedPath);
        if (!generatedDir.isBlank()) {
            try {
                String resolved = Path.of(generatedDir).resolve(normalized).normalize().toString().replace('\\', '/');
                if (!resolved.startsWith("../") && !resolved.equals("..")) {
                    return trimLeadingSlash(resolved);
                }
            } catch (Exception ignored) { // resolve may fail for unresolvable relative paths; skip
            }
        }
        return normalized.startsWith("../") || normalized.equals("..") ? null : trimLeadingSlash(normalized);
    }

    private static String normalizeFileUri(String sourceText) {
        if (!sourceText.startsWith("file:")) {
            return null;
        }
        try {
            return normalizeAbsolutePath(Path.of(URI.create(sourceText)).toString().replace('\\', '/'));
        } catch (Exception ignored) { // URI.create fails for non-file-URI source texts
            return null;
        }
    }

    private static String normalizeAbsolutePath(String sourceText) {
        try {
            Path path = Path.of(sourceText).normalize().toAbsolutePath();
            Path root = NekoJSPaths.get().root().normalize().toAbsolutePath();
            if (path.startsWith(root)) {
                return root.relativize(path).toString().replace('\\', '/');
            }
            return path.toString().replace('\\', '/');
        } catch (Exception ignored) { // Path.of may fail for non-path strings; fallback to simple replace
            return sourceText.replace('\\', '/');
        }
    }

    private static boolean isAbsoluteOrUri(String path) {
        return path.contains("://") || path.startsWith("file:") || isAbsolutePath(path);
    }

    private static boolean isAbsolutePath(String path) {
        return path.startsWith("/") || path.matches("^[A-Za-z]:/.*");
    }

    private static boolean isRootRelative(String path) {
        return path.startsWith("startup_scripts/")
                || path.startsWith("server_scripts/")
                || path.startsWith("client_scripts/")
                || path.startsWith("test_scripts/");
    }

    private static String generatedDirectory(String generatedPath) {
        if (generatedPath == null) return "";
        int slash = generatedPath.lastIndexOf('/');
        return slash < 0 ? "" : generatedPath.substring(0, slash);
    }

    private static String trimLeadingSlash(String value) {
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private record NormalizedSourceMap(
            String generatedPath,
            String file,
            int prependedLineCount,
            List<List<MappingEntry>> lineMappings,
            List<String> sources,
            List<String> sourcesContent,
            List<String> names
    ) {
        static NormalizedSourceMap empty(String generatedPath, int prependedLineCount) {
            return new NormalizedSourceMap(generatedPath, null, prependedLineCount, List.of(), List.of(), List.of(), List.of());
        }

        boolean matchesGeneratedPath(String path) {
            return generatedPath.equals(path) || (file != null && file.equals(path));
        }

        OriginalPosition map(int jsLine, int jsColumn) {
            int adjustedLine = jsLine - Math.max(0, prependedLineCount);
            OriginalPosition adjustedFallback = new OriginalPosition(adjustedLine > 0 ? adjustedLine : jsLine, jsColumn, null);
            int lineIndex = adjustedLine - 1;
            if (lineIndex < 0 || lineIndex >= lineMappings.size()) {
                return adjustedFallback;
            }

            List<MappingEntry> entries = lineMappings.get(lineIndex);
            if (entries == null || entries.isEmpty()) {
                return adjustedFallback;
            }

            int targetColumn = Math.max(0, jsColumn - 1);
            MappingEntry bestMatch = entries.get(0);
            for (MappingEntry entry : entries) {
                if (entry.generatedColumn <= targetColumn) {
                    bestMatch = entry;
                } else {
                    break;
                }
            }

            String path = bestMatch.sourceIndex >= 0 && bestMatch.sourceIndex < sources.size() ? sources.get(bestMatch.sourceIndex) : null;
            String sourceContent = bestMatch.sourceIndex >= 0 && bestMatch.sourceIndex < sourcesContent.size() ? sourcesContent.get(bestMatch.sourceIndex) : null;
            String name = bestMatch.nameIndex >= 0 && bestMatch.nameIndex < names.size() ? names.get(bestMatch.nameIndex) : null;
            return new OriginalPosition(path, bestMatch.originalLine + 1, bestMatch.originalColumn + 1, name, sourceContent);
        }
    }

    private record MappingEntry(int generatedColumn, int sourceIndex, int originalLine, int originalColumn, int nameIndex) {}

    public static class OriginalPosition {
        public final String path;
        public final int line;
        public final int column;
        public final String name;
        public final String sourceContent;

        public OriginalPosition(int line, int column, String name) {
            this(null, line, column, name, null);
        }

        public OriginalPosition(String path, int line, int column, String name, String sourceContent) {
            this.path = path;
            this.line = line;
            this.column = column;
            this.name = name;
            this.sourceContent = sourceContent;
        }

        @Override
        public String toString() {
            String position = (path == null || path.isBlank() ? "" : path + ":") + line + ":" + column;
            if (name != null) return name + " (" + position + ")";
            return position;
        }
    }
}
