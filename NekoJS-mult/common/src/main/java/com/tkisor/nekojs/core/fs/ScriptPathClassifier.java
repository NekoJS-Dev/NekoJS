package com.tkisor.nekojs.core.fs;

import com.tkisor.nekojs.api.ScriptType;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Internal path-segment classifier; all script/package cleanup uses the injected provider. */
final class ScriptPathClassifier {
    private ScriptPathClassifier() {}

    static ScriptType fromSegment(Path segment) {
        if (segment == null) return null;
        FileSystem fileSystem = segment.getFileSystem();
        Path candidate = fileSystem.getPath(segment.toString());
        for (ScriptType type : ScriptType.all()) {
            if (candidate.equals(fileSystem.getPath(type.scriptsDirectoryName()))) {
                return type;
            }
        }
        return null;
    }

    /**
     * Classify only a flat script root or one of the three explicit pack layouts.
     *
     * <p>In particular, this must not be a search for any {@code *_scripts} segment:
     * shared module trees such as {@code node_modules/foo/server_scripts} are not script
     * roots and must remain unclassified.</p>
     */
    static ScriptType fromPath(Path path) {
        if (path == null) return null;
        List<Path> segments = segments(path);
        for (int index = 0; index < segments.size(); index++) {
            ScriptType type = fromSegment(segments.get(index));
            if (type != null && isRecognizedLayout(segments, index)) return type;
        }
        return null;
    }

    /** Return the stable authored identity, retaining package prefixes. */
    static String authoredPath(Path path) {
        if (path == null) return null;
        List<Path> segments = segments(path);
        for (int index = 0; index < segments.size(); index++) {
            if (fromSegment(segments.get(index)) == null || !isRecognizedLayout(segments, index)) continue;
            int start = layoutStart(segments, index);
            StringBuilder result = new StringBuilder();
            for (int part = start; part < segments.size(); part++) {
                if (result.length() > 0) result.append('/');
                result.append(segments.get(part));
            }
            return result.toString();
        }
        return path.toString().replace('\\', '/');
    }

    static String authoredPathText(String pathText, FileSystem fileSystem) {
        if (pathText == null || pathText.isBlank() || fileSystem == null) return null;
        String normalized = pathText.replace('\\', '/');
        String[] rawSegments = normalized.split("/");
        for (int scriptsIndex = 0; scriptsIndex < rawSegments.length; scriptsIndex++) {
            String scriptSegment = rawSegments[scriptsIndex];
            ScriptType type;
            try {
                type = fromSegment(fileSystem.getPath(scriptSegment));
            } catch (RuntimeException ignored) {
                continue;
            }
            if (type == null || hasTextNameBefore(rawSegments, scriptsIndex, "node_modules", fileSystem)) continue;
            int markerIndex = lastTextMarkerIndex(rawSegments, scriptsIndex, fileSystem);
            if (markerIndex >= 0) {
                String marker = rawSegments[markerIndex];
                int requiredPrefix = sameName(fileSystem, marker, "server_packs") ? 3 : 2;
                if (scriptsIndex - markerIndex != requiredPrefix) continue;
                return join(rawSegments, markerIndex);
            }
            if (!(scriptsIndex == 0 || scriptsIndex == 1
                    || scriptsIndex > 0 && sameName(fileSystem, rawSegments[scriptsIndex - 1], "nekojs"))) {
                continue;
            }
            return join(rawSegments, scriptsIndex);
        }
        return null;
    }

    private static boolean isRecognizedLayout(List<Path> segments, int scriptsIndex) {
        if (hasNameBefore(segments, scriptsIndex, "node_modules")) return false;

        int packsIndex = lastMarkerIndex(segments, scriptsIndex);
        if (packsIndex >= 0) {
            int requiredPrefix = isName(segments.get(packsIndex), "server_packs") ? 3 : 2;
            return scriptsIndex - packsIndex == requiredPrefix;
        }
        // Without a pack marker, accept only the actual script root: a direct root child or
        // the injected NekoJS root. A nested vendor/module directory is deliberately unknown;
        // otherwise authoredPath would silently merge two packages with the same script file.
        return scriptsIndex == 0 || scriptsIndex == 1
                || scriptsIndex > 0 && isName(segments.get(scriptsIndex - 1), "nekojs");
    }

    private static int layoutStart(List<Path> segments, int scriptsIndex) {
        int packsIndex = lastMarkerIndex(segments, scriptsIndex);
        return packsIndex >= 0 ? packsIndex : scriptsIndex;
    }

    private static int lastMarkerIndex(List<Path> segments, int scriptsIndex) {
        for (int index = scriptsIndex - 1; index >= 0; index--) {
            if (isName(segments.get(index), "packs") || isName(segments.get(index), "nekojs_packs")
                    || isName(segments.get(index), "server_packs")) {
                return index;
            }
            if (isName(segments.get(index), "node_modules")) return -1;
        }
        return -1;
    }

    private static boolean hasNameBefore(List<Path> segments, int end, String expected) {
        for (int index = 0; index < end; index++) {
            if (isName(segments.get(index), expected)) return true;
        }
        return false;
    }

    private static boolean hasTextNameBefore(String[] segments, int end, String expected,
                                             FileSystem fileSystem) {
        for (int index = 0; index < end; index++) {
            if (sameName(fileSystem, segments[index], expected)) return true;
        }
        return false;
    }

    private static int lastTextMarkerIndex(String[] segments, int scriptsIndex, FileSystem fileSystem) {
        for (int index = scriptsIndex - 1; index >= 0; index--) {
            if (sameName(fileSystem, segments[index], "packs")
                    || sameName(fileSystem, segments[index], "nekojs_packs")
                    || sameName(fileSystem, segments[index], "server_packs")) return index;
        }
        return -1;
    }

    private static String join(String[] segments, int start) {
        StringBuilder result = new StringBuilder();
        for (int index = start; index < segments.length; index++) {
            if (segments[index].isEmpty()) continue;
            if (result.length() > 0) result.append('/');
            result.append(segments[index]);
        }
        return result.toString();
    }

    private static String name(Path segment) {
        return segment.toString().replace('\\', '/');
    }

    private static boolean isName(Path segment, String expected) {
        return segment != null && segment.equals(segment.getFileSystem().getPath(expected));
    }

    private static boolean sameName(FileSystem fileSystem, String actual, String expected) {
        try {
            return fileSystem.getPath(actual).equals(fileSystem.getPath(expected));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static List<Path> segments(Path path) {
        List<Path> result = new ArrayList<>();
        for (Path segment : path) result.add(segment);
        return result;
    }
}
