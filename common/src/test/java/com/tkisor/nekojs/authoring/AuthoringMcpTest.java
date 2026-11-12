package com.tkisor.nekojs.authoring;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthoringMcpTest {
    private static JsonObject message(String method) {
        return AuthoringJson.obj("jsonrpc", "2.0", "id", 1, "method", method);
    }

    private static String initialize(AuthoringMcp mcp) {
        JsonObject request = message("initialize");
        request.add("params", AuthoringJson.obj("protocolVersion", AuthoringMcp.VERSION, "capabilities", new JsonObject(),
                "clientInfo", AuthoringJson.obj("name", "live-query-test", "version", "1")));
        String session = mcp.handle(request, null, null).session();
        mcp.handle(AuthoringJson.obj("jsonrpc", "2.0", "method", "notifications/initialized"), session, AuthoringMcp.VERSION);
        return session;
    }

    @Test void liveQueriesAreReadOnlyAndIndependentOfMutationAvailability() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (AuthoringMcp mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) -> {
            calls.incrementAndGet();
            assertTrue(List.of("errors", "operation_status").contains(operation));
            return AuthoringJson.obj("available", true, "fixture", operation);
        }, Clock.systemUTC(), (message, failure) -> fail("Protocol handling must not fail"), operation -> false, operation -> true)) {
            String session = initialize(mcp);
            var tools = mcp.handle(message("tools/list"), session, AuthoringMcp.VERSION).body().getAsJsonObject("result").getAsJsonArray("tools");
            assertEquals(9, tools.size());
            for (String operation : List.of("errors", "operation_status")) {
                JsonObject tool = tools.asList().stream().map(value -> value.getAsJsonObject())
                        .filter(value -> value.get("name").getAsString().equals("nekojs_" + operation)).findFirst().orElseThrow();
                assertEquals(AuthoringJson.obj("readOnlyHint", true, "destructiveHint", false, "idempotentHint", true, "openWorldHint", false), tool.get("annotations"));
                assertFalse(tool.getAsJsonObject("inputSchema").get("additionalProperties").getAsBoolean());
                JsonObject request = message("tools/call");
                request.add("params", AuthoringJson.obj("name", "nekojs_" + operation, "arguments", new JsonObject()));
                JsonObject result = mcp.handle(request, session, AuthoringMcp.VERSION).body().getAsJsonObject("result");
                assertFalse(result.get("isError").getAsBoolean());
                assertEquals(operation, result.getAsJsonObject("structuredContent").get("fixture").getAsString());
            }
            assertEquals(2, calls.get());
        }
    }

    @Test void unavailableLiveQueryIsAnExplicitToolErrorRatherThanAControlOrFabricatedSnapshot() throws Exception {
        try (AuthoringMcp mcp = new AuthoringMcp(AuthoringBundle.load(), (operation, arguments) ->
                AuthoringJson.obj("available", false, "reason", "DIAGNOSTICS_UNAVAILABLE"), operation -> false)) {
            String session = initialize(mcp);
            assertEquals(7, mcp.handle(message("tools/list"), session, AuthoringMcp.VERSION).body()
                    .getAsJsonObject("result").getAsJsonArray("tools").size());
            JsonObject request = message("tools/call");
            request.add("params", AuthoringJson.obj("name", "nekojs_errors", "arguments", new JsonObject()));
            JsonObject result = mcp.handle(request, session, AuthoringMcp.VERSION).body().getAsJsonObject("result");
            assertTrue(result.get("isError").getAsBoolean());
            assertEquals("DIAGNOSTICS_UNAVAILABLE", result.getAsJsonObject("structuredContent").get("reason").getAsString());
        }
    }
}
