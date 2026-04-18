package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.script.ScriptType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceMapRegistry {

    private static final Pattern MAPPINGS_PATTERN = Pattern.compile("\"mappings\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAMES_PATTERN = Pattern.compile("\"names\"\\s*:\\s*\\[(.*?)\\]");

    private static final Map<String, ScriptMapping> MAPPINGS_MAP = new ConcurrentHashMap<>();

    /**
     * 注册 SourceMap（传入完整的 sourceMapJson）
     */
    public static void register(String scriptPath, String sourceMapJson) {
        if (sourceMapJson == null || sourceMapJson.isEmpty() || scriptPath == null) return;

        try {
            String mappings = "";
            Matcher mapMatcher = MAPPINGS_PATTERN.matcher(sourceMapJson);
            if (mapMatcher.find()) mappings = mapMatcher.group(1);

            String[] names = new String[0];
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

            if (!mappings.isEmpty()) {
                String normalizedPath = scriptPath.replace('\\', '/');
                ScriptMapping mapping = parseMappings(mappings, names);
                MAPPINGS_MAP.put(normalizedPath, mapping);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ScriptMapping parseMappings(String mappings, String[] names) {
        String[] jsLines = mappings.split(";", -1);
        List<List<MappingEntry>> lineMappings = new ArrayList<>(jsLines.length);

        // 注意：除了 jsColumn 每行重置，其他状态都是全局累加的！
        int sourceLine = 0;
        int sourceColumn = 0;
        int nameIndex = 0;
        // int sourceIndex = 0; // 单文件脚本暂不需要多文件源索引

        for (String jsLineStr : jsLines) {
            List<MappingEntry> currentLineEntries = new ArrayList<>();
            lineMappings.add(currentLineEntries);

            if (jsLineStr.isEmpty()) continue;

            int jsColumn = 0;
            String[] segments = jsLineStr.split(",");
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                List<Integer> vals = decodeVlq(segment);

                // 第1位：JS 列号偏移
                jsColumn += vals.get(0);

                if (vals.size() >= 4) {
                    // 第2位：源文件索引
                    // 第3位：TS 行号偏移
                    sourceLine += vals.get(2);
                    // 第4位：TS 列号偏移
                    sourceColumn += vals.get(3);

                    String symbolName = null;
                    if (vals.size() >= 5) {
                        // 第5位：符号名偏移
                        nameIndex += vals.get(4);
                        if (nameIndex >= 0 && nameIndex < names.length) {
                            symbolName = names[nameIndex];
                        }
                    }
                    currentLineEntries.add(new MappingEntry(jsColumn, sourceLine, sourceColumn, symbolName));
                }
            }
        }
        return new ScriptMapping(lineMappings);
    }

    /**
     * 获取映射信息（需要传入 JS 报错的行号和列号）
     *
     * @param jsLine   1-based (通常堆栈里是1开始的)
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
            // JS行号转为0基索引
            int lineIndex = jsLine - 1;
            if (lineIndex >= 0 && lineIndex < mapping.lineMappings.size()) {
                List<MappingEntry> entries = mapping.lineMappings.get(lineIndex);
                if (entries == null || entries.isEmpty()) return fallback;

                // JS列号转为0基索引
                int targetCol = jsColumn - 1;
                MappingEntry bestMatch = entries.get(0);

                // 寻找最接近的列（不超过报错列的最后一个片段）
                for (MappingEntry entry : entries) {
                    if (entry.jsColumn <= targetCol) {
                        bestMatch = entry;
                    } else {
                        break;
                    }
                }
                // 返回1基的TS行号和列号
                return new OriginalPosition(bestMatch.tsLine + 1, bestMatch.tsColumn + 1, bestMatch.name);
            }
        }
        return fallback;
    }

    public static void clearByType(ScriptType type) {
        if (type == null) return;
        String typeFolder = type.name.toLowerCase() + "_scripts";
        MAPPINGS_MAP.keySet().removeIf(path -> path.toLowerCase().contains(typeFolder));
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

        ScriptMapping(List<List<MappingEntry>> lineMappings) {
            this.lineMappings = lineMappings;
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