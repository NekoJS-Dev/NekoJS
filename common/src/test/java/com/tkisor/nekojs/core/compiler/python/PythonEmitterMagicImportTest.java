package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@code from nekojs import ...}（魔法 import）被转译器剥离：无 JS 输出、不报错，
 * 且 source map 不偏移；其他模块的 {@code from X import *} 仍照常拒绝。
 */
class PythonEmitterMagicImportTest {

    private String compile(String src) throws Exception {
        ScriptCompileResult r = new PythonToJsCompiler().compileDetailed(Path.of("t.py"), src);
        return r.code();
    }

    @Test
    void starImportFromNekojs_strippedNoJs() throws Exception {
        String js = compile("from nekojs import *\nx = 1\n");
        assertFalse(js.contains("import"), "magic `from nekojs import *` must not appear in JS: " + js);
        assertTrue(js.contains("x"), "statement after the magic import must remain: " + js);
    }

    @Test
    void namedImportFromNekojs_strippedNoJs() throws Exception {
        String js = compile("from nekojs import Item\ny = 2\n");
        assertFalse(js.contains("import"), "named `from nekojs import Item` must not appear in JS: " + js);
        assertTrue(js.contains("y"), "following statement must remain: " + js);
    }

    @Test
    void magicImport_isLineNeutral() throws Exception {
        // 剥离后，带魔法 import 的脚本的 JS 行数应与不带时一致（import 不产生任何行）
        String withMagic = compile("from nekojs import *\nx = 1\n");
        String noMagic = compile("x = 1\n");
        assertEquals(countLines(noMagic), countLines(withMagic),
                "magic import must be line-neutral (source map stays aligned)");
    }

    @Test
    void starImportFromOtherModule_stillRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> compile("from utils import *\nz = 3\n"),
                "non-nekojs `from X import *` must still be rejected");
    }

    private static int countLines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }
}
