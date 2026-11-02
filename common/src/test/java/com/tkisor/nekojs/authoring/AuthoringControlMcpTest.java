package com.tkisor.nekojs.authoring;

import com.google.gson.*;
import com.tkisor.nekojs.api.authoring.AuthoringControlHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringControlMcpTest {
    @TempDir Path game;

    private static JsonObject request(String method) {
        return AuthoringJson.obj("jsonrpc", "2.0", "id", 1, "method", method);
    }
    private static JsonObject initialize() {
        JsonObject message = request("initialize");
        message.add("params", AuthoringJson.obj("protocolVersion", AuthoringMcp.VERSION, "capabilities", new JsonObject(),
                "clientInfo", AuthoringJson.obj("name", "controls-test", "version", "1")));
        return message;
    }
    private static void ready(AuthoringMcp mcp) {
        assertTrue(mcp.handleStdio(initialize()).has("result"));
        assertNull(mcp.handleStdio(AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized")));
    }
    private static JsonObject call(String operation, JsonObject arguments) {
        JsonObject message = request("tools/call");
        message.add("params", AuthoringJson.obj("name", "nekojs_" + operation, "arguments", arguments));
        return message;
    }
    private static JsonArray tools(AuthoringMcp mcp) {
        return mcp.handleStdio(request("tools/list")).getAsJsonObject("result").getAsJsonArray("tools");
    }
    private static JsonObject tool(JsonArray tools, String name) {
        return tools.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(item -> item.get("name").getAsString().equals("nekojs_" + name)).findFirst().orElseThrow();
    }
    private static JsonObject arguments(String operation) {
        return operation.equals("execute_game_command") ? AuthoringJson.obj("command", "say fixture") : AuthoringJson.obj("type", "server");
    }

    @Test void oldConstructorsDefaultToSevenReadOnlyToolsAndAvailabilityIsPerOperation() throws Exception {
        try (var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> new JsonObject())) {
            ready(mcp);
            assertEquals(7, tools(mcp).size());
            for (JsonElement item : tools(mcp)) assertTrue(item.getAsJsonObject().getAsJsonObject("annotations").get("readOnlyHint").getAsBoolean());
        }
        AtomicBoolean reload = new AtomicBoolean();
        try (var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> new JsonObject(),
                operation -> operation.equals("execute_game_command") || reload.get())) {
            ready(mcp);
            assertEquals(8, tools(mcp).size());
            assertNotNull(tool(tools(mcp), "execute_game_command"));
            reload.set(true);
            assertEquals(9, tools(mcp).size());
            assertNotNull(tool(tools(mcp), "reload_scripts"));
        }
    }

    @Test void enabledToolsAdvertiseStrictSchemasAndUnsandboxedMutationAnnotations() throws Exception {
        try (var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> new JsonObject(), operation -> true)) {
            ready(mcp);
            JsonArray tools = tools(mcp);
            assertEquals(9, tools.size());
            for (String operation : List.of("execute_game_command", "reload_scripts")) {
                JsonObject tool = tool(tools, operation);
                assertEquals(AuthoringJson.obj("readOnlyHint", false, "destructiveHint", true, "idempotentHint", false, "openWorldHint", true), tool.get("annotations"));
                JsonObject schema = tool.getAsJsonObject("inputSchema");
                assertEquals("object", schema.get("type").getAsString());
                assertFalse(schema.get("additionalProperties").getAsBoolean());
                assertEquals(2, schema.getAsJsonObject("properties").size());
                assertEquals(AuthoringJson.GSON.toJsonTree(List.of(operation.equals("execute_game_command") ? "command" : "type")), schema.get("required"));
                assertEquals(AuthoringJson.obj("type", "integer", "minimum", 1, "maximum", 8000, "default", 5000), schema.getAsJsonObject("properties").get("timeoutMs"));
            }
            JsonObject command = tool(tools, "execute_game_command").getAsJsonObject("inputSchema").getAsJsonObject("properties").getAsJsonObject("command");
            assertEquals(1, command.get("minLength").getAsInt());
            assertEquals(2048, command.get("maxLength").getAsInt());
            JsonObject type = tool(tools, "reload_scripts").getAsJsonObject("inputSchema").getAsJsonObject("properties").getAsJsonObject("type");
            assertEquals(AuthoringJson.GSON.toJsonTree(List.of("server", "client", "test")), type.get("enum"));
        }
    }

    @Test void mcpPreservesSelectedReloadTypeAndDoesNotTreatStartupAsReload() throws Exception {
        var selected = new AtomicReference<com.tkisor.nekojs.api.ScriptType>();
        AuthoringControlHost host = new AuthoringControlHost() {
            @Override public long epoch() { return 1; }
            @Override public long epoch(com.tkisor.nekojs.api.ScriptType type) { return 1; }
            @Override public void enqueue(Runnable operation) { fail("The selected owner must be forwarded."); }
            @Override public void enqueue(com.tkisor.nekojs.api.ScriptType type, Runnable operation) { operation.run(); }
            @Override public CompletionStage<CommandResult> executeCommand(String command, Consumer<String> output) {
                throw new AssertionError("Reload must not execute a command.");
            }
            @Override public CompletionStage<ReloadResult> reloadServer(Consumer<String> output) {
                throw new AssertionError("The selected type must be forwarded.");
            }
            @Override public CompletionStage<ReloadResult> reload(com.tkisor.nekojs.api.ScriptType type, Consumer<String> output) {
                selected.set(type); return CompletableFuture.completedFuture(new ReloadResult(true, true));
            }
        };
        try (var controls = new AuthoringControl(new AuthoringControl.Policy(false, true, java.util.Set.of()), host);
             var mcp = new AuthoringMcp(AuthoringBundle.load(), controls::execute, controls::available)) {
            ready(mcp);
            for (String type : List.of("server", "client", "test")) {
                JsonObject result = mcp.handleStdio(call("reload_scripts", AuthoringJson.obj("type", type))).getAsJsonObject("result");
                assertFalse(result.get("isError").getAsBoolean());
                JsonObject structured = result.getAsJsonObject("structuredContent");
                assertEquals(type, structured.get("type").getAsString());
                assertTrue(structured.get("committed").getAsBoolean());
                assertTrue(structured.get("postprocessingSucceeded").getAsBoolean());
                assertEquals(type, selected.get().name);
            }
            JsonObject rejected = mcp.handleStdio(call("reload_scripts", AuthoringJson.obj("type", "startup"))).getAsJsonObject("result");
            assertTrue(rejected.get("isError").getAsBoolean());
            assertTrue(rejected.toString().contains("RESTART_REQUIRED"));
            assertEquals(com.tkisor.nekojs.api.ScriptType.TEST, selected.get());
        }
    }

    @Test void hiddenForgedControlCallsReachTheDisabledCorePolicyWithoutEnqueue() throws Exception {
        AtomicInteger enqueued = new AtomicInteger();
        AuthoringControlHost host = new AuthoringControlHost() {
            @Override public long epoch() { return 1; }
            @Override public void enqueue(Runnable operation) { enqueued.incrementAndGet(); operation.run(); }
            @Override public CompletionStage<CommandResult> executeCommand(String command, Consumer<String> output) { return CompletableFuture.completedFuture(new CommandResult(1, true)); }
            @Override public CompletionStage<ReloadResult> reloadServer(Consumer<String> output) { return CompletableFuture.completedFuture(new ReloadResult(true, true)); }
        };
        AtomicInteger routed = new AtomicInteger();
        try (var control = new AuthoringControl(AuthoringControl.Policy.disabled(), host);
             var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> {
                 routed.incrementAndGet();
                 return control.execute(operation, arguments);
             }, control::available)) {
            ready(mcp);
            assertEquals(7, tools(mcp).size());
            for (String operation : List.of("execute_game_command", "reload_scripts")) {
                JsonObject result = mcp.handleStdio(call(operation, arguments(operation))).getAsJsonObject("result");
                assertTrue(result.get("isError").getAsBoolean());
                JsonObject error = AuthoringJson.parse(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
                assertEquals("CONTROL_DISABLED", error.get("code").getAsString());
            }
            assertEquals(2, routed.get());
            assertEquals(0, enqueued.get());
            JsonObject unknown = mcp.handleStdio(call("unknown_control", new JsonObject()));
            assertEquals(-32602, unknown.getAsJsonObject("error").get("code").getAsInt());
            assertEquals(2, routed.get());
        }
    }

    @Test void admittedControlOutcomesRetainStructuredContentAndReportEveryNonSuccessAsError() throws Exception {
        AtomicReference<String> status = new AtomicReference<>();
        try (var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) ->
                AuthoringJson.obj("operation", operation, "operationId", "fixture-operation", "status", status.get(), "effects", "unknown"), operation -> true)) {
            ready(mcp);
            for (String operation : List.of("execute_game_command", "reload_scripts")) {
                for (String next : List.of("succeeded", "failed", "timed_out", "interrupted", "closed", "rejected")) {
                    status.set(next);
                    JsonObject result = mcp.handleStdio(call(operation, arguments(operation))).getAsJsonObject("result");
                    assertEquals(!next.equals("succeeded"), result.get("isError").getAsBoolean(), next);
                    JsonObject structured = result.getAsJsonObject("structuredContent");
                    assertEquals(next, structured.get("status").getAsString());
                    assertEquals("unknown", structured.get("effects").getAsString());
                    assertEquals(structured, AuthoringJson.parse(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString()));
                }
            }
        }
    }

    @Test void unexpectedControlFailuresDoNotLeakArgumentsOrClaimNoMutation() throws Exception {
        StringWriter diagnostics = new StringWriter();
        try (var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> {
            throw new IllegalStateException("SECRET_COMMAND_AND_FEEDBACK");
        }, (message, failure) -> {
            diagnostics.append(message);
            failure.printStackTrace(new PrintWriter(diagnostics));
        }, operation -> true)) {
            ready(mcp);
            JsonObject response = mcp.handleStdio(call("execute_game_command", arguments("execute_game_command")));
            assertEquals(-32603, response.getAsJsonObject("error").get("code").getAsInt());
            assertFalse(response.toString().contains("SECRET_COMMAND_AND_FEEDBACK"));
            assertFalse(diagnostics.toString().contains("SECRET_COMMAND_AND_FEEDBACK"));
            assertFalse(diagnostics.toString().contains("no mutation"));
            assertTrue(diagnostics.toString().contains("outcome may be unknown"));
        }
    }

    @Test void offlineControlsFailBeforeDescriptorRefreshQueryValidationOrTokenRelay() throws Exception {
        JsonObject environment = AuthoringJson.obj("minecraft", "fixture", "loader", "test");
        JsonObject descriptor = AuthoringJson.obj("schemaVersion", "1", "instanceId", "offline", "environment", environment,
                "environmentId", "environment:" + AuthoringJson.hash(AuthoringJson.canonical(environment)),
                "apiEnabled", false, "apiSnapshotDirectory", "missing-snapshot");
        Path file = game.resolve("nekojs/ai/environment.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, descriptor.toString());
        Path connection = file.resolveSibling("connection.json");
        String token = "SECRET_TOKEN_MUST_NOT_BE_RELAYED";
        Files.writeString(connection, AuthoringJson.obj("token", token, "port", 1).toString());
        try (var session = new AuthoringSnapshotSession(game, AuthoringBundle.load());
             var mcp = new AuthoringMcp(AuthoringBundle.load(), session::execute)) {
            ready(mcp);
            Files.delete(file);
            assertEquals(7, tools(mcp).size());
            for (String operation : List.of("execute_game_command", "reload_scripts")) {
                AuthoringException failure = assertThrows(AuthoringException.class, () -> session.execute(operation, new JsonObject()));
                assertEquals(409, failure.status());
                assertEquals("LIVE_RUNTIME_REQUIRED", failure.code());
                JsonObject response = mcp.handleStdio(call(operation, new JsonObject()));
                JsonObject result = response.getAsJsonObject("result");
                assertTrue(result.get("isError").getAsBoolean());
                assertTrue(result.toString().contains("LIVE_RUNTIME_REQUIRED"));
                assertFalse(response.toString().contains(token));
            }
        }
        assertFalse(Files.exists(game.resolve("missing-snapshot")));
        assertTrue(Files.readString(connection).contains(token));
    }

    private static HttpResponse<String> send(HttpClient client, int port, String path, JsonObject body, String session) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer fixture-token").header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        if (session != null) builder.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", AuthoringMcp.VERSION);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void httpPredicateReachesMcpButPlainMutationRoutesStayAbsent() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        try (HttpClient client = HttpClient.newHttpClient();
             var server = new AuthoringHttpServer(0, "fixture-token", true, true, AuthoringBundle.load(), (operation, arguments) -> {
                 mutations.incrementAndGet();
                 return AuthoringJson.obj("status", "succeeded", "effects", "applied");
             }, operation -> true)) {
            for (String operation : List.of("execute_game_command", "reload_scripts")) {
                assertEquals(404, send(client, server.port(), "/api/v1/authoring/" + operation, arguments(operation), null).statusCode());
            }
            assertEquals(0, mutations.get());
            HttpResponse<String> init = send(client, server.port(), "/mcp", initialize(), null);
            assertEquals(200, init.statusCode());
            String session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();
            assertEquals(202, send(client, server.port(), "/mcp", AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), session).statusCode());
            JsonObject list = AuthoringJson.parse(send(client, server.port(), "/mcp", request("tools/list"), session).body());
            assertEquals(9, list.getAsJsonObject("result").getAsJsonArray("tools").size());
            JsonObject response = AuthoringJson.parse(send(client, server.port(), "/mcp", call("reload_scripts", arguments("reload_scripts")), session).body());
            assertFalse(response.getAsJsonObject("result").get("isError").getAsBoolean());
            assertEquals(1, mutations.get());
        }
    }

    @Test void batchEntriesRemainQueriesAndNeverDispatchControls() throws Exception {
        AtomicInteger mutations = new AtomicInteger();
        try (var query = AuthoringQueryTest.engine();
             var mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> {
                 if (operation.equals("execute_game_command") || operation.equals("reload_scripts")) mutations.incrementAndGet();
                 return query.execute(operation, arguments);
             }, operation -> true)) {
            ready(mcp);
            JsonObject batch = AuthoringJson.obj("scope", AuthoringQueryTest.scope(), "queries", List.of(
                    AuthoringJson.obj("operation", "execute_game_command", "command", "say fixture"),
                    AuthoringJson.obj("operation", "reload_scripts", "type", "server")));
            JsonObject result = mcp.handleStdio(call("batch", batch)).getAsJsonObject("result").getAsJsonObject("structuredContent");
            assertEquals(2, result.getAsJsonArray("results").size());
            for (JsonElement entry : result.getAsJsonArray("results")) assertEquals("error", entry.getAsJsonObject().get("status").getAsString());
            assertEquals(0, mutations.get());
        }
    }
}
