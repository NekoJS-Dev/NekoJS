package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.probe.backend.python.PythonProbeBackend;

/**
 * Self-registering built-in plugin providing NekoJS's own probe backends
 * (TypeScript {@code .d.ts} + Python {@code .pyi}) through the same
 * {@link NekoJSPlugin#registerProbeBackends} hook third-party plugins use.
 *
 * <p>Registered names are a cross-layer contract: all platforms'
 * {@code NekoJSCommands} look up the default backend as
 * {@code ("typescript", "builtin")} by string, so the names below must not
 * change.
 *
 * <p>Lives under {@code com.tkisor.nekojs.*} so it is auto-discovered on every
 * platform (NeoForge ASM scan from the embedded {@code common} classes and the
 * Cleanroom package scanner alike). Uses the default priority (1000): backend
 * registration order is irrelevant because {@link ProbeBackendRegistry#lock()}
 * fails fast on {@code (language, name)} conflicts.
 */
@RegisterNekoJSPlugin
public final class NekoProbeBuiltinPlugin implements NekoJSPlugin {

    @Override
    public void registerProbeBackends(ProbeBackendRegistry registry) {
        registry.register(new TypeScriptProbeBackend(), "NekoJS (built-in)");
        registry.register(new PythonProbeBackend(), "NekoJS (built-in)");
    }
}
