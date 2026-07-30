package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.facade.ModInfoValue;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.PlatformCapability;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultPlatformFacadeTest {
    private final DefaultPlatformFacade facade = new DefaultPlatformFacade(new FixturePlatform());

    @Test
    void exposesPortableScalarFields() {
        assertFalse(facade.isClient());
        assertEquals("1.21.1", facade.getMcVersion());
        assertEquals("neoforge", facade.getLoaderId());
        assertEquals("21.1.0", facade.getLoaderVersion());
    }

    @Test
    void returnsImmutableModSnapshotOrNull() {
        assertEquals(new ModInfoValue("nekojs", "NekoJS", "1.1.0"), facade.getInfo("nekojs"));
        assertNull(facade.getInfo("missing"));
    }

    @Test
    void sortsModAndCapabilityIds() {
        assertEquals(java.util.List.of("alpha", "nekojs"), facade.getList());
        assertEquals(java.util.List.of("network-custom-channel", "recipe-hot-reload"), facade.capabilities());
    }

    private static final class FixturePlatform implements IPlatform {
        private final Map<String, IModInfo> mods = new LinkedHashMap<>();

        private FixturePlatform() {
            mods.put("nekojs", new FixtureModInfo("nekojs", "NekoJS", "1.1.0"));
            mods.put("alpha", new FixtureModInfo("alpha", "Alpha", "1.0.0"));
        }

        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return Path.of("."); }
        @Override public Map<String, IModInfo> getMods() { return mods; }
        @Override public IModInfo getInfo(String modID) { return mods.get(modID); }
        @Override public Set<PlatformCapability> capabilities() {
            return Set.of(PlatformCapability.RECIPE_HOT_RELOAD, PlatformCapability.NETWORK_CUSTOM_CHANNEL);
        }
        @Override public String getLoaderId() { return "neoforge"; }
        @Override public String getLoaderVersion() { return "21.1.0"; }
    }

    private record FixtureModInfo(String id, String name, String version) implements IModInfo {
        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public void setName(String name) { throw new UnsupportedOperationException(); }
        @Override public String getVersion() { return version; }
        @Override public String getCustomName() { return name; }
    }
}
