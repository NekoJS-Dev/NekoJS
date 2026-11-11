package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringStdioProcessTest {
    @TempDir Path directory;
    private record Result(int exit, String output, String errors) {}

    private Result launch(String input, String... args) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var command = new ArrayList<>(List.of(java, "-jar", System.getProperty("nekojs.authoring.stdio.jar")));
        command.addAll(List.of(args));
        Path output = Files.createTempFile(directory, "stdout-", ".txt");
        Path errors = Files.createTempFile(directory, "stderr-", ".txt");
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
        try {
            try (var stream = process.getOutputStream()) { stream.write(input.getBytes(StandardCharsets.UTF_8)); }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Standalone MCP did not exit after EOF.");
            return new Result(process.exitValue(), Files.readString(output), Files.readString(errors));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        }
    }
    private static JsonObject rpc(int id, String method, JsonObject params) {
        return AuthoringJson.obj("jsonrpc", "2.0", "id", id, "method", method, "params", params);
    }
    private static JsonObject tool(int id, String name, JsonObject args) { return rpc(id, "tools/call", AuthoringJson.obj("name", name, "arguments", args)); }
    private static JsonObject structured(JsonObject reply) { return reply.getAsJsonObject("result").getAsJsonObject("structuredContent"); }

    @ParameterizedTest
    @ValueSource(strings = {"default", "configuration", "override"})
    void realJarRunsWithoutGameClasspathAndStdoutContainsOnlyMcpResponses(String discovery) throws Exception {
        Path game = directory.resolve("game with spaces");
        String aiDirectory = discovery.equals("default") ? AuthoringPaths.DEFAULT_DIRECTORY : "custom/AI tools";
        Path descriptor = game.resolve(aiDirectory + "/environment.json");
        Files.createDirectories(descriptor.getParent());
        JsonObject environment = AuthoringJson.obj("minecraft", "fixture", "loader", "offline");
        String environmentId = "environment:" + AuthoringJson.hash(AuthoringJson.canonical(environment));
        Files.writeString(descriptor, AuthoringJson.obj("schemaVersion", "1", "instanceId", "fixture", "environment", environment,
                "environmentId", environmentId, "apiEnabled", true, "apiSnapshotDirectory", aiDirectory + "/snapshots").toString());
        JsonObject snapshot = AuthoringQueryTest.fixture("s1", List.of(AuthoringQueryTest.member("overload", "convert", "string", "number")));
        snapshot.addProperty("environmentId", environmentId);
        AuthoringSnapshotFiles.publish(game.resolve(aiDirectory + "/snapshots"), snapshot);
        if (!discovery.equals("default")) {
            Path config = game.resolve("nekojs/config/ai.toml");
            Files.createDirectories(config.getParent());
            Files.writeString(config, discovery.equals("override") ? "invalid TOML"
                    : "[output]\ndirectory = '" + aiDirectory + "'\n[mcp]\nenabled = true\n");
            Files.writeString(config.resolveSibling("ai.token"), "offline-must-not-relay-this-token");
        }
        Map<String, String> before = files(game);
        JsonObject query = AuthoringQueryTest.request("members", null);
        query.getAsJsonObject("scope").addProperty("environmentId", environmentId);
        String initialize = rpc(1, "initialize", AuthoringJson.obj("protocolVersion", AuthoringMcp.VERSION, "capabilities", new JsonObject(), "clientInfo", AuthoringJson.obj("name", "process-test", "version", "1"))).toString();
        String input = initialize + "\n" + AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized") + "\n"
                + tool(2, "nekojs_context", new JsonObject()) + "\n" + tool(3, "nekojs_query", query) + "\n";
        Result result = discovery.equals("override")
                ? launch(input, "--ai-dir", aiDirectory, "--game-dir", game.toString())
                : launch(input, "--game-dir", game.toString());
        assertEquals(0, result.exit(), result.errors());
        assertEquals("", result.errors());
        List<JsonObject> replies = result.output().lines().map(AuthoringJson::parse).toList();
        assertEquals(3, replies.size());
        assertEquals(AuthoringMcp.VERSION, replies.get(0).getAsJsonObject("result").get("protocolVersion").getAsString());
        assertEquals("ready", structured(replies.get(1)).get("indexState").getAsString());
        assertEquals("stdio", structured(replies.get(1)).get("transport").getAsString());
        assertEquals("overload", structured(replies.get(2)).getAsJsonArray("items").get(0).getAsJsonObject().get("symbolId").getAsString());
        assertEquals(before, files(game), "Offline discovery must not modify any exported or configuration file.");
        if (discovery.equals("default")) assertFalse(Files.exists(game.resolve("nekojs/config")));
        assertFalse(Files.exists(game.resolve("nekojs/config/ai-location.json")));
        assertFalse(Files.exists(game.resolve(aiDirectory + "/connection.json")));
        try (JarFile jar = new JarFile(System.getProperty("nekojs.authoring.stdio.jar"))) {
            assertEquals("com.tkisor.nekojs.authoring.AuthoringStdioMain", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            assertEquals("true", jar.getManifest().getMainAttributes().getValue("Multi-Release"));
            assertNotNull(jar.getEntry("com/google/gson/Gson.class"));
            assertNotNull(jar.getEntry("com/electronwill/nightconfig/toml/TomlParser.class"));
            assertNotNull(jar.getEntry("META-INF/licenses/night-config/LICENSE.txt"));
            assertNotNull(jar.getEntry("META-INF/licenses/night-config/NOTICE.txt"));
            assertNotNull(jar.getEntry("com/tkisor/nekojs/authoring/AuthoringLocator.class"));
            assertNotNull(jar.getEntry("com/tkisor/nekojs/authoring/AuthoringPaths.class"));
            assertNotNull(jar.getEntry("nekojs/authoring/manifest.json"));
            assertNull(jar.getEntry("com/tkisor/nekojs/NekoJS.class"));
            assertNull(jar.getEntry("com/tkisor/nekojs/authoring/AuthoringRuntime.class"));
            assertNull(jar.getEntry("com/tkisor/nekojs/authoring/AuthoringHttpServer.class"));
            assertNull(jar.getEntry("com/tkisor/nekojs/authoring/AuthoringConfig.class"));
            assertTrue(jar.stream().filter(entry -> entry.getName().endsWith(".class"))
                    .map(entry -> entry.getName().replaceFirst("^META-INF/versions/[0-9]+/", ""))
                    .allMatch(name -> name.startsWith("com/google/gson/") || name.startsWith("com/google/errorprone/annotations/")
                            || name.startsWith("com/electronwill/nightconfig/") || name.startsWith("com/tkisor/nekojs/authoring/")));
        }
    }

    private static Map<String, String> files(Path root) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.toList()) result.put(root.relativize(file).toString(), Files.isDirectory(file) ? "<directory>" : Files.readString(file));
        }
        return result;
    }

    @Test void helpAndInvalidStartupNeverWriteToProtocolStdout() throws Exception {
        Result help = launch("", "--help");
        assertEquals(0, help.exit()); assertEquals("", help.output()); assertTrue(help.errors().contains("--game-dir"));
        assertTrue(help.errors().contains("--ai-dir")); assertTrue(help.errors().contains("ai.toml"));
        assertFalse(help.errors().contains("ai-location.json"));
        assertTrue(help.errors().contains(AuthoringPaths.DEFAULT_DIRECTORY));
        Result missing = launch("");
        assertEquals(2, missing.exit()); assertEquals("", missing.output()); assertTrue(missing.errors().contains("NEKO-4055"));
        Result unexported = launch("", "--game-dir", directory.toString());
        assertEquals(2, unexported.exit()); assertEquals("", unexported.output()); assertTrue(unexported.errors().contains("NEKO-1019"));
    }
    @Test void duplicateUnknownAndIncompleteFlagsAreRejectedBeforeDiscovery() throws Exception {
        for (String[] args : List.of(new String[]{"--game-dir", directory.toString(), "--game-dir", directory.toString()},
                new String[]{"--game-dir", directory.toString(), "--ai-dir", "one", "--ai-dir", "two"},
                new String[]{"--ai-dir", "one"}, new String[]{"--game-dir", directory.toString(), "--unknown", "one"},
                new String[]{"--game-dir", directory.toString(), "--ai-dir"}, new String[]{"--game-dir", "--ai-dir"})) {
            Result result = launch("", args);
            assertEquals(2, result.exit()); assertEquals("", result.output()); assertTrue(result.errors().contains("NEKO-4055"));
        }
        assertFalse(Files.exists(directory.resolve("nekojs")));
    }

    @Test void escapingOverrideAndConfigurationAreRejectedWithoutWrites() throws Exception {
        Path game = Files.createDirectory(directory.resolve("game"));
        Path config = game.resolve("nekojs/config/ai.toml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "[output]\ndirectory = '../escape'\n");
        Map<String, String> before = files(game);
        for (String[] args : List.of(new String[]{"--game-dir", game.toString()},
                new String[]{"--game-dir", game.toString(), "--ai-dir", "../escape"})) {
            Result result = launch("", args);
            assertEquals(2, result.exit()); assertEquals("", result.output()); assertTrue(result.errors().contains("NEKO-1019"));
            assertEquals(before, files(game));
        }
    }
}
