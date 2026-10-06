//? if neoforge && <26 {
package com.tkisor.nekojs.wrapper.event.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the evaluated 1.21.1 loot-table compatibility path. */
class LootTableEventJSLegacySourceTest {
    @Test
    void evaluatedLegacyPathDoesNotCallMissingLootEventRegistryMethod() throws Exception {
        Path source = Path.of("build", "generated", "stonecutter", "main", "java",
                "com", "tkisor", "nekojs", "wrapper", "event", "server", "LootTableEventJS.java");
        assertTrue(Files.isRegularFile(source), "Stonecutter must generate the legacy loot source before tests");
        String code = Files.readString(source);
        assertFalse(code.contains("REGISTRIES = event.getRegistries()"),
                "1.21.1 must not call the absent LootTableLoadEvent.getRegistries method");
        assertTrue(code.contains("server.reloadableRegistries().lookup()"),
                "1.21.1 must use the legacy reloadable registry context");
        assertTrue(code.contains("NEKO-2301"),
                "legacy unavailable registry replacement must be explicitly diagnosed");
    }
}
//?}
