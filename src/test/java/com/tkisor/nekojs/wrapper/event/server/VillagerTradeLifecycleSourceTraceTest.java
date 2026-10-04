package com.tkisor.nekojs.wrapper.event.server;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerTradeLifecycleSourceTraceTest {

    @Test
    void supportedListenersWireInitialCollectionAndServerStopCleanup() throws Exception {
        assertLifecycleWiring(read("src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java"));
        assertLifecycleWiring(read("versions/1.21.1/src/main/java/com/tkisor/nekojs/listener/ServerEventListener.java"));
    }

    private static String read(String relativePath) throws Exception {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(directory.resolve("src"))) {
                return Files.readString(directory.resolve(relativePath), StandardCharsets.UTF_8);
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("cannot locate repository root from " + Path.of("").toAbsolutePath());
    }

    private static void assertLifecycleWiring(String source) {
        int startup = source.indexOf("villagerTradeDomain.applyInitialPlan(server)");
        int stop = source.indexOf("villagerTradeDomain.clearServer()");
        assertTrue(startup >= 0, "server startup must apply the initial Villager Trades plan");
        assertTrue(stop >= 0, "server stop must clear the Villager Trades binding");
        assertTrue(source.indexOf("villagerTradeDomain()") >= 0,
                "the listener must resolve the root-owned Villager Trades domain");
    }
}
