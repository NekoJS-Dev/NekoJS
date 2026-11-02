package com.tkisor.nekojs.probe;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.authoring.*;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.backend.typescript.TypeScriptProbeBackend;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringProbePublicationTest {
    @BeforeAll static void platform() { TestPlatformInit.ensureInitialized(); }
    @TempDir Path directory;

    @Test void successfulProbePublishesQueryableFinalBindingsAndFailureRetainsItsPointer() throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        ProbeConfig config = ProbeConfig.defaultConfig();
        var snapshot = LegacyProbeFixture.snapshot();
        List<Class<?>> collected = List.of(LegacyProbeFixture.SampleHelper.class, LegacyProbeFixture.SampleContext.class);
        Path output = directory.resolve("typescript");
        var context = new ProbeContext.Of(snapshot, collected, config, paths, "typescript", output, null);
        var backend = new TypeScriptProbeBackend();
        assertTrue(backend.generate(context).success());
        Path api = context.authoringPaths().snapshots();
        String environment = AuthoringEnvironment.id(AuthoringEnvironment.current());
        JsonObject index = AuthoringSnapshotFiles.load(api, environment);
        assertNotNull(index);
        JsonObject scope = AuthoringJson.obj("environmentId", environment, "snapshotId", index.get("snapshotId"), "scriptType", "SERVER", "language", "typescript", "memberView", "declared");
        try (AuthoringQuery query = new AuthoringQuery(environment)) {
            query.publish(new AuthoringSnapshot(index));
            JsonObject resolved = query.execute("resolve", AuthoringJson.obj("scope", scope, "name", "SampleHelper", "kind", "binding"));
            assertEquals(1, resolved.getAsJsonArray("items").size());
            String owner = resolved.getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId").getAsString();
            scope.addProperty("ownerSymbolId", owner);
            JsonObject result = query.execute("members", AuthoringJson.obj("scope", scope, "name", "of", "select", List.of("name", "parameters", "returnType", "signature", "source")));
            assertEquals(2, result.getAsJsonArray("items").size());
            assertEquals("incomplete", result.getAsJsonObject("coverage").get("state").getAsString());
            for (var item : result.getAsJsonArray("items")) {
                JsonObject source = item.getAsJsonObject().getAsJsonObject("source");
                String text = Files.readString(output.resolve(source.get("path").getAsString()));
                assertEquals(source.get("sha256").getAsString(), AuthoringJson.hash(text));
            }
        }
        String before = Files.readString(api.resolve("current.json"));
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "Keep this file.");
        assertFalse(backend.generate(new ProbeContext.Of(snapshot, collected, config, paths, "typescript", blocked, null)).success());
        assertEquals(before, Files.readString(api.resolve("current.json")));
        assertEquals("Keep this file.", Files.readString(blocked));
    }

    @Test void repeatedBindingDelegateSourcesDoNotDuplicatePublishedMembers() throws Exception {
        var helper = LegacyProbeFixture.SampleHelper.class;
        var bindings = List.of(
                BindingCatalogEntry.of("StaticHelper", ScriptType.SERVER, helper, true)
                        .withExtraDocTypes(List.of(helper, helper)),
                BindingCatalogEntry.of("delegated", ScriptType.SERVER, LegacyProbeFixture.SampleContext.class, false)
                        .withExtraDocTypes(List.of(helper, helper)),
                new BindingCatalogEntry("Aliased", ScriptType.SERVER, helper, true, true, true, "HelperAlias", null, List.of(), List.of()));
        var catalog = new NekoScriptCatalogSnapshot(
                List.of(ScriptType.SERVER), bindings, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                new TypeOutputLayout(Path.of("probe-types"), Path.of("snippets")),
                java.util.Map.of(), List.of());
        var config = ProbeConfig.defaultConfig();
        var context = new ProbeContext.Of(catalog, List.of(helper, LegacyProbeFixture.SampleContext.class), config,
                NekoJSPaths.fromGameDir(directory), "typescript", directory.resolve("typescript"), null);
        assertTrue(new TypeScriptProbeBackend().generate(context).success());
        String environment = AuthoringEnvironment.id(AuthoringEnvironment.current());
        var index = AuthoringSnapshotFiles.load(context.authoringPaths().snapshots(), environment);
        assertNotNull(index);
        try (var query = new AuthoringQuery(environment)) {
            query.publish(new AuthoringSnapshot(index));
            for (String name : List.of("StaticHelper", "delegated")) {
                var scope = AuthoringJson.obj("environmentId", environment, "scriptType", "SERVER", "language", "typescript", "memberView", "declared");
                var resolved = query.execute("resolve", AuthoringJson.obj("scope", scope, "name", name, "kind", "binding"));
                scope.add("ownerSymbolId", resolved.getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId"));
                var result = query.execute("members", AuthoringJson.obj("scope", scope, "name", "of", "select", List.of("signature", "source")));
                assertEquals(2, result.getAsJsonArray("items").size(), name);
                for (var item : result.getAsJsonArray("items")) {
                    var record = item.getAsJsonObject();
                    String text = Files.readString(context.languageDir().resolve(record.getAsJsonObject("source").get("path").getAsString()));
                    assertTrue(text.contains(record.get("signature").getAsString()));
                }
            }
            var scope = AuthoringJson.obj("environmentId", environment, "scriptType", "SERVER", "language", "typescript");
            var resolved = query.execute("resolve", AuthoringJson.obj("scope", scope, "name", "Aliased", "kind", "binding"));
            scope.add("ownerSymbolId", resolved.getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId"));
            assertTrue(query.execute("members", AuthoringJson.obj("scope", scope, "name", "of")).getAsJsonArray("items").isEmpty());
            assertFalse(query.execute("members", AuthoringJson.obj("scope", scope, "name", "name")).getAsJsonArray("items").isEmpty());
        }
    }

    @Test void selectedAuthoringRootDoesNotFollowNestedDeclarationBaseDir() throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        ProbeConfig config = new ProbeConfig(true, "deep/nested/probe", ProbeConfig.ScanConfig.defaultScan());
        AuthoringPaths selected = AuthoringPaths.resolve(directory, "chosen/ai-output");
        Path output = directory.resolve(config.baseDir()).resolve("typescript");
        var context = new ProbeContext.Of(LegacyProbeFixture.snapshot(),
                List.of(LegacyProbeFixture.SampleHelper.class, LegacyProbeFixture.SampleContext.class),
                config, paths, "typescript", output, null, null, null, selected);
        assertTrue(new TypeScriptProbeBackend().generate(context).success());
        assertNotNull(AuthoringSnapshotFiles.load(selected.snapshots(), AuthoringEnvironment.id(AuthoringEnvironment.current())));
        assertTrue(Files.isDirectory(output));
        assertFalse(Files.exists(directory.resolve(config.baseDir()).resolve("authoring")));
        assertFalse(Files.exists(directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY)));
    }

    @Test void retargetedAuthoringRootCannotRedirectSnapshotPublicationAfterDeclarationsCommit(@TempDir Path outside) throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        AuthoringPaths selected = AuthoringPaths.resolve(directory, "selected-ai");
        Path link = directory.resolve("selected-ai");
        try {
            try { Files.createSymbolicLink(link, outside); }
            catch (java.io.IOException | UnsupportedOperationException | SecurityException unavailable) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + unavailable);
            }
            Path output = directory.resolve("typescript");
            var context = new ProbeContext.Of(LegacyProbeFixture.snapshot(),
                    List.of(LegacyProbeFixture.SampleHelper.class, LegacyProbeFixture.SampleContext.class),
                    ProbeConfig.defaultConfig(), paths, "typescript", output, null, null, null, selected);
            var result = new TypeScriptProbeBackend().generate(context);
            assertTrue(result.success(), result.message());
            assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("Authoring snapshot publication failed")));
            assertTrue(Files.isDirectory(output));
            assertFalse(Files.exists(outside.resolve("snapshots")));
        } finally {
            Files.deleteIfExists(link);
        }
    }

    @Test void defaultRuntimeExportsWithoutScanningOrListeningAndClosesCleanly() throws Exception {
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory); paths.initFolders();
        AuthoringRuntime runtime = AuthoringRuntime.open(paths, ProbeConfig.defaultConfig());
        try (runtime) {
            assertEquals("not-ready", runtime.execute("context", new JsonObject()).get("indexState").getAsString());
            AuthoringPaths selected = AuthoringPaths.resolve(directory, AuthoringPaths.DEFAULT_DIRECTORY);
            assertTrue(Files.exists(selected.instructions()));
            assertFalse(Files.exists(paths.config().resolve("ai.token")));
            JsonObject connection = AuthoringJson.parse(Files.readString(selected.connection()));
            assertFalse(connection.get("listening").getAsBoolean());
        }
        runtime.close();
        assertEquals("SERVICE_CLOSED", assertThrows(AuthoringException.class, () -> runtime.execute("context", new JsonObject())).code());
    }
}
