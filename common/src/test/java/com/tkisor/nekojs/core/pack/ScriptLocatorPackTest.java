package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.script.ScriptContainer;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link ScriptLocator} script pack discovery: a script inside a pack gets a ScriptId with a
 * {@code packs/<id>/} (GLOBAL) or {@code worldpacks/<id>/} (WORLD) prefix; load order is GLOBAL, then
 * WORLD, then flat directories; disabled packs and non-script files inside a pack are not discovered.
 * Flat directories come from the static {@code ScriptType.path} and hold no scripts in the test
 * environment, so only the containers from packs are asserted.
 */
class ScriptLocatorPackTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @TempDir
    Path packsRoot;

    @TempDir
    Path worldRoot;

    private final ScriptPropertyRegistry properties = new ScriptPropertyRegistry.Impl();

    @Test
    void packScriptsAreDiscoveredWithPrefixedIdAndPackAttribution() throws Exception {
        Path scripts = packScripts(packsRoot, "demo");
        Files.writeString(scripts.resolve("main.js"), "console.info('hi')");
        Files.writeString(scripts.resolve("nested"), "ignored"); // No extension: not a script file.

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);

        List<ScriptContainer> discovered = ScriptLocator.discover(
            ScriptType.SERVER, properties, ScriptFilePolicy.legacyRuntime(), registry);

        List<ScriptContainer> fromPack = discovered.stream().filter(c -> c.packId != null).toList();
        assertEquals(1, fromPack.size());
        ScriptContainer container = fromPack.get(0);
        assertEquals("demo", container.packId);
        assertEquals(ScriptPackScope.GLOBAL, container.packScope);
        assertEquals("nekojs:server/packs/demo/main.js", container.id.toString());
        assertEquals(scripts.resolve("main.js"), container.path);
    }

    @Test
    void discoveryOrderIsGlobalThenWorldThenWorkspace() throws Exception {
        Files.writeString(packScripts(packsRoot, "zeta").resolve("z.js"), "// z");
        Files.writeString(packScripts(packsRoot, "alpha").resolve("a.js"), "// a");
        Path worldPacks = worldRoot.resolve(ScriptPackRegistry.WORLD_PACKS_DIR);
        Files.writeString(packScripts(worldPacks, "wp").resolve("w.js"), "// w");

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);
        registry.activateWorldPacks(worldRoot);

        List<String> packIds = ScriptLocator.discover(
            ScriptType.SERVER, properties, ScriptFilePolicy.legacyRuntime(), registry)
            .stream().filter(c -> c.packId != null)
            .map(c -> c.id.toString())
            .toList();

        assertEquals(List.of(
            "nekojs:server/packs/alpha/a.js",
            "nekojs:server/packs/zeta/z.js",
            "nekojs:server/worldpacks/wp/w.js"), packIds);
    }

    @Test
    void disabledPacksAreSkipped() throws Exception {
        Files.writeString(packScripts(packsRoot, "off").resolve("x.js"), "// x");
        Files.writeString(packsRoot.resolve("off").resolve(ScriptPackManifest.FILE_NAME),
            "{\"id\": \"off\", \"enabled\": false}");

        ScriptPackRegistry registry = new ScriptPackRegistry();
        registry.refreshGlobalPacks(packsRoot);

        List<ScriptContainer> discovered = ScriptLocator.discover(
            ScriptType.SERVER, properties, ScriptFilePolicy.legacyRuntime(), registry);
        assertEquals(0, discovered.stream().filter(c -> "off".equals(c.packId)).count());
    }

    private static Path packScripts(Path root, String packId) throws Exception {
        Path scripts = root.resolve(packId).resolve("server_scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.getParent().resolve(ScriptPackManifest.FILE_NAME), "{\"id\": \"" + packId + "\"}");
        return scripts;
    }
}
