// Fabric node-local wiring test: mirrors the purpose of McVersionCompatWiringTest on the NeoForge
// side. When ServiceLoader has no provider, the first call throws only IllegalStateException, which
// the compiler cannot catch. It is not in the shared test tree because the `//? if fabric` guard is
// a lazy comment for the active node (26.1.2 NeoForge, which compiles the shared tree directly from
// disk) and would compile and run on the wrong platform. A node-local test tree is mounted by the
// standard source set layout, so it belongs to this node only. Both fabric nodes carry one copy of
// the same test, following the same-body node distribution used for golden fixtures.
package com.tkisor.nekojs.platform.compat;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that {@link McVersionCompat} is wired on 26.1.2-fabric: this node's jar and test classpath
 * must carry the {@code Fabric261VersionCompat} services entry.
 * {@code LevelExtension.neko$spawnLightning}, injected by {@code inject.MixinLevel} in the shared
 * tree, gets the lightning entity type through this facade; without a provider that call crashes
 * the ServiceLoader step of McVersionCompat static initialization, and nothing catches it at that
 * point. The d.ts already advertises spawnLightning. The test does not trigger MC class
 * initialization, because instantiating the provider only resolves the method body lazily.
 */
class FabricVersionCompatWiringTest {

    @Test
    void fabricNodeProvidesVersionCompatImpl() {
        assertTrue(ServiceLoader.load(McVersionCompat.Impl.class, McVersionCompat.class.getClassLoader())
                        .findFirst().isPresent(),
                "no McVersionCompat.Impl provider on 26.1.2-fabric test classpath: "
                        + "expected META-INF/services entry for Fabric261VersionCompat");
    }
}
