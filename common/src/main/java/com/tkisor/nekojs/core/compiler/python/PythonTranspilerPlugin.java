package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.compiler.FrontendSemantics;
import com.tkisor.nekojs.core.compiler.NekoScriptLanguage;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;

import java.util.Set;

/**
 * Self-registering built-in plugin exposing Python (a {@code .py} subset → JS) as a NekoJS
 * script language via {@link PythonToJsCompiler}.
 *
 * <p>Lives under {@code com.tkisor.nekojs.*} so it is auto-discovered on every platform:
 * NeoForge's ASM annotation scan picks it up from the embedded {@code common} classes, and
 * package-scanning loaders rooted at {@code com.tkisor.nekojs} find it too. No
 * per-platform wiring is required. Uses the default priority (1000), so it registers after
 * {@code NekoJSCorePlugin} ({@link NekoJSPlugin#CORE_PRIORITY}).
 */
@RegisterNekoJSPlugin
public final class PythonTranspilerPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.ScriptCompilersPoint.Contributor {

    @Override
    public void registerScriptCompilers(ScriptCompilerRegistry registry) {
        registry.registerFrontend("nekojs:python", "1",
                new NekoScriptLanguage("python", Set.of(".py"), new PythonToJsCompiler()),
                new FrontendSemantics(new FrontendSemantics.Input(FrontendSemantics.SOURCE_TEXT, "nekojs:python"),
                        FrontendSemantics.JAVASCRIPT_OUTPUT, Set.of(FrontendSemantics.PYTHON_AUTHORING)));
    }
}
