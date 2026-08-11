package com.tkisor.nekojs.core.compiler.python;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a v3 source-map JSON mapping generated JS lines back to the original Python source at
 * statement granularity, for {@link PythonToJsCompiler}. Each generated line that begins a
 * statement carries one 4-field segment (genCol, sourceIdx, origLine, origCol); all other lines
 * are unmapped. VLQ-encoded per the source-map v3 spec.
 */
final class PythonSourceMap {

    private static final String BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private PythonSourceMap() {}

    /**
     * @param file        the source file name (also used as the lone {@code sources} entry)
     * @param source      the original Python source (included verbatim as {@code sourcesContent})
     * @param mappings    {@code {generatedJsLine0Based, originalPythonLine0Based}} pairs
     * @param totalLines  number of generated (JS) lines
     */
    static String build(String file, String source, List<int[]> mappings, int totalLines) {
        List<int[]> sorted = new ArrayList<>(mappings);
        sorted.sort((a, b) -> Integer.compare(a[0], b[0]));

        StringBuilder msb = new StringBuilder();
        int prevOrigLine = 0;   // 0-based, running delta baseline
        int mi = 0;
        for (int g = 0; g < totalLines; g++) {
            if (g > 0) msb.append(';');
            if (mi < sorted.size() && sorted.get(mi)[0] == g) {
                int origLine0 = sorted.get(mi)[1];
                msb.append(vlq(0));                              // generated column (always 0)
                msb.append(vlq(0));                              // source index delta (single source)
                msb.append(vlq(origLine0 - prevOrigLine));       // original line delta
                msb.append(vlq(0));                              // original column (always 0)
                prevOrigLine = origLine0;
                mi++;
            }
            // else: empty segment (line carries no mapping)
        }

        return "{\"version\":3"
                + ",\"file\":" + jsStr(file)
                + ",\"sourceRoot\":\"\""
                + ",\"sources\":[" + jsStr(file) + "]"
                + ",\"sourcesContent\":[" + jsStr(source) + "]"
                + ",\"names\":[]"
                + ",\"mappings\":\"" + msb + "\"}";
    }

    /** Base64 VLQ encode of a signed integer. */
    private static String vlq(int val) {
        int v = val < 0 ? ((-val << 1) | 1) : (val << 1);
        StringBuilder sb = new StringBuilder();
        do {
            int digit = v & 0x1f;
            v >>>= 5;
            if (v != 0) digit |= 0x20;   // continuation bit
            sb.append(BASE64.charAt(digit));
        } while (v != 0);
        return sb.toString();
    }

    private static String jsStr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
