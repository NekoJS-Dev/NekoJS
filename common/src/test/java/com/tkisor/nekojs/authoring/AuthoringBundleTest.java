package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringBundleTest {
    @TempDir Path directory;

    @Test void bundleHashesEnglishContentLinksAndExportsPreserveUserFiles() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        assertEquals("en", bundle.manifest().get("language").getAsString());
        assertFalse(bundle.paths().isEmpty());
        for (String path : bundle.paths()) {
            String text = bundle.read(path);
            assertFalse(Pattern.compile("[\\p{IsHan}]").matcher(text).find(), path);
            if (!path.endsWith(".md")) continue;
            var links = Pattern.compile("\\]\\(([^)]+)\\)").matcher(text);
            while (links.find()) {
                String target = links.group(1);
                if (target.startsWith("<") && target.endsWith(">")) target = target.substring(1, target.length() - 1);
                if (target.contains("://") || target.startsWith("#")) continue;
                String resolved = Path.of(path).getParent().resolve(target.split("#")[0]).normalize().toString().replace('\\', '/');
                assertTrue(bundle.paths().contains(resolved), path + " -> " + target);
            }
        }
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        paths.initFolders();
        Files.writeString(paths.root().resolve("AGENTS.md"), "Legacy author rules\n");
        Path ai = directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY);
        Files.createDirectories(ai);
        Files.writeString(ai.resolve("AGENTS.md"), "Author rules\n");
        AuthoringBundleExporter.export(bundle, paths, true);
        assertEquals("Author rules\n", Files.readString(ai.resolve("AGENTS.md")));
        assertEquals("Legacy author rules\n", Files.readString(paths.root().resolve("AGENTS.md")));
        Path current = directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY).resolve("current.json");
        String pointer = Files.readString(current);
        AuthoringBundleExporter.export(bundle, paths, true);
        assertEquals(pointer, Files.readString(current));
        Path handbook = ai.resolve("bundled/" + bundle.id() + "/handbook/events.md");
        Files.writeString(handbook, "Author annotations\n");
        assertThrows(java.io.IOException.class, () -> AuthoringBundleExporter.export(bundle, paths, true));
        assertEquals("Author annotations\n", Files.readString(handbook));
        assertEquals(pointer, Files.readString(current));
    }

    @Test void missingWorkspaceEntryIsCreatedAndExamplesExecuteTheirStatedContracts() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        paths.initFolders(); AuthoringBundleExporter.export(bundle, paths, true);
        assertEquals(bundle.read("templates/AGENTS.md"), Files.readString(directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY).resolve("AGENTS.md")));
        try (Context context = Context.newBuilder("js").build()) {
            context.eval("js", "globalThis.messages = []; globalThis.callbacks = []; globalThis.console = {info: text => messages.push(text)}; globalThis.ServerEvents = {recipes: callback => callbacks.push(callback)};");
            context.eval("js", bundle.read("examples/greeting.js"));
            assertEquals(1, context.eval("js", "messages.length").asInt());
            context.eval("js", "messages.length = 0");
            context.eval("js", bundle.read("examples/event-subscription.js"));
            assertEquals(1, context.eval("js", "callbacks.length").asInt());
            assertEquals(0, context.eval("js", "messages.length").asInt());
            context.eval("js", "callbacks[0]({})");
            assertEquals(1, context.eval("js", "messages.length").asInt());
        }
    }

    @Test void editedExportManifestIsRejectedWithoutSwitchingThePointer() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        paths.initFolders();
        AuthoringBundleExporter.export(bundle, paths, true);
        Path current = directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY).resolve("current.json");
        String pointer = Files.readString(current);
        Path manifest = directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY).resolve("bundled/" + bundle.id() + "/manifest.json");
        String edited = "{\"modified\":true}\n";
        Files.writeString(manifest, edited);

        assertThrows(java.io.IOException.class, () -> AuthoringBundleExporter.export(bundle, paths, true));
        assertEquals(edited, Files.readString(manifest));
        assertEquals(pointer, Files.readString(current));
    }

    @Test void documentedDefaultsAndMcpExamplesMatchTheImplementation() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        String guide = bundle.read("handbook/connections.md");
        var config = Pattern.compile("```toml\n(.*?)```", Pattern.DOTALL).matcher(guide);
        assertTrue(config.find());
        assertEquals(AuthoringConfig.DEFAULT_TEXT.strip(), config.group(1).strip());
        assertFalse(config.find());
        var examples = jsonExamples(guide).stream().filter(example -> example.has("jsonrpc")).toList();
        assertEquals(2, examples.size());
        assertEquals(AuthoringMcp.VERSION, examples.get(0).getAsJsonObject("params").get("protocolVersion").getAsString());
        try (AuthoringQuery query = AuthoringQueryTest.engine();
             AuthoringMcp mcp = new AuthoringMcp(bundle, query::execute)) {
            var initialized = mcp.handle(examples.get(0), null, null);
            assertEquals(200, initialized.status());
            assertNotNull(initialized.session());
            assertEquals(AuthoringMcp.VERSION, initialized.body().getAsJsonObject("result").get("protocolVersion").getAsString());
            var notification = AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized");
            assertEquals(202, mcp.handle(notification, initialized.session(), AuthoringMcp.VERSION).status());
            var called = mcp.handle(examples.get(1), initialized.session(), AuthoringMcp.VERSION);
            assertEquals(200, called.status());
            var result = called.body().getAsJsonObject("result");
            assertFalse(result.get("isError").getAsBoolean());
            assertEquals("env", result.getAsJsonObject("structuredContent").get("environmentId").getAsString());
        }
    }

    @Test void documentedInModMcpRequiresOptInAndEndpointCredentialsInsteadOfProcessPaths() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        var examples = jsonExamples(bundle.read("handbook/connections.md")).stream()
                .filter(example -> example.has("mcpServers")).toList();
        assertEquals(1, examples.size());
        JsonObject client = examples.get(0).getAsJsonObject("mcpServers").getAsJsonObject("nekojs");
        assertEquals(java.util.Set.of("url", "headers"), client.keySet());
        var endpoint = java.net.URI.create(client.get("url").getAsString());
        assertEquals("http", endpoint.getScheme());
        assertEquals("127.0.0.1", endpoint.getHost());
        assertEquals("/mcp", endpoint.getPath());
        assertEquals("Bearer <token>", client.getAsJsonObject("headers").get("Authorization").getAsString());

        Path file = directory.resolve("ai.toml");
        AuthoringConfig defaults = AuthoringConfig.load(file);
        assertFalse(defaults.mcp());
        assertFalse(defaults.http());
        assertEquals(defaults.port(), endpoint.getPort());
        Files.writeString(file, AuthoringConfig.DEFAULT_TEXT.replace("[mcp]\nenabled = false", "[mcp]\nenabled = true"));
        AuthoringConfig optedIn = AuthoringConfig.load(file);
        assertTrue(optedIn.mcp());
        assertFalse(optedIn.http());
        assertEquals(defaults.port(), optedIn.port());
    }

    @Test void documentedStdioClientConfigurationMatchesTheRunnableCompanion() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        var examples = jsonExamples(bundle.read("handbook/stdio.md"));
        assertEquals(1, examples.size());
        JsonObject client = examples.get(0).getAsJsonObject("mcpServers").getAsJsonObject("nekojs");
        assertEquals("java", client.get("command").getAsString());
        var args = client.getAsJsonArray("args");
        assertEquals(4, args.size());
        assertEquals("-jar", args.get(0).getAsString());
        assertTrue(args.get(1).getAsString().endsWith("nekojs-" + bundle.manifest().get("version").getAsString() + "-authoring-stdio.jar"));
        assertEquals("--game-dir", args.get(2).getAsString());
        assertEquals("/absolute/game/directory", args.get(3).getAsString());
    }

    @Test void documentedQueryRequestsExecuteAgainstMatchingSnapshotAndLiveFixtures() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        var requests = jsonExamples(bundle.read("handbook/query-api.md"));
        assertEquals(6, requests.size(), "Four snapshot requests and two live result requests must all execute");
        try (AuthoringQuery query = AuthoringQueryTest.engine(AuthoringQueryTest.member("example", "of", "ItemStack", "string"))) {
            assertEquals(1, query.execute("resolve", requests.get(0)).getAsJsonArray("items").size());
            assertEquals(1, query.execute("members", requests.get(1)).getAsJsonArray("items").size());
            assertEquals(1, query.execute("query", requests.get(2)).getAsJsonArray("items").size());
            var batch = query.execute("batch", requests.get(3)).getAsJsonArray("results");
            assertEquals(2, batch.size());
            for (var result : batch) assertEquals("ok", result.getAsJsonObject().get("status").getAsString());
        }
        com.tkisor.nekojs.testfixture.TestPlatformInit.ensureInitialized();
        var paths = NekoJSPaths.fromGameDir(directory.resolve("documented-live"));
        paths.initFolders();
        Files.writeString(paths.config().resolve("ai.toml"),
                "[documentation]\nenabled = false\n[api]\nenabled = false\n[control]\ncommands = true\ncommand_allowlist = [\"list\"]\n");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        try (var runtime = AuthoringRuntime.open(paths, com.tkisor.nekojs.probe.ProbeConfig.defaultConfig())) {
            var diagnostic = new com.tkisor.nekojs.core.error.ScriptDiagnosticRecord("documented-error",
                    com.tkisor.nekojs.api.ScriptType.SERVER, com.tkisor.nekojs.core.error.DiagnosticPhase.EXECUTION,
                    "Script Execution Environment", 1, false, "server_scripts/example.js", 2, 3, null, null,
                    "Example failure", "Example cause");
            runtime.installDiagnostics(() -> java.util.List.of(diagnostic));
            JsonObject errors = runtime.execute("errors", requests.get(4));
            assertTrue(errors.get("available").getAsBoolean());
            assertEquals("documented-error", errors.getAsJsonArray("errors").get(0).getAsJsonObject().get("errorId").getAsString());
            runtime.installHost(new com.tkisor.nekojs.api.authoring.AuthoringControlHost() {
                @Override public long epoch() { return 1; }
                @Override public void enqueue(Runnable operation) { operation.run(); }
                @Override public java.util.concurrent.CompletionStage<CommandResult> executeCommand(
                        String command, java.util.function.Consumer<String> output) {
                    calls.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(new CommandResult(1, true));
                }
                @Override public java.util.concurrent.CompletionStage<ReloadResult> reloadServer(java.util.function.Consumer<String> output) {
                    return java.util.concurrent.CompletableFuture.completedFuture(new ReloadResult(true, true));
                }
            });
            JsonObject admitted = runtime.execute(AuthoringControl.EXECUTE_COMMAND, AuthoringJson.obj("command", "list"));
            JsonObject statusRequest = requests.get(5).deepCopy();
            assertTrue(statusRequest.get("operationId").getAsString().startsWith("<operationId"));
            statusRequest.add("operationId", admitted.get("operationId").deepCopy());
            JsonObject status = runtime.execute("operation_status", statusRequest);
            assertTrue(status.get("found").getAsBoolean());
            assertTrue(status.get("terminal").getAsBoolean());
            assertEquals("succeeded", status.getAsJsonObject("latestResult").get("status").getAsString());
            assertEquals(1, calls.get(), "Reading the documented status request must not dispatch again");
        }
    }

    private static java.util.List<JsonObject> jsonExamples(String guide) {
        var examples = new java.util.ArrayList<JsonObject>();
        var blocks = Pattern.compile("```json\n(.*?)```", Pattern.DOTALL).matcher(guide);
        while (blocks.find()) {
            String json = blocks.group(1).replace("<environmentId>", "env")
                    .replace("<snapshotId>", "s1").replace("<ownerSymbolId>", "owner");
            examples.add(AuthoringJson.parse(json));
        }
        return examples;
    }

    @Test void configDefaultsDoNotOpenAListenerAndInvalidSettingsRemainRejected() throws Exception {
        Path config = directory.resolve("ai.toml");
        AuthoringConfig defaults = AuthoringConfig.load(config);
        assertTrue(defaults.documentation()); assertFalse(defaults.http()); assertFalse(defaults.mcp());
        assertEquals(".github/neko_ai", defaults.directory());
        assertFalse(defaults.editorAgents());
        assertFalse(defaults.controls().commandsEnabled());
        assertFalse(defaults.controls().reloadEnabled());
        assertTrue(defaults.controls().commandAllowlist().isEmpty());
        Files.writeString(config, "[mcp]\nenabled = true\nhost = '0.0.0.0'\n");
        assertThrows(java.io.IOException.class, () -> AuthoringConfig.load(config));
        assertTrue(Files.readString(config).contains("0.0.0.0"));
        Files.writeString(config, "[mcp]\nenabled = 'true'\n");
        assertThrows(java.io.IOException.class, () -> AuthoringConfig.load(config));
    }

    @Test void customOutputIsContainedAndDoesNotMoveLegacyArtifacts() throws Exception {
        AuthoringBundle bundle = AuthoringBundle.load();
        Path legacy = directory.resolve("nekojs/ai/current.json");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "Legacy pointer\n");
        AuthoringPaths custom = AuthoringPaths.resolve(directory, "custom/ai #100%");
        AuthoringBundleExporter.export(bundle, custom, true);
        assertTrue(Files.exists(custom.current()));
        assertTrue(Files.exists(custom.instructions()));
        for (String file : bundle.paths()) assertEquals(bundle.read(file), Files.readString(custom.bundled().resolve(bundle.id()).resolve(file)));
        assertFalse(Files.exists(directory.resolve(AuthoringPaths.DEFAULT_DIRECTORY)));
        assertEquals("Legacy pointer\n", Files.readString(legacy));
    }

    @Test void controlAndOutputConfigRequiresExplicitTypedAuthorization() throws Exception {
        Path file = directory.resolve("ai.toml");
        Files.writeString(file, "[output]\ndirectory = 'custom/ai'\neditor_agents = true\n[control]\ncommands = true\nreload = true\ncommand_allowlist = ['say', 'time']\n");
        var config = AuthoringConfig.load(file);
        assertEquals("custom/ai", config.directory());
        assertTrue(config.editorAgents());
        assertTrue(config.controls().commandsEnabled());
        assertTrue(config.controls().reloadEnabled());
        assertEquals(java.util.Set.of("say", "time"), config.controls().commandAllowlist());
        assertFalse(config.mcp());
        for (String invalid : java.util.List.of("[control]\ncommands = 'true'", "[control]\ncommand_allowlist = 'say'",
                "[control]\ncommand_allowlist = ['execute as']", "[output]\ndirectory = 17", "[output]\neditor_agents = 'true'")) {
            Files.writeString(file, invalid);
            assertThrows(java.io.IOException.class, () -> AuthoringConfig.load(file), invalid);
        }
    }

    @Test void streamedPublicationPreservesCompactBytesIdentityAndRepeatedPublication() throws Exception {
        var document = AuthoringQueryTest.fixture("streamed", java.util.List.of());
        document.add("values", AuthoringJson.parse("{\"null\":null,\"number\":1e+02,\"text\":\"<>&\\n漢字😀\"}"));
        String expected = document.toString();
        AuthoringSnapshotFiles.publish(directory, document);
        String pointer = Files.readString(directory.resolve("current.json"));
        var reference = AuthoringJson.parse(pointer);
        String digest = AuthoringJson.hash(expected);
        assertEquals(digest, reference.get("sha256").getAsString());
        assertEquals(digest + ".json", reference.get("file").getAsString());
        assertArrayEquals(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), Files.readAllBytes(directory.resolve(digest + ".json")));
        AuthoringSnapshotFiles.publish(directory, document);
        assertEquals(pointer, Files.readString(directory.resolve("current.json")));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".authoring-")));
        }
    }

    @Test void failedUtf8PublicationRetainsThePreviousPointerAndRemovesItsTemporaryFile() throws Exception {
        var first = AuthoringQueryTest.fixture("s1", java.util.List.of());
        AuthoringSnapshotFiles.publish(directory, first);
        String pointer = Files.readString(directory.resolve("current.json"));
        Path published = directory.resolve(AuthoringJson.parse(pointer).get("file").getAsString());
        byte[] previous = Files.readAllBytes(published);
        var rejected = first.deepCopy();
        rejected.addProperty("invalid", String.valueOf((char) 0xd800));
        assertThrows(java.io.IOException.class, () -> AuthoringSnapshotFiles.publish(directory, rejected));
        assertEquals(pointer, Files.readString(directory.resolve("current.json")));
        assertArrayEquals(previous, Files.readAllBytes(published));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".authoring-")));
        }
    }

    @Test void invalidPublicationAndTamperingDoNotReplaceThePreviousVerifiedSnapshot() throws Exception {
        var first = AuthoringQueryTest.fixture("s1", java.util.List.of());
        AuthoringSnapshotFiles.publish(directory, first);
        assertEquals("s1", AuthoringSnapshotFiles.load(directory, "env").get("snapshotId").getAsString());
        String pointer = Files.readString(directory.resolve("current.json"));
        String digest = AuthoringJson.parse(pointer).get("sha256").getAsString();
        Files.writeString(directory.resolve(digest + ".json"), "{}");
        assertThrows(java.io.IOException.class, () -> AuthoringSnapshotFiles.load(directory, "env"));
        assertThrows(java.io.IOException.class, () -> AuthoringSnapshotFiles.publish(directory, first));
        assertEquals(pointer, Files.readString(directory.resolve("current.json")));
        assertNull(AuthoringSnapshotFiles.load(directory, "other-env"));
    }
}
