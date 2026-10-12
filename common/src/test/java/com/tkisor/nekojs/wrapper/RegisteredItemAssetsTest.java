package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.core.registry.ItemAssetDefinition;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RegisteredItemAssetsTest {
    @BeforeAll static void platform() { TestPlatformInit.ensureInitialized(); }
    private static ItemAssetDefinition definition(String texture) {
        return new ItemAssetDefinition("test:gem", "minecraft:item/generated", Map.of("layer0", texture));
    }
    private static Path root() throws IOException {
        return Files.createDirectories(Files.createTempDirectory(Platform.getGameDir(), "item-assets-").resolve("assets"));
    }
    private static void delete(Path root) throws IOException {
        try (var files = Files.walk(root.getParent())) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test void writesBothFilesUpdatesOwnedOutputsAndRemovesOnlyOwnedFiles() throws Exception {
        Path root = root();
        try {
            RegisteredItemAssets writer = new RegisteredItemAssets();
            writer.generate(root, List.of(definition("test:item/first")), true, ignored -> fail(ignored));
            Path model = root.resolve("test/models/item/gem.json");
            Path item = root.resolve("test/items/gem.json");
            assertTrue(Files.readString(model).contains("test:item/first"));
            assertTrue(Files.readString(item).contains("test:item/gem"));
            // A new instance reads the persisted ownership index on the next boot.
            new RegisteredItemAssets().generate(root, List.of(definition("test:item/second")), true, ignored -> fail(ignored));
            assertTrue(Files.readString(model).contains("test:item/second"));
            Path texture = root.resolve("test/textures/item/gem.png");
            Files.createDirectories(texture.getParent()); Files.write(texture, new byte[]{1, 2});
            writer.generate(root, List.of(), true, ignored -> fail(ignored));
            assertFalse(Files.exists(model)); assertFalse(Files.exists(item));
            assertTrue(Files.exists(texture));
        } finally { delete(root); }
    }

    @Test void explicitAndManualFilesWinAndModifiedFilesLoseAutomaticOwnership() throws Exception {
        Path root = root();
        try {
            RegisteredItemAssets writer = new RegisteredItemAssets();
            writer.generate(root, List.of(definition("test:item/first")), true, ignored -> fail(ignored));
            Path model = root.resolve("test/models/item/gem.json");
            String explicit = "{\"parent\":\"test:item/manual\"}";
            new DataGeneratorJS(root).text("test/models/item/gem.json", explicit);
            List<String> skipped = new ArrayList<>();
            writer.generate(root, List.of(definition("test:item/second")), true, skipped::add);
            assertEquals(explicit, Files.readString(model)); assertEquals(1, skipped.size());
            writer.generate(root, List.of(), true, skipped::add);
            assertEquals(explicit, Files.readString(model));
            assertFalse(Files.exists(root.resolve("test/items/gem.json")));
        } finally { delete(root); }
    }

    @Test void failureAfterFirstReplacementRestoresMatchedGroupAndOwnershipIndex() throws Exception {
        Path root = root();
        try {
            new RegisteredItemAssets().generate(root, List.of(definition("test:item/first")), true, ignored -> fail(ignored));
            Map<String, String> before = Map.of(
                    "test/models/item/gem.json", Files.readString(root.resolve("test/models/item/gem.json")),
                    "test/items/gem.json", Files.readString(root.resolve("test/items/gem.json")),
                    "../.nekojs-item-assets/ownership.json", Files.readString(root.resolveSibling(".nekojs-item-assets").resolve("ownership.json")));
            AtomicInteger moves = new AtomicInteger();
            RegisteredItemAssets failing = new RegisteredItemAssets((source, target) -> {
                if (moves.incrementAndGet() == 2) throw new IOException("injected publication failure");
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            });
            assertThrows(IllegalStateException.class, () -> failing.generate(root,
                    List.of(definition("test:item/second")), true, ignored -> fail(ignored)));
            assertEquals(2, moves.get());
            for (var entry : before.entrySet()) assertEquals(entry.getValue(), Files.readString(root.resolve(entry.getKey())));
            try (var entries = Files.list(root)) {
                assertFalse(entries.anyMatch(p -> p.getFileName().toString().startsWith(".nekojs-item-stage-")));
            }
        } finally { delete(root); }
    }

    @Test void invalidIndexRejectsGenerationAndUnownedCompanionPreventsPartialWrite() throws Exception {
        Path root = root();
        try {
            Path item = root.resolve("test/items/gem.json");
            Files.createDirectories(item.getParent()); Files.writeString(item, "{}");
            new RegisteredItemAssets().generate(root, List.of(definition("test:item/first")), true, ignored -> {});
            assertFalse(Files.exists(root.resolve("test/models/item/gem.json")));
            Files.writeString(root.resolveSibling(".nekojs-item-assets").resolve("ownership.json"), "not json");
            assertThrows(IllegalStateException.class, () -> new RegisteredItemAssets().generate(root,
                    List.of(definition("test:item/first")), true, ignored -> {}));
            assertEquals("{}", Files.readString(item));
        } finally { delete(root); }
    }
}
