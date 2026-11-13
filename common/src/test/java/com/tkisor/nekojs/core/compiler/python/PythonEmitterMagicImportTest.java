package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.ScriptCompileResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the transpiler strips {@code from nekojs import ...} (the magic import): it
 * produces no JS output, raises no error, and does not shift the source map.
 * A {@code from X import *} from any other module is still rejected as usual.
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
        // After stripping, a script with the magic import has the same JS line count as one without it, because the
        // import produces no lines.
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
