package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringStdioTest {
    private record Transcript(List<JsonObject> messages, String diagnostics, byte[] output) {}

    private static JsonObject request(Object id, String method) {
        return AuthoringJson.obj("jsonrpc", "2.0", "id", id, "method", method);
    }
    private static JsonObject initialize() {
        JsonObject request = request("init", "initialize");
        request.add("params", AuthoringJson.obj("protocolVersion", AuthoringMcp.VERSION, "capabilities", new JsonObject(),
                "clientInfo", AuthoringJson.obj("name", "stdio-test", "version", "1")));
        return request;
    }
    private static JsonObject initialized() { return AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"); }
    private static JsonObject call(Object id, String operation, JsonObject arguments) {
        JsonObject request = request(id, "tools/call");
        request.add("params", AuthoringJson.obj("name", "nekojs_" + operation, "arguments", arguments));
        return request;
    }
    private static String lines(JsonObject... messages) {
        StringBuilder text = new StringBuilder();
        for (JsonObject message : messages) text.append(message).append('\n');
        return text.toString();
    }
    private static Transcript run(String text, BiFunction<String, JsonObject, JsonObject> query) throws Exception {
        return run(text.getBytes(StandardCharsets.UTF_8), query);
    }
    private static Transcript run(byte[] input, BiFunction<String, JsonObject, JsonObject> query) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(), diagnostics = new ByteArrayOutputStream();
        new AuthoringStdio(AuthoringBundle.load(), query).run(new ByteArrayInputStream(input), output, new PrintStream(diagnostics, true, StandardCharsets.UTF_8));
        String text = output.toString(StandardCharsets.UTF_8);
        if (!text.isEmpty()) assertTrue(text.endsWith("\n"));
        return new Transcript(text.lines().map(AuthoringJson::parse).toList(), diagnostics.toString(StandardCharsets.UTF_8), output.toByteArray());
    }
    private static int code(JsonObject message) { return message.getAsJsonObject("error").get("code").getAsInt(); }

    @Test void initializedConnectionSharesQueriesAndAdvertisesTheSameToolsAndResources() throws Exception {
        try (AuthoringQuery query = AuthoringQueryTest.engine(AuthoringQueryTest.member("a", "of", "ItemStack", "string"))) {
            JsonObject arguments = AuthoringQueryTest.request("members", AuthoringQueryTest.comparison("name", "eq", "of"));
            JsonObject expected = query.execute("query", arguments);
            Transcript result = run(lines(initialize(), initialized(), request(2, "tools/list"), request(3, "resources/list"), call(4, "query", arguments)), query::execute);
            assertEquals(4, result.messages().size());
            assertEquals(AuthoringMcp.VERSION, result.messages().get(0).getAsJsonObject("result").get("protocolVersion").getAsString());
            assertEquals(7, result.messages().get(1).getAsJsonObject("result").getAsJsonArray("tools").size());
            assertFalse(result.messages().get(2).getAsJsonObject("result").getAsJsonArray("resources").isEmpty());
            JsonObject tool = result.messages().get(3).getAsJsonObject("result");
            JsonObject actual = tool.getAsJsonObject("structuredContent").deepCopy();
            assertDoesNotThrow(() -> java.util.UUID.fromString(actual.get("requestId").getAsString()));
            expected.remove("requestId");
            actual.remove("requestId");
            assertEquals(expected, actual);
            assertFalse(tool.get("isError").getAsBoolean());
            assertEquals("", result.diagnostics());
            assertFalse(new String(result.output(), StandardCharsets.UTF_8).contains("Mcp-Session-Id"));
        }
    }

    @Test void stdioRequiresItsOwnHandshakeAndNotificationsNeverProduceReplies() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Transcript result = run(lines(initialized(), request(1, "ping"), request(2, "tools/list"), initialize(), request(3, "tools/list"),
                initialized(), AuthoringJson.obj("jsonrpc", "2.0", "method", "unknown", "params", List.of()),
                AuthoringJson.obj("jsonrpc", "2.0", "method", "tools/call", "params", AuthoringJson.obj("name", "nekojs_context")),
                request(4, "tools/list"), initialize()), (operation, request) -> { calls.incrementAndGet(); return new JsonObject(); });
        assertEquals(6, result.messages().size());
        assertTrue(result.messages().get(0).has("result"));
        assertEquals(-32000, code(result.messages().get(1)));
        assertEquals(-32000, code(result.messages().get(3)));
        assertTrue(result.messages().get(4).has("result"));
        assertEquals(-32600, code(result.messages().get(5)));
        assertEquals(0, calls.get());
    }

    @Test void malformedFramesRecoverAndInvalidIdsNeverLeakIntoResponses() throws Exception {
        String input = "{\n[]\nnull\n{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"ping\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}\n" + lines(initialize(), initialized(), request("method-id", "unknown"));
        Transcript result = run(input, (operation, request) -> new JsonObject());
        assertEquals(7, result.messages().size());
        assertEquals(-32700, code(result.messages().get(0)));
        for (int i = 1; i < 5; i++) {
            assertEquals(-32600, code(result.messages().get(i)));
            assertTrue(result.messages().get(i).get("id").isJsonNull());
        }
        assertEquals("method-id", result.messages().get(6).get("id").getAsString());
        assertEquals(-32601, code(result.messages().get(6)));
        byte[] invalidUtf8 = {(byte) 0xc3, '\n'};
        assertEquals(-32700, code(run(invalidUtf8, (operation, request) -> new JsonObject()).messages().get(0)));
    }

    @Test void expectedQueryFailuresStayToolErrorsAndUnexpectedFailuresOnlyDiagnoseOnStderr() throws Exception {
        Transcript known = run(lines(initialize(), initialized(), call("known", "context", new JsonObject())), (operation, request) -> {
            throw new AuthoringException(503, "INDEX_UNAVAILABLE", "/", "Snapshot unavailable.");
        });
        assertTrue(known.messages().get(1).getAsJsonObject("result").get("isError").getAsBoolean());
        assertEquals("", known.diagnostics());
        Transcript internal = run(lines(initialize(), initialized(), call("broken", "context", new JsonObject())), (operation, request) -> {
            throw new IllegalStateException("not protocol output");
        });
        assertEquals("broken", internal.messages().get(1).get("id").getAsString());
        assertEquals(-32603, code(internal.messages().get(1)));
        assertTrue(internal.diagnostics().contains("[NEKO-1019]"));
        assertFalse(new String(internal.output(), StandardCharsets.UTF_8).contains("IllegalStateException"));
    }

    @Test void oversizeInputStopsWithoutDrainingAndOutputBudgetKeepsToolEnvelope() throws Exception {
        ByteArrayInputStream source = new ByteArrayInputStream(("x".repeat(1_000_000) + "\n" + lines(initialize())).getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream(), diagnostics = new ByteArrayOutputStream();
        new AuthoringStdio(AuthoringBundle.load(), (operation, request) -> new JsonObject()).run(source, output, new PrintStream(diagnostics));
        assertTrue(source.available() > 0);
        List<JsonObject> messages = output.toString(StandardCharsets.UTF_8).lines().map(AuthoringJson::parse).toList();
        assertEquals(1, messages.size()); assertEquals(-32600, code(messages.get(0)));
        assertTrue(diagnostics.toString(StandardCharsets.UTF_8).contains("64 KiB"));
        Transcript large = run(lines(initialize(), initialized(), call("large", "context", new JsonObject())), (operation, request) -> AuthoringJson.obj("text", "x".repeat(70_000)));
        JsonObject tool = large.messages().get(1).getAsJsonObject("result");
        assertTrue(tool.get("isError").getAsBoolean());
        assertTrue(tool.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString().contains("RESPONSE_TOO_LARGE"));
        for (JsonObject message : large.messages()) assertTrue(message.toString().getBytes(StandardCharsets.UTF_8).length <= 65_536);
    }

    @Test void exactInputLimitAndNearLimitIdsCannotOverflowOutput() throws Exception {
        String invalid = " ".repeat(65_535) + "{";
        Transcript accepted = run(invalid, (operation, request) -> new JsonObject());
        assertEquals(-32700, code(accepted.messages().get(0)));
        assertEquals("", accepted.diagnostics());
        assertEquals(-32600, code(run(invalid + " ", (operation, request) -> new JsonObject()).messages().get(0)));
        int overhead = request("", "unknown").toString().getBytes(StandardCharsets.UTF_8).length;
        Transcript giantId = run(lines(request("x".repeat(65_536 - overhead), "unknown")), (operation, request) -> new JsonObject());
        assertEquals(-32600, code(giantId.messages().get(0)));
        assertTrue(giantId.messages().get(0).get("id").isJsonNull());
        assertTrue(giantId.output().length <= 65_537);
    }

    @Test void eofCrLfAndStreamOwnershipAreClean() throws Exception {
        class Input extends ByteArrayInputStream {
            boolean closed;
            Input(byte[] bytes) { super(bytes); }
            @Override public void close() { closed = true; }
        }
        class Output extends ByteArrayOutputStream {
            boolean closed;
            @Override public void close() { closed = true; }
        }
        AuthoringStdio adapter = new AuthoringStdio(AuthoringBundle.load(), (operation, request) -> new JsonObject());
        Input input = new Input((initialize() + "\r\n" + initialized() + "\r\n" + request("eof", "ping")).getBytes(StandardCharsets.UTF_8));
        Output output = new Output();
        adapter.run(input, output, new PrintStream(new ByteArrayOutputStream()));
        assertEquals(2, output.toString(StandardCharsets.UTF_8).lines().count());
        assertFalse(input.closed); assertFalse(output.closed);
        Output second = new Output();
        adapter.run(new ByteArrayInputStream(lines(initialize()).getBytes(StandardCharsets.UTF_8)), second, new PrintStream(new ByteArrayOutputStream()));
        assertTrue(AuthoringJson.parse(second.toString(StandardCharsets.UTF_8)).has("result"));
        assertTrue(run("", (operation, request) -> new JsonObject()).messages().isEmpty());
        assertEquals(-32700, code(run("{", (operation, request) -> new JsonObject()).messages().get(0)));
    }

    @Test void stdioLifecycleHasNoHttpExpiryAndDoesNotDependOnHttpSessions() throws Exception {
        AtomicLong now = new AtomicLong();
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
        };
        try (AuthoringMcp mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, request) -> new JsonObject(), clock)) {
            assertTrue(mcp.handleStdio(initialize()).has("result"));
            assertNull(mcp.handleStdio(initialized()));
            String session = mcp.handle(initialize(), null, null).session();
            now.set(1_800_000);
            assertEquals(404, mcp.handle(request(2, "ping"), session, AuthoringMcp.VERSION).status());
            assertEquals(7, mcp.handleStdio(request(3, "tools/list")).getAsJsonObject("result").getAsJsonArray("tools").size());
            assertTrue(mcp.handleStdio(request(4, "ping")).has("result"));
        }
    }
}
