package com.example.nekojs08;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.module.NodeModuleRegister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test-only external NekoJS addon (ticket 08 fixture).
 *
 * <p>It is built into a standalone jar that is dropped into {@code run/mods} (NeoForge) or
 * {@code run-server/mods} (Fabric). Discovery therefore goes through the real loader paths:
 * FML annotation/metadata scan on NeoForge, and the {@code nekojs} entrypoint in
 * {@code fabric.mod.json} on Fabric.
 *
 * <p>Contributions use the documented base-interface authoring form, so the fixture also pins
 * ADR-0010 equivalence (the hook is a projection of an already existing extension point):
 * <ul>
 *   <li>{@code registerBinding} — projection of {@code nekojs:bindings};</li>
 *   <li>{@code registerNodeModules} — projection of {@code nekojs:node_modules}.</li>
 * </ul>
 * No new extension point is introduced: both channels already exist in the engine.
 */
@RegisterNekoJSPlugin(priority = 777)
public final class AddonPlugin implements NekoJSPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Addon08");

    /** Emitted once when the loader instantiates this plugin — the real-discovery witness. */
    public static final String DISCOVERED_MARKER = "NEKOJS-ADDON08 plugin-instantiated";

    /** Bumped per instance so the smoke can prove discovery runs exactly once per process. */
    private static final java.util.concurrent.atomic.AtomicInteger INSTANCES =
            new java.util.concurrent.atomic.AtomicInteger();

    public AddonPlugin() {
        LOGGER.info("{} instance={}", DISCOVERED_MARKER, INSTANCES.incrementAndGet());
    }

    /** Registered instance count in this process (read by scripts through the binding below). */
    public static int instanceCount() {
        return INSTANCES.get();
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
        boolean accepted = registry.register("AddonProbe", new AddonProbeJS());
        LOGGER.info("NEKOJS-ADDON08 binding-registered name=AddonProbe accepted={}", accepted);
    }

    @Override
    public void registerNodeModules(NodeModuleRegister registry) {
        registry.register("nekojs08:hello", """
                module.exports = {
                    marker: function (tag) { return 'NEKOJS-ADDON08 module-consumed tag=' + tag; }
                };
                """);
    }

    @Override
    public void initStartup() {
        LOGGER.info("NEKOJS-ADDON08 initStartup instances={}", instanceCount());
    }
}
