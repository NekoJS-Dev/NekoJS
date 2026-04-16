package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.script.ScriptType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配合底层 Mixin 的极速 SourceMap 缓存器
 */
public class SourceMapRegistry {
    // 缓存结构：文件路径 -> [JS行号] = TS行号
    private static final Map<String, int[]> MAPPINGS_MAP = new ConcurrentHashMap<>();

    // 在 SWCCompiler.compile 时调用这个方法
    public static void register(String scriptPath, String mappings) {
        if (mappings == null || mappings.isEmpty() || scriptPath == null) return;

        String normalizedPath = scriptPath.replace('\\', '/');

        String[] jsLines = mappings.split(";", -1);
        int[] lineMap = new int[jsLines.length + 1]; // 1-based 数组
        int currentOriginalLine = 0;

        for (int i = 0; i < jsLines.length; i++) {
            String lineStr = jsLines[i];
            if (!lineStr.isEmpty()) {
                String[] segments = lineStr.split(",");
                for (String segment : segments) {
                    if (segment.isEmpty()) continue;
                    List<Integer> vals = decodeVlq(segment);
                    if (vals.size() >= 3) {
                        currentOriginalLine += vals.get(2);
                    }
                }
            }
            lineMap[i + 1] = currentOriginalLine + 1;
        }

        MAPPINGS_MAP.put(normalizedPath, lineMap);
    }

    public static int getMappedLine(String scriptPath, int jsLine) {
        if (scriptPath == null) return jsLine;

        if (MAPPINGS_MAP.isEmpty()) return jsLine;

        String query = scriptPath.replace('\\', '/');

        int[] exactMap = MAPPINGS_MAP.get(query);
        if (exactMap != null) {
            if (jsLine > 0 && jsLine < exactMap.length) {
                return exactMap[jsLine];
            }
            return jsLine;
        }

        for (Map.Entry<String, int[]> entry : MAPPINGS_MAP.entrySet()) {
            String registeredPath = entry.getKey();
            if (registeredPath.endsWith(query) || query.endsWith(registeredPath)) {
                int[] map = entry.getValue();
                if (jsLine > 0 && jsLine < map.length) {
                    return map[jsLine];
                }
            }
        }
        return jsLine;
    }

    public static void clearByType(ScriptType type) {
        if (type == null) return;
        // 构建文件夹特征，例如 "server_scripts"
        String typeFolder = type.name.toLowerCase() + "_scripts";

        MAPPINGS_MAP.keySet().removeIf(path -> path.toLowerCase().contains(typeFolder));
    }

    // VLQ Base64 解码器 (仅在编译脚本时调用一次)
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
                value = 0; shift = 0;
            } else {
                shift += 5;
            }
        }
        return result;
    }
}