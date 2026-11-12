package com.tkisor.nekojs.authoring;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.probe.ProbeConfig;
import com.tkisor.nekojs.testfixture.TestPlatformInit;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringHttpServerTest {
    private static final String TOKEN = "test-token-for-isolated-loopback-server";
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private static HttpResponse<String> send(int port, String path, String method, JsonObject body, boolean auth, String session) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream");
        if (auth) builder.header("Authorization", "Bearer " + TOKEN);
        if (session != null) builder.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", AuthoringMcp.VERSION);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void httpContextExposesLatestTestReportWithoutAnOperationIdAndNeverRetainsCallerMutations(@TempDir Path directory) throws Exception {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        paths.initFolders();
        Files.writeString(paths.config().resolve("ai.toml"), "[documentation]\nenabled = false\n");
        var latest = new java.util.concurrent.atomic.AtomicReference<com.tkisor.nekojs.api.testing.TestRunReport>();
        try (AuthoringRuntime runtime = AuthoringRuntime.open(paths, ProbeConfig.defaultConfig());
             AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(), runtime::execute)) {
            runtime.installDiagnostics(List::of, latest::get);
            String endpoint = "/api/v1/authoring/context";
            JsonObject before = AuthoringJson.parse(send(server.port(), endpoint, "GET", null, true, null).body());
            assertTrue(before.get("testRunReport").isJsonNull());
            latest.set(new com.tkisor.nekojs.api.testing.TestRunReport(true, true, 0, 1));
            JsonObject report = AuthoringJson.parse(send(server.port(), endpoint, "GET", null, true, null).body()).getAsJsonObject("testRunReport");
            assertFalse(report.get("assertionsPassed").getAsBoolean());
            assertFalse(report.get("overallPassed").getAsBoolean());
            report.addProperty("failed", 0);
            assertEquals(1, AuthoringJson.parse(send(server.port(), endpoint, "GET", null, true, null).body())
                    .getAsJsonObject("testRunReport").get("failed").getAsInt());
        }
    }

    @Test void authenticatedErrorsQueryReportsMissingDiagnosticsInsteadOfInventingAnEmptyLiveSnapshot(@TempDir Path directory) throws Exception {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory);
        paths.initFolders();
        Files.writeString(paths.config().resolve("ai.toml"), "[documentation]\nenabled = false\n");
        try (AuthoringRuntime runtime = AuthoringRuntime.open(paths, ProbeConfig.defaultConfig());
             AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(), runtime::execute)) {
            String endpoint = "/api/v1/authoring/errors";
            JsonObject request = AuthoringJson.obj("scriptType", "SERVER", "limit", 5);
            assertEquals(401, send(server.port(), endpoint, "POST", request, false, null).statusCode());
            HttpResponse<String> response = send(server.port(), endpoint, "POST", request, true, null);
            assertEquals(200, response.statusCode(), response.body());
            JsonObject result = AuthoringJson.parse(response.body());
            assertFalse(result.get("available").getAsBoolean());
            assertEquals("DIAGNOSTICS_UNAVAILABLE", result.get("reason").getAsString());
            assertFalse(response.body().contains(TOKEN));
        }
    }

    @Test void authenticatedOperationLookupObservesLateCompletionWithoutRedispatch() throws Exception {
        AtomicLong clock = new AtomicLong();
        AtomicLong calls = new AtomicLong();
        CompletableFuture<com.tkisor.nekojs.api.authoring.AuthoringControlHost.ReloadResult> completion = new CompletableFuture<>();
        var host = new com.tkisor.nekojs.api.authoring.AuthoringControlHost() {
            @Override public long epoch() { return 7; }
            @Override public void enqueue(Runnable operation) { operation.run(); }
            @Override public CompletionStage<CommandResult> executeCommand(String command, java.util.function.Consumer<String> output) {
                throw new AssertionError("A status query must never execute a command");
            }
            @Override public CompletionStage<ReloadResult> reloadServer(java.util.function.Consumer<String> output) {
                calls.incrementAndGet();
                clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(AuthoringControl.MAX_TIMEOUT_MS + 1L));
                return completion;
            }
        };
        try (AuthoringControl controls = new AuthoringControl(new AuthoringControl.Policy(false, true, java.util.Set.of()), host, clock::get);
             AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(), controls::execute)) {
            JsonObject first = controls.execute("reload_scripts", AuthoringJson.obj("type", "server"));
            assertEquals("timed_out", first.get("status").getAsString());
            assertEquals(1, calls.get());
            JsonObject lookup = AuthoringJson.obj("operationId", first.get("operationId").getAsString());
            String endpoint = "/api/v1/authoring/operation_status";
            assertEquals(401, send(server.port(), endpoint, "POST", lookup, false, null).statusCode());
            HttpResponse<String> pendingResponse = send(server.port(), endpoint, "POST", lookup, true, null);
            assertEquals(200, pendingResponse.statusCode(), pendingResponse.body());
            JsonObject pending = AuthoringJson.parse(pendingResponse.body());
            assertFalse(pending.get("terminal").getAsBoolean());
            assertFalse(pending.get("retryable").getAsBoolean());
            assertEquals("timed_out_pending", pending.get("state").getAsString());
            completion.complete(new com.tkisor.nekojs.api.authoring.AuthoringControlHost.ReloadResult(true, true));
            HttpResponse<String> finalResponse = send(server.port(), endpoint, "POST", lookup, true, null);
            assertEquals(200, finalResponse.statusCode(), finalResponse.body());
            JsonObject finished = AuthoringJson.parse(finalResponse.body());
            assertTrue(finished.get("terminal").getAsBoolean());
            assertEquals("succeeded", finished.getAsJsonObject("latestResult").get("status").getAsString());
            assertEquals("timed_out", first.get("status").getAsString(), "A later query must not rewrite the original response");
            assertEquals(1, calls.get(), "Read-only lookup must never enqueue or retry the mutation");
        }
    }

    @Test void authenticatedHttpAndMcpShareQueriesAndShutdownReleasesThePort() throws Exception {
        int port;
        try (AuthoringQuery query = AuthoringQueryTest.engine(AuthoringQueryTest.member("a", "of", "ItemStack", "string"));
             AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(), query::execute)) {
            port = server.port();
            assertEquals(401, send(port, "/api/v1/authoring/context", "GET", null, false, null).statusCode());
            assertEquals(200, send(port, "/api/v1/authoring/context", "GET", null, true, null).statusCode());
            assertEquals(405, send(port, "/api/v1/authoring/query", "GET", null, true, null).statusCode());
            JsonObject request = AuthoringQueryTest.request("members", AuthoringQueryTest.comparison("name", "eq", "of"));
            JsonObject http = AuthoringJson.parse(send(port, "/api/v1/authoring/query", "POST", request, true, null).body());
            var initialized = send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 1, "method", "initialize",
                    "params", AuthoringJson.obj("protocolVersion", AuthoringMcp.VERSION, "capabilities", new JsonObject(), "clientInfo", AuthoringJson.obj("name", "test", "version", "1"))), true, null);
            assertEquals(200, initialized.statusCode());
            String session = initialized.headers().firstValue("Mcp-Session-Id").orElseThrow();
            assertEquals(202, send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), true, session).statusCode());
            var tools = AuthoringJson.parse(send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 2, "method", "tools/list"), true, session).body());
            assertEquals(7, tools.getAsJsonObject("result").getAsJsonArray("tools").size());
            var rpc = AuthoringJson.parse(send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 3, "method", "tools/call", "params",
                    AuthoringJson.obj("name", "nekojs_query", "arguments", request)), true, session).body());
            JsonObject tool = rpc.getAsJsonObject("result");
            assertFalse(tool.get("isError").getAsBoolean());
            assertEquals(http.get("items"), tool.getAsJsonObject("structuredContent").get("items"));
            assertEquals(http.get("coverage"), tool.getAsJsonObject("structuredContent").get("coverage"));
            var resourceList = AuthoringJson.parse(send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 4, "method", "resources/list"), true, session).body());
            String uri = resourceList.getAsJsonObject("result").getAsJsonArray("resources").get(0).getAsJsonObject().get("uri").getAsString();
            var resource = AuthoringJson.parse(send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 5, "method", "resources/read", "params", AuthoringJson.obj("uri", uri)), true, session).body());
            assertFalse(resource.getAsJsonObject("result").getAsJsonArray("contents").isEmpty());
            assertEquals(204, send(port, "/mcp", "DELETE", null, true, session).statusCode());
            assertEquals(404, send(port, "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 6, "method", "ping"), true, session).statusCode());
        }
        try (var rebound = new java.net.ServerSocket()) { rebound.bind(new InetSocketAddress("127.0.0.1", port)); }
    }

    @Test void hostOriginSizeAndProtocolBoundariesAreEnforced() throws Exception {
        try (AuthoringQuery query = AuthoringQueryTest.engine(); AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(), query::execute)) {
            String base = "http://127.0.0.1:" + server.port();
            var badOrigin = HttpRequest.newBuilder(URI.create(base + "/api/v1/authoring/context"))
                    .header("Authorization", "Bearer " + TOKEN).header("Origin", "https://attacker.invalid").GET().build();
            assertEquals(403, CLIENT.send(badOrigin, HttpResponse.BodyHandlers.ofString()).statusCode());
            try (var socket = new java.net.Socket("127.0.0.1", server.port())) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(("GET /api/v1/authoring/context HTTP/1.1\r\nHost: attacker.invalid\r\nAuthorization: Bearer " + TOKEN + "\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                assertTrue(new String(socket.getInputStream().readNBytes(12), java.nio.charset.StandardCharsets.US_ASCII).contains("403"));
            }
            JsonObject huge = AuthoringJson.obj("padding", "x".repeat(70_000));
            assertEquals(413, send(server.port(), "/api/v1/authoring/query", "POST", huge, true, null).statusCode());
            assertEquals(405, send(server.port(), "/mcp", "GET", null, true, null).statusCode());
            assertEquals(400, send(server.port(), "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 1, "method", "ping"), true, null).statusCode());
            assertEquals(400, send(server.port(), "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 2), true, null).statusCode());
        }
    }
    private static JsonObject initialize() {
        return AuthoringJson.obj("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", AuthoringJson.obj(
                "protocolVersion", AuthoringMcp.VERSION, "capabilities", new JsonObject(), "clientInfo", AuthoringJson.obj("name", "test", "version", "1")));
    }

    private static HttpResponse<String> rawPost(int port, String path, byte[] body, String contentType, String accept) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + TOKEN).header("Content-Type", contentType).header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test void mcpDistinguishesWireErrorsAndRejectsInvalidAcceptAndUtf8() throws Exception {
        String accept = "application/json, text/event-stream";
        try (AuthoringQuery query = AuthoringQueryTest.engine();
             AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(), query::execute)) {
            byte[] init = initialize().toString().getBytes(StandardCharsets.UTF_8);
            for (String invalid : List.of("application/json, text/event-stream;q=0", "application/json-bogus, text/event-stream", "application/json;q=NaN, text/event-stream")) {
                assertEquals(406, rawPost(server.port(), "/mcp", init, "application/json", invalid).statusCode(), invalid);
            }
            assertEquals(200, rawPost(server.port(), "/mcp", init, "application/json", "application/json;q=0.5, text/event-stream;q=1").statusCode());
            assertEquals(415, rawPost(server.port(), "/mcp", init, "text/plain", accept).statusCode());
            assertEquals(413, rawPost(server.port(), "/mcp", new byte[70_000], "application/json", accept).statusCode());
            for (String nonObject : List.of("[]", "null", "42")) {
                var response = rawPost(server.port(), "/mcp", nonObject.getBytes(StandardCharsets.UTF_8), "application/json", accept);
                assertEquals(400, response.statusCode());
                assertEquals(-32600, AuthoringJson.parse(response.body()).getAsJsonObject("error").get("code").getAsInt());
            }
            var malformed = rawPost(server.port(), "/mcp", "{".getBytes(StandardCharsets.UTF_8), "application/json", accept);
            assertEquals(-32700, AuthoringJson.parse(malformed.body()).getAsJsonObject("error").get("code").getAsInt());
            byte[] invalidUtf8 = {'{', '"', 'x', '"', ':', '"', (byte) 0xc3, '"', '}'};
            assertEquals(400, rawPost(server.port(), "/api/v1/authoring/query", invalidUtf8, "application/json", accept).statusCode());
            var invalidId = send(server.port(), "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", new JsonObject(), "method", "ping"), true, null);
            JsonObject error = AuthoringJson.parse(invalidId.body());
            assertEquals(-32600, error.getAsJsonObject("error").get("code").getAsInt());
            assertTrue(error.get("id").isJsonNull());
            var missingMethod = send(server.port(), "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 7), true, null);
            assertEquals(7, AuthoringJson.parse(missingMethod.body()).get("id").getAsInt());
        }
    }

    @Test void mcpSessionsAreBoundedExpireAtTheBoundaryAndCannotReopenAfterClose() throws Exception {
        AtomicLong now = new AtomicLong();
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        AuthoringMcp mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, request) -> new JsonObject(), clock);
        try {
            String first = mcp.handle(initialize(), null, null).session();
            JsonObject list = AuthoringJson.obj("jsonrpc", "2.0", "id", 2, "method", "tools/list");
            assertEquals(-32000, mcp.handle(list, first, AuthoringMcp.VERSION).body().getAsJsonObject("error").get("code").getAsInt());
            assertEquals(400, mcp.handle(list, first, "unsupported").status());
            assertEquals(202, mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), first, AuthoringMcp.VERSION).status());
            assertEquals(7, mcp.handle(list, first, AuthoringMcp.VERSION).body().getAsJsonObject("result").getAsJsonArray("tools").size());
            var notification = mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "method", "unknown", "params", List.of()), first, AuthoringMcp.VERSION);
            assertEquals(202, notification.status()); assertNull(notification.body());
            assertNull(mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "method", "initialize"), null, null).session());
            list.add("params", AuthoringJson.obj("cursor", "ignored-before"));
            assertEquals(-32602, mcp.handle(list, first, AuthoringMcp.VERSION).body().getAsJsonObject("error").get("code").getAsInt());
            for (int i = 1; i < 32; i++) assertNotNull(mcp.handle(initialize(), null, null).session());
            assertEquals(429, mcp.handle(initialize(), null, null).status());
            now.set(1_800_000);
            assertEquals(404, mcp.handle(list, first, AuthoringMcp.VERSION).status());
            assertFalse(mcp.delete(first));
            String fresh = mcp.handle(initialize(), null, null).session();
            assertNotNull(fresh);
            now.addAndGet(1_800_000);
            assertFalse(mcp.delete(fresh));
        } finally { mcp.close(); }
        assertEquals(503, mcp.handle(initialize(), null, null).status());
    }

    @Test void mcpResourceMimeTypesMatchAndUnexpectedCallbacksRemainJsonRpcErrors() throws Exception {
        try (AuthoringMcp mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, request) -> { throw new IllegalStateException("test callback failure"); })) {
            String session = mcp.handle(initialize(), null, null).session();
            mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), session, AuthoringMcp.VERSION);
            var resources = mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "id", 2, "method", "resources/list"), session, AuthoringMcp.VERSION)
                    .body().getAsJsonObject("result").getAsJsonArray("resources");
            assertFalse(resources.isEmpty());
            for (JsonElement value : resources) {
                JsonObject resource = value.getAsJsonObject();
                var read = mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "id", 3, "method", "resources/read", "params", AuthoringJson.obj("uri", resource.get("uri"))), session, AuthoringMcp.VERSION);
                assertEquals(resource.get("mimeType"), read.body().getAsJsonObject("result").getAsJsonArray("contents").get(0).getAsJsonObject().get("mimeType"));
            }
            var failure = mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "id", 4, "method", "tools/call", "params", AuthoringJson.obj("name", "nekojs_context")), session, AuthoringMcp.VERSION);
            assertEquals(200, failure.status());
            assertEquals(-32603, failure.body().getAsJsonObject("error").get("code").getAsInt());
        }
    }

    @Test void closingMcpDoesNotWaitForRootCallbacksHoldingOtherLocks() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "authoring-close-test"); thread.setDaemon(true); return thread;
        });
        try (AuthoringMcp mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, request) -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Callback release timed out"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            return new JsonObject();
        })) {
            String session = mcp.handle(initialize(), null, null).session();
            mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), session, AuthoringMcp.VERSION);
            Future<AuthoringMcp.Reply> call = executor.submit(() -> mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "id", 2, "method", "tools/call", "params", AuthoringJson.obj("name", "nekojs_context")), session, AuthoringMcp.VERSION));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                executor.submit(mcp::close).get(2, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            assertEquals(200, call.get(2, TimeUnit.SECONDS).status());
            assertEquals(503, mcp.handle(initialize(), null, null).status());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void responseBudgetPreservesMcpToolErrorEnvelope() throws Exception {
        try (AuthoringHttpServer server = new AuthoringHttpServer(0, TOKEN, true, true, AuthoringBundle.load(),
                (operation, request) -> AuthoringJson.obj("padding", "x".repeat(70_000)))) {
            var http = send(server.port(), "/api/v1/authoring/context", "GET", null, true, null);
            assertEquals(422, http.statusCode());
            assertEquals("RESPONSE_TOO_LARGE", AuthoringJson.parse(http.body()).get("code").getAsString());
            String session = send(server.port(), "/mcp", "POST", initialize(), true, null).headers().firstValue("Mcp-Session-Id").orElseThrow();
            send(server.port(), "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), true, session);
            var response = send(server.port(), "/mcp", "POST", AuthoringJson.obj("jsonrpc", "2.0", "id", 2, "method", "tools/call", "params", AuthoringJson.obj("name", "nekojs_context")), true, session);
            assertEquals(200, response.statusCode());
            JsonObject result = AuthoringJson.parse(response.body()).getAsJsonObject("result");
            assertTrue(result.get("isError").getAsBoolean());
            assertTrue(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString().contains("RESPONSE_TOO_LARGE"));
            assertTrue(response.body().getBytes(StandardCharsets.UTF_8).length <= 65_536);
        }
    }

    @Test void runtimeForwardsTargetOwnersAndDiscoversClientWithoutServer(@TempDir Path directory) throws Exception {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory); paths.initFolders();
        Files.writeString(paths.config().resolve("ai.toml"), "[documentation]\nenabled = false\n[control]\nreload = true\n");
        AtomicLong serverEpoch = new AtomicLong(-1);
        var queued = new java.util.concurrent.atomic.AtomicReference<com.tkisor.nekojs.api.ScriptType>();
        var reloaded = new java.util.concurrent.atomic.AtomicReference<com.tkisor.nekojs.api.ScriptType>();
        try (var runtime = AuthoringRuntime.open(paths, ProbeConfig.defaultConfig())) {
            assertFalse(Files.exists(paths.config().resolve("ai-location.json")));
            assertTrue(runtime.execute("capabilities", new JsonObject()).getAsJsonObject("liveControls").getAsJsonArray("reloadTypes").isEmpty());
            runtime.installHost(new com.tkisor.nekojs.api.authoring.AuthoringControlHost() {
                @Override public long epoch() { return serverEpoch.get(); }
                @Override public long epoch(com.tkisor.nekojs.api.ScriptType type) {
                    return type == com.tkisor.nekojs.api.ScriptType.CLIENT ? 42 : serverEpoch.get();
                }
                @Override public void enqueue(Runnable operation) { fail("The target owner must be forwarded."); }
                @Override public void enqueue(com.tkisor.nekojs.api.ScriptType type, Runnable operation) {
                    queued.set(type); operation.run();
                }
                @Override public CompletionStage<CommandResult> executeCommand(String command, java.util.function.Consumer<String> output) {
                    throw new AssertionError("Reload must not execute a command.");
                }
                @Override public CompletionStage<ReloadResult> reloadServer(java.util.function.Consumer<String> output) {
                    throw new AssertionError("The selected type must be forwarded.");
                }
                @Override public CompletionStage<ReloadResult> reload(com.tkisor.nekojs.api.ScriptType type, java.util.function.Consumer<String> output) {
                    reloaded.set(type); return CompletableFuture.completedFuture(new ReloadResult(true, true));
                }
            });
            JsonObject controls = runtime.execute("context", new JsonObject()).getAsJsonObject("liveControls");
            assertEquals(AuthoringJson.GSON.toJsonTree(List.of("client")), controls.get("reloadTypes"));
            assertFalse(controls.get("executeGameCommandAvailable").getAsBoolean());
            JsonObject client = runtime.execute("reload_scripts", AuthoringJson.obj("type", "client"));
            assertEquals("succeeded", client.get("status").getAsString());
            assertEquals("client", client.get("type").getAsString());
            assertEquals(com.tkisor.nekojs.api.ScriptType.CLIENT, queued.get());
            assertEquals(com.tkisor.nekojs.api.ScriptType.CLIENT, reloaded.get());
            assertEquals("LIVE_RUNTIME_REQUIRED", assertThrows(AuthoringException.class,
                    () -> runtime.execute("reload_scripts", AuthoringJson.obj("type", "test"))).code());
            serverEpoch.set(7);
            assertEquals(AuthoringJson.GSON.toJsonTree(List.of("server", "client", "test")),
                    runtime.execute("capabilities", new JsonObject()).getAsJsonObject("liveControls").get("reloadTypes"));
            JsonObject test = runtime.execute("reload_scripts", AuthoringJson.obj("type", "test"));
            assertEquals("succeeded", test.get("status").getAsString());
            assertEquals(com.tkisor.nekojs.api.ScriptType.TEST, queued.get());
            assertEquals(com.tkisor.nekojs.api.ScriptType.TEST, reloaded.get());
            runtime.stopControls();
            assertTrue(runtime.execute("context", new JsonObject()).getAsJsonObject("liveControls").getAsJsonArray("reloadTypes").isEmpty());
        }
    }

    @Test void runtimeRejectsMissingCurrentSnapshotAndPreservesNewerRootDescriptor(@TempDir Path directory) throws Exception {
        TestPlatformInit.ensureInitialized();
        NekoJSPaths paths = NekoJSPaths.fromGameDir(directory); paths.initFolders();
        Files.writeString(paths.config().resolve("ai.toml"), "[documentation]\nenabled = false\n");
        ProbeConfig probe = ProbeConfig.defaultConfig();
        AuthoringRuntime first = AuthoringRuntime.open(paths, probe);
        try {
            JsonObject state = first.execute("context", new JsonObject());
            JsonObject fixture = AuthoringQueryTest.fixture("s1", List.of());
            fixture.addProperty("environmentId", state.get("environmentId").getAsString());
            Path snapshots = AuthoringPaths.resolve(paths.gameDir(), AuthoringPaths.DEFAULT_DIRECTORY).snapshots();
            AuthoringSnapshotFiles.publish(snapshots, fixture);
            assertEquals("s1", first.execute("context", new JsonObject()).get("snapshotId").getAsString());
            Files.delete(snapshots.resolve("current.json"));
            assertEquals("unavailable", first.execute("context", new JsonObject()).get("indexState").getAsString());
            assertEquals("INDEX_UNAVAILABLE", assertThrows(AuthoringException.class, () -> first.execute("query", new JsonObject())).code());
            assertThrows(AuthoringException.class, () -> first.execute("context", AuthoringJson.obj("unexpected", true)));
            try (AuthoringRuntime second = AuthoringRuntime.open(paths, probe)) {
                Path descriptor = AuthoringPaths.resolve(paths.gameDir(), AuthoringPaths.DEFAULT_DIRECTORY).connection();
                String saved = Files.readString(descriptor);
                first.close();
                assertEquals(saved, Files.readString(descriptor));
                assertThrows(AuthoringException.class, () -> first.execute("context", new JsonObject()));
            }
        } finally { first.close(); }
        assertFalse(AuthoringJson.parse(Files.readString(AuthoringPaths.resolve(paths.gameDir(), AuthoringPaths.DEFAULT_DIRECTORY).connection())).get("listening").getAsBoolean());
    }
}
