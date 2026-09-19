package com.tkisor.nekojs.core.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票据 12 review-round-1 F3：enum 数字字面量诊断必须指向**该成员**的 authored 位置，
 * 不得因文件里更早出现同名 token/注释而指向别处，也不得在位置未知时伪造 1:1。
 */
class NekoTypeScriptEnumDiagnosticLocationTest {

    @Test
    void earlierCommentOccurrenceDoesNotStealThePosition() {
        // The literal "1e" appears in a comment on line 1 and a string on line 2, long before the
        // offending enum member on line 4. Searching the file for the text would report 1:1 / 2:x.
        String src = "// note: 1e is not valid here\n"
                + "const note = '1e';\n"
                + "enum Mode {\n"
                + "  Broken = 1e\n"
                + "}\n";

        NekoCompileException failure = assertThrows(NekoCompileException.class,
                () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("enum-location.ts"), src));

        assertTrue(failure.getMessage().contains("1e"), failure.getMessage());
        assertEquals(4, failure.line(), "must point at the offending member, not the earlier comment");
        assertTrue(failure.column() > 1, "must be the value column, not line start: " + failure.column());
        assertEquals(src.indexOf("= 1e") + 2, offsetOf(src, failure.line(), failure.column()),
                "the reported offset must be the offending literal itself");
    }

    @Test
    void earlierSameLiteralInAnotherMemberDoesNotStealThePosition() {
        // Line 3 already contains the literal; the invalid one is on line 4. A file-wide search
        // always wins the earlier occurrence.
        String src = "enum Mode {\n"
                + "  One = 1e0,\n"
                + "  Broken = 1e+\n"
                + "}\n";

        NekoCompileException failure = assertThrows(NekoCompileException.class,
                () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("enum-repeat.ts"), src));

        assertTrue(failure.getMessage().contains("1e+"), failure.getMessage());
        assertEquals(3, failure.line(), "must point at the invalid member, not the valid one above it");
        assertEquals(src.indexOf("= 1e+") + 2, offsetOf(src, failure.line(), failure.column()));
    }

    @Test
    void multiLineEnumReportsTheMemberLine() {
        String src = "const prefix = '0xG';\n"
                + "enum Mode {\n"
                + "  First = 0,\n"
                + "  Second = 1,\n"
                + "  Broken = 0xG\n"
                + "}\n";

        NekoCompileException failure = assertThrows(NekoCompileException.class,
                () -> NekoTypeScriptCompiler.eraseTypescript(Path.of("enum-multiline.ts"), src));

        assertEquals(5, failure.line(), failure.getMessage());
        assertEquals(src.indexOf("= 0xG") + 2, offsetOf(src, failure.line(), failure.column()));
    }

    /** Authored offset of a 1-based line/column pair. */
    private static int offsetOf(String source, int line, int column) {
        int offset = 0;
        for (int current = 1; current < line; current++) {
            int newline = source.indexOf('\n', offset);
            offset = newline + 1;
        }
        return offset + column - 1;
    }
}
