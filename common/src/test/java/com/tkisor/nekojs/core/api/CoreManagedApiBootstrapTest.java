package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreManagedApiBootstrapTest {
    @Test
    void loadsCompleteCoreContractContributionsAndImplementations() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), URI.create("test:///core.jar"));

        assertEquals(ApiVersion.parse("0.7.0"),
                core.contracts().requirePortable("nekojs-core").identity().version());
        assertEquals("nekojs-core", core.contributions().owner().ownerId());
        assertEquals(65, core.contributions().symbolContributions().size());
        assertEquals(6, core.globalImplementations().size());
        assertNotNull(core.globalImplementations().get(CoreManagedApiBootstrap.ID_GLOBAL));
        assertNotNull(core.globalImplementations().get(CoreManagedApiBootstrap.PLATFORM_GLOBAL));
        assertNotNull(core.globalImplementations().get(CoreManagedApiBootstrap.TEXT_GLOBAL));
        assertNotNull(core.globalImplementations().get(CoreManagedApiBootstrap.JSON_IO_GLOBAL));
        assertNotNull(core.globalImplementations().get(CoreManagedApiBootstrap.NBT_GLOBAL));
        assertNotNull(core.globalImplementations().get(CoreManagedApiBootstrap.REGISTRY_GLOBAL));
    }

    @Test
    void rejectsLegacyBindingThatShadowsManagedGlobal() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new EmptyPlatform(), URI.create("test:///core.jar"));
        LegacyGlobalReservation legacyId = new LegacyGlobalReservation(
                "ID", ApiSymbolId.parse("global:legacy-ID"));

        assertThrows(ApiResolutionException.class, () -> JsApiSurfaceResolver.resolve(
                environment(), core.contracts(), List.of(core.contributions()), List.of(legacyId)));

        LegacyGlobalReservation legacyText = new LegacyGlobalReservation(
                "Text", ApiSymbolId.parse("global:legacy-Text"));
        assertThrows(ApiResolutionException.class, () -> JsApiSurfaceResolver.resolve(
                environment(), core.contracts(), List.of(core.contributions()), List.of(legacyText)));

        LegacyGlobalReservation legacyJsonIo = new LegacyGlobalReservation(
                "JsonIO", ApiSymbolId.parse("global:legacy-JsonIO"));
        assertThrows(ApiResolutionException.class, () -> JsApiSurfaceResolver.resolve(
                environment(), core.contracts(), List.of(core.contributions()), List.of(legacyJsonIo)));

        LegacyGlobalReservation legacyNbt = new LegacyGlobalReservation(
                "NBT", ApiSymbolId.parse("global:legacy-NBT"));
        assertThrows(ApiResolutionException.class, () -> JsApiSurfaceResolver.resolve(
                environment(), core.contracts(), List.of(core.contributions()), List.of(legacyNbt)));
    }

    private static EnvironmentKey environment() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER,
                RuntimeDist.DEDICATED_SERVER,
                "test",
                "0.0.0",
                LoaderVersion.parse("0.0.0"),
                "1.21.1",
                Map.of());
    }

    private static final class EmptyPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return Path.of("."); }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modID) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0.0.0"; }
    }
}
