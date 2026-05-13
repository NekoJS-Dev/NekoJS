package com.tkisor.nekojs.core.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VLQ SourceMap 映射注册表。
 * <p>
 * 纯算法类，负责解析 SourceMap JSON 中的 VLQ 编码映射，
 * 将编译后 JavaScript 的行列号映射回原始 TypeScript 源码位置。
 * </p>
 */
public class SourceMapRegistry {

    private static final Pattern MAPPINGS_PATTERN = Pattern.compile("\"mappings\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAMES_PATTERN = Pattern.compile("\"names\"\\s*:\\s*\\[(.*?)\\]");

    private static final Map<String, ScriptMapping> MAPPINGS_MAP = new ConcurrentHashMap<>();

    /**
     * 注册 SourceMap（传入完整的 sourceMapJson）
     */
    public static void register(String scriptPath, String sourceMapJson) {
        register(scriptPath, sourceMapJson, 0);
    }

    public static void register(String scriptPath, String sourceMapJson, int prependedLineCount) {
        if (scriptPath == null) return;

        try {
            String normalizedPath = scriptPath.replace('\\', '/');
            String mappings = "";
            String[] names = new String[0];

            if (sourceMapJson != null && !sourceMapJson.isEmpty()) {
                Matcher mapMatcher = MAPPINGS_PATTERN.matcher(sourceMapJson);
                if (mapMatcher.find()) mappings = mapMatcher.group(1);

                Matcher nameMatcher = NAMES_PATTERN.matcher(sourceMapJson);
                if (nameMatcher.find()) {
                    String namesStr = nameMatcher.group(1);
                    if (!namesStr.trim().isEmpty()) {
                        String[] rawNames = namesStr.split(",");
                        names = new String[rawNames.length];
                        for (int i = 0; i < rawNames.length; i++) {
                            names[i] = rawNames[i].trim().replaceAll("^\"|\"$", "");
                        }
                    }
                }
            }

            ScriptMapping mapping = mappings.isEmpty()
                    ? new ScriptMapping(List.of(), prependedLineCount)
                    : parseMappings(mappings, names, prependedLineCount);
            MAPPINGS_MAP.put(normalizedPath, mapping);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ScriptMapping parseMappings(String mappings, String[] names, int prependedLineCount) {
        String[] jsLines = mappings.split(";", -1);
        List<List<MappingEntry>> lineMappings = new ArrayList<>(jsLines.length);

        int sourceLine = 0;
        int sourceColumn = 0;
        int nameIndex = 0;

        for (String jsLineStr : jsLines) {
            List<MappingEntry> currentLineEntries = new ArrayList<>();
            lineMappings.add(currentLineEntries);

            if (jsLineStr.isEmpty()) continue;

            int jsColumn = 0;
            String[] segments = jsLineStr.split(",");
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                List<Integer> vals = decodeVlq(segment);

                jsColumn += vals.get(0);

                if (vals.size() >= 4) {
                    sourceLine += vals.get(2);
                    sourceColumn += vals.get(3);

                    String symbolName = null;
                    if (vals.size() >= 5) {
                        nameIndex += vals.get(4);
                        if (nameIndex >= 0 && nameIndex < names.length) {
                            symbolName = names[nameIndex];
                        }
                    }
                    currentLineEntries.add(new MappingEntry(jsColumn, sourceLine, sourceColumn, symbolName));
                }
            }
        }
        return new ScriptMapping(lineMappings, prependedLineCount);
    }

    /**
     * 获取映射信息（需要传入 JS 报错的行号和列号）
     *
     * @param jsLine   1-based
     * @param jsColumn 1-based
     * @return 返回原始位置信息，如果没找到则返回带原始请求的兜底对象
     */
    public static OriginalPosition getMappedPosition(String scriptPath, int jsLine, int jsColumn) {
        OriginalPosition fallback = new OriginalPosition(jsLine, jsColumn, null);
        if (scriptPath == null || MAPPINGS_MAP.isEmpty()) return fallback;

        String query = scriptPath.replace('\\', '/');
        ScriptMapping mapping = MAPPINGS_MAP.get(query);

        if (mapping == null) {
            for (Map.Entry<String, ScriptMapping> entry : MAPPINGS_MAP.entrySet()) {
                if (entry.getKey().endsWith(query) || query.endsWith(entry.getKey())) {
                    mapping = entry.getValue();
                    break;
                }
            }
        }

        if (mapping != null) {
            int adjustedLine = jsLine - mapping.prependedLineCount;
            OriginalPosition adjustedFallback = new OriginalPosition(adjustedLine > 0 ? adjustedLine : jsLine, jsColumn, null);
            int lineIndex = adjustedLine - 1;
            if (lineIndex >= 0 && lineIndex < mapping.lineMappings.size()) {
                List<MappingEntry> entries = mapping.lineMappings.get(lineIndex);
                if (entries == null || entries.isEmpty()) return adjustedFallback;

                int targetCol = jsColumn - 1;
                MappingEntry bestMatch = entries.get(0);

                for (MappingEntry entry : entries) {
                    if (entry.jsColumn <= targetCol) {
                        bestMatch = entry;
                    } else {
                        break;
                    }
                }
                return new OriginalPosition(bestMatch.tsLine + 1, bestMatch.tsColumn + 1, bestMatch.name);
            }
            return adjustedFallback;
        }
        return fallback;
    }

    /**
     * 根据路径前缀清除映射（用于按脚本类型批量清除）。
     * 平台层通过 {@code SourceMapRegistryPlatformBridge.clearByType} 桥接调用。
     */
    public static void clearByPathPrefix(String pathPrefix) {
        if (pathPrefix == null) return;
        String lower = pathPrefix.toLowerCase();
        MAPPINGS_MAP.keySet().removeIf(path -> path.toLowerCase().contains(lower));
    }

    // VLQ Base64 解码器
    private static List<Integer> decodeVlq(String str) {
        List<Integer> result = new ArrayList<>();
        int value = 0, shift = 0;
        for (int i = 0; i < str.length(); i++) {
            int c = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".indexOf(str.charAt(i));
            if (c < 0) continue;
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

    private static class ScriptMapping {
        final List<List<MappingEntry>> lineMappings;
        final int prependedLineCount;

        ScriptMapping(List<List<MappingEntry>> lineMappings, int prependedLineCount) {
            this.lineMappings = lineMappings;
            this.prependedLineCount = Math.max(0, prependedLineCount);
        }
    }

    private static class MappingEntry {
        final int jsColumn;
        final int tsLine;
        final int tsColumn;
        final String name;

        MappingEntry(int jsColumn, int tsLine, int tsColumn, String name) {
            this.jsColumn = jsColumn;
            this.tsLine = tsLine;
            this.tsColumn = tsColumn;
            this.name = name;
        }
    }

    public static class OriginalPosition {
        public final int line;
        public final int column;
        public final String name;

        public OriginalPosition(int line, int column, String name) {
            this.line = line;
            this.column = column;
            this.name = name;
        }

        @Override
        public String toString() {
            if (name != null) return name + " (" + line + ":" + column + ")";
            return line + ":" + column;
        }
    }
}