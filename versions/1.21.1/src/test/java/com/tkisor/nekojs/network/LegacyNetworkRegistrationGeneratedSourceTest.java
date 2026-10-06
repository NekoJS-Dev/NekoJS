//? if neoforge && <26 {
package com.tkisor.nekojs.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the active Stonecutter output, not only guarded shared source comments. */
class LegacyNetworkRegistrationGeneratedSourceTest {
    @Test
    void evaluatedLegacyNodeUsesTheModBusForPayloadRegistration() {
        Path source = Path.of("build", "generated", "stonecutter", "main", "java",
                "com", "tkisor", "nekojs", "network", "NekoJSNetwork.java");
        assertTrue(Files.isRegularFile(source), "Stonecutter must generate the legacy node source before tests");
        String code;
        try {
            code = Files.readString(source);
        } catch (java.io.IOException failure) {
            throw new AssertionError("Cannot read generated legacy network source", failure);
        }
        assertTrue(code.contains("@EventBusSubscriber(modid = NekoJS.MODID")
                        && code.contains(", bus = EventBusSubscriber.Bus.MOD"),
                "1.21.1 generated network subscriber must use the MOD bus");
    }
}
//?}
