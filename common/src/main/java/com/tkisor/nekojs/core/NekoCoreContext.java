package com.tkisor.nekojs.core;

import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.ErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Engine;

/**
 * Immutable holder for the core-wide cross-cutting dependencies.
 *
 * <p>It carries only the 4 fields that are core-wide and live as long as config, engine and
 * error handling: {@code engine}, {@code sandboxConfig}, {@code classFilter} and
 * {@code errorTracker}. Paths are reached through the
 * {@link com.tkisor.nekojs.core.fs.NekoJSPaths#get()} singleton.
 *
 * <ul>
 *   <li>It offers no service lookup such as {@code get()} / {@code current()}.</li>
 *   <li>Ordinary business classes do not take {@code NekoCoreContext} in their constructors.</li>
 *   <li>A class that does take {@code NekoCoreContext} may only be a terminal consumer or a
 *       factory input.</li>
 * </ul>
 */
public record NekoCoreContext(
        Engine engine,
        SandboxConfig sandboxConfig,
        ClassFilter classFilter,
        ErrorTracker errorTracker
) {
}
