package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringSnapshotSessionTest {
    @TempDir Path game;
    private String outputDirectory = AuthoringPaths.DEFAULT_DIRECTORY;
    private final JsonObject environment = AuthoringJson.obj("minecraft", "fixture", "loader", "test");
    private String environmentId() { return "environment:" + AuthoringJson.hash(AuthoringJson.canonical(environment)); }

    private JsonObject descriptor(boolean enabled) throws Exception {
        JsonObject descriptor = AuthoringJson.obj("schemaVersion", "1", "instanceId", "fixture-instance", "environmentId", environmentId(),
                "environment", environment, "apiEnabled", enabled, "apiSnapshotDirectory", outputDirectory + "/snapshots");
        writeDescriptor(descriptor);
        return descriptor;
    }
    private void writeDescriptor(JsonObject descriptor) throws Exception {
        Path file = game.resolve(outputDirectory + "/environment.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, descriptor.toString());
    }
    private void publish(String id) throws Exception {
        JsonObject snapshot = AuthoringQueryTest.fixture(id, List.of(AuthoringQueryTest.member(id + "-member", "convert", "string", "number")));
        snapshot.addProperty("environmentId", environmentId());
        AuthoringSnapshotFiles.publish(game.resolve(outputDirectory + "/snapshots"), snapshot);
    }
    private JsonObject request() { return AuthoringJson.obj("from", "members", "scope", AuthoringJson.obj("environmentId", environmentId(), "language", "typescript", "scriptType", "SERVER", "memberView", "declared")); }

    @Test void defaultSnapshotDirectoryRefreshesFromLastSuccessfulPublicationWithoutWrites() throws Exception {
        descriptor(true);
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            assertEquals("not-ready", session.execute("context", new JsonObject()).get("indexState").getAsString());
            publish("s1");
            assertEquals("s1-member", session.execute("query", request()).getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId").getAsString());
            publish("s2");
            assertEquals("s2", session.execute("context", new JsonObject()).get("snapshotId").getAsString());
            assertEquals("s2-member", session.execute("query", request()).getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId").getAsString());
            JsonObject old = request(); old.getAsJsonObject("scope").addProperty("snapshotId", "s1");
            assertEquals("s1-member", session.execute("query", old).getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId").getAsString());
            publish("s1");
            assertEquals("s1", session.execute("context", new JsonObject()).get("snapshotId").getAsString());
        }
        assertFalse(Files.exists(game.resolve("nekojs/config")));
        assertFalse(Files.exists(game.resolve(outputDirectory + "/connection.json")));
    }

    @Test void disabledExportAndUnavailableCurrentPointerNeverSilentlyServeOldCurrentData() throws Exception {
        descriptor(true); publish("s1");
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            assertEquals("ready", session.execute("context", new JsonObject()).get("indexState").getAsString());
            Files.delete(game.resolve(outputDirectory + "/snapshots/current.json"));
            assertEquals("unavailable", session.execute("context", new JsonObject()).get("indexState").getAsString());
            assertEquals("INDEX_UNAVAILABLE", assertThrows(AuthoringException.class, () -> session.execute("query", request())).code());
            descriptor(false);
            assertEquals("disabled", session.execute("context", new JsonObject()).get("indexState").getAsString());
            assertEquals("INDEX_DISABLED", assertThrows(AuthoringException.class, () -> session.execute("query", request())).code());
            assertNotNull(session.execute("schema", new JsonObject()));
        }
    }

    @Test void changedEnvironmentRequiresRestartAndTamperedSnapshotsAreUnavailable() throws Exception {
        descriptor(true); publish("s1");
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            environment.addProperty("minecraft", "different"); descriptor(true);
            assertEquals("unavailable", session.execute("context", new JsonObject()).get("indexState").getAsString());
        }
        environment.addProperty("minecraft", "fixture"); descriptor(true);
        Path pointer = game.resolve(outputDirectory + "/snapshots/current.json");
        String digest = AuthoringJson.parse(Files.readString(pointer)).get("sha256").getAsString();
        Files.writeString(pointer.resolveSibling(digest + ".json"), "{}");
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            assertEquals("unavailable", session.execute("context", new JsonObject()).get("indexState").getAsString());
        }
    }

    @Test void loadingAnObservedPointerDoesNotReopenAConcurrentlyReplacedPointer() throws Exception {
        descriptor(true); publish("s1");
        Path snapshots = game.resolve(outputDirectory + "/snapshots");
        String observed = Files.readString(snapshots.resolve("current.json"));
        publish("s2");
        assertEquals("s1", AuthoringSnapshotFiles.load(snapshots, environmentId(), observed).get("snapshotId").getAsString());
        assertEquals("s2", AuthoringSnapshotFiles.load(snapshots, environmentId()).get("snapshotId").getAsString());
    }

    @Test void invalidDiscoveryAndEscapingPathsAreRejectedWithoutCreatingFiles() throws Exception {
        assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
        JsonObject value = descriptor(true);
        value.addProperty("apiSnapshotDirectory", "../outside"); writeDescriptor(value);
        assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
        value.addProperty("apiSnapshotDirectory", outputDirectory + "/snapshots");
        value.addProperty("environmentId", "wrong"); writeDescriptor(value);
        assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
        Files.writeString(game.resolve(outputDirectory + "/environment.json"), " ".repeat(64 * 1024 + 1));
        assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
    }
    private void locator(String text) throws Exception {
        Path file = game.resolve("nekojs/config/ai-location.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test void locatorSelectsCustomRootAndExplicitOverrideWinsWithoutReadingInvalidLocator() throws Exception {
        descriptor(false);
        outputDirectory = "custom/AI tools";
        descriptor(true); publish("custom");
        locator(AuthoringJson.obj("schemaVersion", 1, "directory", outputDirectory).toString());
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            assertEquals("custom", session.execute("context", new JsonObject()).get("snapshotId").getAsString());
            locator("invalid locator");
            assertEquals("custom", session.execute("context", new JsonObject()).get("snapshotId").getAsString());
        }
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load(), outputDirectory)) {
            assertEquals("custom", session.execute("context", new JsonObject()).get("snapshotId").getAsString());
        }
        assertFalse(Files.exists(game.resolve("nekojs/config/ai.token")));
    }

    @Test void malformedOrMissingLocatorSelectionNeverFallsBackToValidDefault() throws Exception {
        descriptor(false);
        for (String invalid : List.of("{}", "[]", "{broken", AuthoringJson.obj("schemaVersion", "1", "directory", outputDirectory).toString(),
                AuthoringJson.obj("schemaVersion", 2, "directory", outputDirectory).toString(), AuthoringJson.obj("schemaVersion", 1, "directory", "../escape").toString(),
                AuthoringJson.obj("schemaVersion", 1, "directory", "missing/export").toString(), " ".repeat(4097))) {
            locator(invalid);
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()), invalid);
        }
    }

    @Test void explicitDirectoriesRejectTraversalAndMissingExportsWithoutDiscoveryFallback() throws Exception {
        descriptor(false);
        for (String invalid : List.of("", "../escape", "custom/../escape", "/absolute", "C:/escape", "custom\\escape", "missing/export")) {
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load(), invalid), invalid);
        }
        assertFalse(Files.exists(game.resolve("missing")));
        assertFalse(Files.exists(game.resolve("nekojs/config")));
    }

    @Test void modernDescriptorsCannotSelectSnapshotsFromAnotherContainedRoot() throws Exception {
        JsonObject value = descriptor(true);
        value.addProperty("apiSnapshotDirectory", "other/snapshots"); writeDescriptor(value);
        assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
    }

    @Test void legacyFallbackRetainsContainedCustomSnapshotsOnlyWithoutDefaultOrLocator() throws Exception {
        outputDirectory = "nekojs/ai";
        JsonObject value = descriptor(true);
        value.addProperty("apiSnapshotDirectory", "custom-probe/authoring"); writeDescriptor(value);
        JsonObject snapshot = AuthoringQueryTest.fixture("legacy", List.of());
        snapshot.addProperty("environmentId", environmentId());
        AuthoringSnapshotFiles.publish(game.resolve("custom-probe/authoring"), snapshot);
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            assertEquals("legacy", session.execute("context", new JsonObject()).get("snapshotId").getAsString());
        }
        assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load(), outputDirectory));
        outputDirectory = AuthoringPaths.DEFAULT_DIRECTORY;
        descriptor(false);
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            assertEquals("disabled", session.execute("context", new JsonObject()).get("indexState").getAsString());
        }
        Files.writeString(game.resolve(outputDirectory + "/environment.json"), "{}");
        assertThrows(AuthoringException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
    }

    @Test void symlinkedOverridesLocatorsAndDescriptorChildrenCannotEscapeTheGameOrRoot() throws Exception {
        Path outside = Files.createTempDirectory("authoring-outside-");
        try {
            Path alias = game.resolve("alias");
            try { Files.createSymbolicLink(alias, outside); }
            catch (UnsupportedOperationException | java.nio.file.FileSystemException failure) {
                org.junit.jupiter.api.Assumptions.abort("Symlink creation is unavailable: " + failure.getMessage());
            }
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load(), "alias/missing"));
            locator(AuthoringJson.obj("schemaVersion", 1, "directory", "alias/missing").toString());
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
            Files.delete(game.resolve("nekojs/config/ai-location.json"));
            Files.createSymbolicLink(game.resolve("nekojs/config/ai-location.json"), outside.resolve("missing.json"));
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
            Files.delete(game.resolve("nekojs/config/ai-location.json"));
            Path descriptor = game.resolve(outputDirectory + "/environment.json");
            Files.createDirectories(descriptor.getParent());
            Files.createSymbolicLink(descriptor, outside.resolve("missing.json"));
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
            Files.delete(descriptor);
            descriptor(true);
            Path sibling = Files.createDirectory(game.resolve("other"));
            Files.createSymbolicLink(game.resolve(outputDirectory + "/snapshots"), sibling);
            assertThrows(java.io.IOException.class, () -> new AuthoringSnapshotSession(game, AuthoringBundle.load()));
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void removedDescriptorStillRejectsOfflineControlsBeforeRefreshingOrParsingArguments() throws Exception {
        descriptor(true);
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load())) {
            Files.delete(game.resolve(outputDirectory + "/environment.json"));
            for (String operation : List.of("execute_game_command", "reload_scripts")) {
                assertEquals("LIVE_RUNTIME_REQUIRED", assertThrows(AuthoringException.class, () -> session.execute(operation, null)).code());
            }
        }
    }
}
