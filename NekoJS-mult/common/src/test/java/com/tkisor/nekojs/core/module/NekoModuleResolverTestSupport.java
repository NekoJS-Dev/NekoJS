package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

/**
 * Shared construction for NekoModuleResolver tests: NekoJSPaths is built reflectively from a game
 * dir, and every resolver test must observe the same wiring. Extracted so the two resolver test
 * classes stop duplicating it (ticket 08 code review, Standards #3).
 */
final class NekoModuleResolverTestSupport {

    private NekoModuleResolverTestSupport() {}

    static NekoModuleResolver resolverFor(Path gameDir) throws Exception {
        Constructor<NekoJSPaths> constructor = NekoJSPaths.class.getDeclaredConstructor(Path.class);
        constructor.setAccessible(true);
        NekoJSPaths paths = constructor.newInstance(gameDir);
        ScriptCompilerRegistry compilers = ScriptCompilerRegistry.createRuntimeRegistry();
        return new NekoModuleResolver(paths, new ScriptFilePolicy(compilers));
    }
}
