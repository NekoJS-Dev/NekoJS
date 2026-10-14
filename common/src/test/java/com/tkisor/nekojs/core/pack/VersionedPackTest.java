package com.tkisor.nekojs.core.pack;

import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.ScriptFilePolicy;
import com.tkisor.nekojs.core.ScriptLocator;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VersionedPackTest {
    @TempDir Path root;
    private static final PackDependencyPlan.Environment ENV =
            new PackDependencyPlan.Environment("neoforge", "26.2", Map.of("example", "1.3.0"));

    @BeforeAll static void init() { TestPlatformInit.ensureInitialized(); }

    @Test void legacyRemainsPermissiveButVersionedFieldsAreStrict() {
        assertNull(parse("{\"version\":false,\"future\":42}").descriptor());
        for (String json : List.of(
                "{\"formatVersion\":2}",
                "{\"formatVersion\":null}",
                "{\"formatVersion\":1,\"id\":\"demo\",\"version\":\"1.0.0\",\"entries\":{},\"future\":42}",
                "{\"formatVersion\":1,\"id\":\"Demo!\",\"version\":\"1.0.0\",\"entries\":{}}",
                "{\"formatVersion\":1,\"id\":\"demo\",\"version\":\"1.0\",\"entries\":{}}",
                "{\"formatVersion\":1,\"id\":\"demo\",\"version\":\"1.0.0\",\"enabled\":\"false\",\"entries\":{}}")) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> parse(json)).getMessage().contains("NEKO-1016"));
        }
    }

    @Test void onlyDeclaredEntrypointsAreDiscoveredAndLibrariesAreNotStandaloneEntries() throws Exception {
        Path pack = Files.createDirectories(root.resolve("demo"));
        Files.writeString(pack.resolve("manifest.json"), manifest("demo", "1.0.0", "", "{\"server\":[\"main.js\"]}"));
        Path scripts = Files.createDirectory(pack.resolve("server_scripts"));
        Files.writeString(scripts.resolve("main.js"), "import './library.mjs';");
        Files.writeString(scripts.resolve("library.mjs"), "export const value = 1;");
        ScriptPackRegistry registry = new ScriptPackRegistry(); registry.refreshGlobalPacks(root);
        var entries = ScriptLocator.discover(ScriptType.SERVER, new ScriptPropertyRegistry.Impl(),
                ScriptFilePolicy.legacyRuntime(), registry).stream().filter(script -> script.packId != null).toList();
        assertEquals(List.of("nekojs:server/packs/demo/main.js"), entries.stream().map(script -> script.id.toString()).toList());
    }

    @Test void entryPathsMissingFilesAndAliasesAreRejectedBeforeActivation() throws Exception {
        for (String path : List.of("../outside.js", "/root.js", "C:/file.js", "a\\\\file.js", "./main.js", "node_modules/x.js", "a//x.js")) {
            String json = manifest("demo", "1.0.0", "", "{\"server\":[\"" + path + "\"]}");
            assertThrows(IllegalArgumentException.class, () -> parse(json));
        }
        var missing = parse(manifest("demo", "1.0.0", "", "{\"server\":[\"missing.js\"]}"));
        assertThrows(IllegalArgumentException.class, () -> missing.descriptor().validateFiles(root, ScriptFilePolicy.legacyRuntime()));
        assertThrows(IllegalArgumentException.class, () -> parse(manifest("demo", "1.0.0", "", "{\"server\":[\"a.js\",\"a.js\"]}")));
    }

    @Test void packageGraphRejectsMissingIncompatibleCyclesAndDisabledProvidersWithoutBlockingUnrelatedPacks() {
        var economy = model("economy", "1.0.0", "", true, ScriptPackScope.GLOBAL);
        var shop = model("shop", "1.0.0", ",\"dependencies\":{\"economy\":\">=1.0.0 <2.0.0\"}", true, ScriptPackScope.GLOBAL);
        var chat = model("chat", "1.0.0", "", true, ScriptPackScope.GLOBAL);
        assertEquals(List.of("economy", "chat", "shop"),
                PackDependencyPlan.resolve(List.of(shop, economy, chat), ENV).ordered().stream().map(ScriptPack::id).toList());
        assertEquals(List.of("chat"), PackDependencyPlan.resolve(List.of(shop, chat), ENV).ordered().stream().map(ScriptPack::id).toList());
        assertTrue(PackDependencyPlan.resolve(List.of(shop, model("economy", "2.0.0", "", true, ScriptPackScope.GLOBAL)), ENV)
                .blocked().get("GLOBAL:shop").contains("version"));
        assertTrue(PackDependencyPlan.resolve(List.of(shop, model("economy", "1.0.0", "", false, ScriptPackScope.GLOBAL)), ENV)
                .blocked().get("GLOBAL:shop").contains("blocked"));
        var cycle = model("economy", "1.0.0", ",\"dependencies\":{\"shop\":\"*\"}", true, ScriptPackScope.GLOBAL);
        var graph = PackDependencyPlan.resolve(List.of(shop, cycle, chat), ENV);
        assertEquals(List.of("chat"), graph.ordered().stream().map(ScriptPack::id).toList());
        assertTrue(graph.blocked().get("GLOBAL:economy").contains("cycle"));
    }

    @Test void sourceResolutionIsDeterministicAndRemoteDependenciesCannotBorrowLocalTrust() {
        var global = model("economy", "1.0.0", "", true, ScriptPackScope.GLOBAL);
        var world = model("economy", "2.0.0", "", true, ScriptPackScope.WORLD);
        var shop = model("shop", "1.0.0", ",\"dependencies\":{\"economy\":\"2.0.0\"}", true, ScriptPackScope.WORLD);
        assertEquals(List.of("WORLD:economy"), PackDependencyPlan.resolve(List.of(global, world, shop), ENV).dependencies().get("WORLD:shop"));
        var remote = model("shop", "1.0.0", ",\"dependencies\":{\"GLOBAL:economy\":\"*\"}", true, ScriptPackScope.SERVER_CACHE);
        assertTrue(PackDependencyPlan.resolve(List.of(global, remote), ENV).blocked().containsKey("SERVER_CACHE:shop"));
    }

    @Test void environmentConditionsAreDistinctFromPackagePresence() {
        var accepted = model("demo", "1.0.0", ",\"environment\":{\"mods\":{\"example\":\">=1.2.0\"},\"loaders\":[\"neoforge\"],\"minecraft\":\">=26.1 <27\"}", true, ScriptPackScope.GLOBAL);
        assertTrue(PackDependencyPlan.resolve(List.of(accepted), ENV).blocked().isEmpty());
        assertTrue(PackDependencyPlan.resolve(List.of(accepted), new PackDependencyPlan.Environment("fabric", "26.2", Map.of())).blocked().containsKey("GLOBAL:demo"));
    }

    @Test void versionRangesAndPrereleaseOrderingHaveExplicitResults() {
        assertTrue(PackVersion.satisfies("1.2.3+build", ">=1.0.0 <2.0.0"));
        assertFalse(PackVersion.satisfies("2.0.0", ">=1.0.0 <2.0.0"));
        assertTrue(PackVersion.parse("1.0.0-alpha.9").compareTo(PackVersion.parse("1.0.0-alpha.10")) < 0);
        assertTrue(PackVersion.parse("1.0.0-alpha").compareTo(PackVersion.parse("1.0.0")) < 0);
        for (String range : List.of("^1.0", "~1.0", "1 || 2", "", ">=", "01.0.0", "1.0.0-01")) {
            assertThrows(IllegalArgumentException.class, () -> PackVersion.validateRange(range));
        }
    }

    @Test void schemaHasEveryParserFieldAndCanBePublishedWithoutMinecraft() {
        var schema = PackDescriptor.schema();
        assertEquals(1, schema.getAsJsonObject("properties").getAsJsonObject("formatVersion").get("const").getAsInt());
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertEquals(4, schema.getAsJsonObject("properties").getAsJsonObject("entries").getAsJsonObject("properties").size());
    }

    private ScriptPack model(String id, String version, String extra, boolean enabled, ScriptPackScope scope) {
        var manifest = parse(manifest(id, version, extra, "{}"));
        return new ScriptPack(id, id, version, scope, root.resolve(scope.name()).resolve(id), enabled, manifest);
    }
    private static ScriptPackManifest parse(String json) {
        return ScriptPackManifest.parse(JsonParser.parseString(json).getAsJsonObject(), "fallback");
    }
    private static String manifest(String id, String version, String extra, String entries) {
        return "{\"formatVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"entries\":" + entries + extra + "}";
    }
}
