package com.tkisor.nekojs.core.module.esm;

import java.nio.file.Path;

public record NekoEsmDiagnostic(
        Path file,
        NekoEsmSpan span,
        int line,
        int column,
        String message
) {
    public static NekoEsmDiagnostic fromSource(Path file, String source, NekoEsmSpan span, String message) {
        int line = 1;
        int column = 1;
        int end = Math.min(span == null ? 0 : span.start(), source == null ? 0 : source.length());
        for (int i = 0; i < end; i++) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new NekoEsmDiagnostic(file, span, line, column, message);
    }

    @Override
    public String toString() {
        return message + location();
    }

    private String location() {
        if (file == null || span == null) {
            return "";
        }
        return " at " + file + ":" + line + ":" + column;
    }
}
