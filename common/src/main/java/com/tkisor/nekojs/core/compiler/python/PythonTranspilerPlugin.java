package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;

import java.util.Set;

/**
 * Self-registering built-in plugin exposing Python (a {@code .py} subset → JS) as a NekoJS
 * script language via {@link PythonToJsCompiler}.
 *
 * <p>Lives under {@code com.tkisor.nekojs.*} so it is auto-discovered on every platform:
 * NeoForge's ASM annotation scan picks it up from the embedded {@code common} classes, and the
 * Cleanroom 1.12.2 package scanner (rooted at {@code com.tkisor.nekojs}) finds it too. No
 * per-platform wiring is required. Uses the default priority (1000), so it registers after
 * {@code NekoJSCorePlugin} ({@link NekoJSPlugin#CORE_PRIORITY}).
 */
@RegisterNekoJSPlugin
public final class PythonTranspilerPlugin implements NekoJSPlugin {

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.registerLanguage("python", Set.of(".py"), new PythonToJsCompiler());
    }
}
