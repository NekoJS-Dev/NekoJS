// TODO(fabric): the fabric side has no matching implementation yet; remove this whole-file guard
// once the port is done
//? if neoforge {
package com.tkisor.nekojs.platform.compat;

import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring regression for the client compat layer: shared code gets the per-version compat
 * implementation through ServiceLoader. When the services file is missing, the first call throws
 * only IllegalStateException, which the compiler cannot see at all; it fails at runtime (the error
 * panel toast, host codes and lightning spawning all stop working). This test checks directly on
 * the test classpath that the provider exists. It triggers no MC class initialisation, because
 * instantiating the provider only resolves the method bodies lazily.
 *
 * <p>The test runs once per test task on all three NeoForge platforms (1.21.1/26.1/26.2). Each
 * platform's jar should carry only its own version's services entry.
 */
class McClientCompatWiringTest {

    @Test
    void versionModuleProvidesClientCompatImpl() {
        assertTrue(ServiceLoader.load(McClientCompat.Impl.class, McClientCompat.class.getClassLoader())
                        .findFirst().isPresent(),
                "no McClientCompat.Impl provider on this platform's test classpath: "
                        + "expected META-INF/services entry from the version module "
                        + "(neoforge-1.21.1/26.1/26.2)");
    }
}
//?}
