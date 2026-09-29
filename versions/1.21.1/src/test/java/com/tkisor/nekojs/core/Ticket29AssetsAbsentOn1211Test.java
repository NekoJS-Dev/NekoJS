package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Ticket 29 AC8 capability leg for the 1.21.1 node (node-local test tree, so it runs only here):
 * both assets/lang events stay available, but the typed {@code Assets} binding is absent — the
 * only production registration line sits inside a {@code >=26} stonecutter guard in
 * {@code NekoJSCorePlugin} (pinned by the shared-tree
 * {@code Ticket29AssetsLangCapabilityTraceTest} guard trace), so this node evaluates it away.
 *
 * <p>This records an existing platform difference as an explicit capability row, not a silent
 * gap; widening the binding to 1.21.1 is a deliberate surface change, not a test fix.
 */
class Ticket29AssetsAbsentOn1211Test {

    @BeforeAll
    static void initPlatform() {
        // Node-local Platform stub (Platform.init is defensive: already initialized stays put);
        // ScriptType's static init needs NekoJSPaths, so the stub must come first.
        try {
            Platform.init(new StubPlatform());
        } catch (RuntimeException ignored) {
            // another test already initialized Platform
        }
    }

    /** Production registration path (same entry the bootstrap BindingsPoint collection uses). */
    private static BindingRegistry.BindingRegistryImpl productionBindings(ScriptType scriptType) {
        BindingRegistry.BindingRegistryImpl registry = new BindingRegistry.BindingRegistryImpl(scriptType);
        new NekoJSCorePlugin().registerBinding(registry);
        return registry;
    }

    @Test
    void bothEventsExistButTheAssetsBindingIsAbsent() {
        assertNull(productionBindings(ScriptType.CLIENT).viewRegistered().get("Assets"),
                "1.21.1 must NOT expose the Assets typed binding (existing >=26-only difference,"
                        + " recorded explicitly by the ticket 29 capability table)");

        assertEquals("generateAssets", ClientEvents.GENERATE_ASSETS.eventName());
        assertEquals("ClientEvents", ClientEvents.GENERATE_ASSETS.groupName());
        assertEquals(ScriptType.CLIENT, ClientEvents.GENERATE_ASSETS.scriptType());
        assertEquals("lang", ClientEvents.LANG.eventName());
        assertEquals("ClientEvents", ClientEvents.LANG.groupName());
        assertEquals(ScriptType.CLIENT, ClientEvents.LANG.scriptType());
    }

    /** Node-test-tree minimal Platform stub (shape follows the shared-tree Ticket29AssetBindingTest). */
    private static final class StubPlatform implements IPlatform {
        @Override
        public boolean isClient() {
            return true;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "1.21.1";
        }

        @Override
        public Path getGameDir() {
            return com.tkisor.nekojs.TestGameDirs.unique("nekojs-ticket29-1211-assets");
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0";
        }

        @Override
        public java.util.Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() {
            return java.util.Map.of();
        }

        @Override
        public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public java.util.Set<com.tkisor.nekojs.platform.PlatformCapability> capabilities() {
            return java.util.Set.of();
        }
    }
}
