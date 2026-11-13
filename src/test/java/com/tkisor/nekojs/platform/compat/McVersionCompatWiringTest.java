// TODO(fabric): the fabric side has no matching implementation yet; remove this whole-file guard
// once the port is done
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.platform.compat;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring regression for the version compat layer (26.x only): the 26-shared LevelExtension gets the
 * {@code LIGHTNING_BOLT} type through {@link McVersionCompat}, and 26.1 ({@code EntityType}) and
 * 26.2 ({@code EntityTypes}) each provide a provider. The 1.21.1 LevelExtension is a separate
 * implementation; it does not go through that facade and provides no services entry, so this test
 * lives only in the 26-shared test tree and compiles and runs on 26.1/26.2 only.
 */
class McVersionCompatWiringTest {

    @Test
    void versionModuleProvidesVersionCompatImpl() {
        assertTrue(ServiceLoader.load(McVersionCompat.Impl.class, McVersionCompat.class.getClassLoader())
                        .findFirst().isPresent(),
                "no McVersionCompat.Impl provider on this 26.x platform's test classpath: "
                        + "expected META-INF/services entry from neoforge-26.1/26.2");
    }
}
//?}
//?}
