package com.tkisor.nekojs.authoring;

import com.google.gson.*;

import java.time.Clock;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Shared MCP dispatch with read-only discovery and optional live controls, scoped to each transport lifecycle. */
final class AuthoringMcp implements AutoCloseable {
    static final String VERSION = "2025-11-25";
    record Reply(int status, JsonObject body, String session) {}
    private record Session(boolean initialized, long expires) {}
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final AuthoringBundle bundle;
    private final BiFunction<String, JsonObject, JsonObject> query;
    private final Clock clock;
    private final BiConsumer<String, Throwable> errors;
    private final Predicate<String> availability;
    private static final List<String> READ_OPERATIONS = List.of("context", "capabilities", "schema", "resolve", "members", "query", "batch");
    private static final List<String> CONTROL_OPERATIONS = List.of("execute_game_command", "reload_scripts");
    private Session stdioSession;
    private boolean closed;

    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query) {
        this(bundle, query, Clock.systemUTC(), (message, error) -> {});
    }
    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query, Clock clock) {
        this(bundle, query, clock, (message, error) -> {});
    }
    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query, BiConsumer<String, Throwable> errors) {
        this(bundle, query, Clock.systemUTC(), errors);
    }
    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query, Predicate<String> availability) {
        this(bundle, query, Clock.systemUTC(), (message, error) -> {}, availability);
    }
    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query, BiConsumer<String, Throwable> errors, Predicate<String> availability) {
        this(bundle, query, Clock.systemUTC(), errors, availability);
    }
    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query, Clock clock, BiConsumer<String, Throwable> errors) {
        this(bundle, query, clock, errors, operation -> false);
    }
    AuthoringMcp(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query, Clock clock, BiConsumer<String, Throwable> errors, Predicate<String> availability) {
        this.bundle = Objects.requireNonNull(bundle);
        this.query = Objects.requireNonNull(query);
        this.clock = Objects.requireNonNull(clock);
        this.errors = Objects.requireNonNull(errors);
        this.availability = Objects.requireNonNull(availability);
    }

    Reply handle(JsonObject request, String sessionId, String protocol) {
        return handle(request, sessionId, protocol, false);
    }
    JsonObject handleStdio(JsonObject request) {
        return handle(request, null, null, true).body();
    }

    private Reply handle(JsonObject request, String sessionId, String protocol, boolean stdio) {
        JsonElement id = request.get("id");
        boolean validId = id == null || id.isJsonPrimitive() && (id.getAsJsonPrimitive().isString() || id.getAsJsonPrimitive().isNumber());
        if (!request.has("jsonrpc") || !request.get("jsonrpc").equals(new JsonPrimitive("2.0")) || !request.has("method")
                || !request.get("method").isJsonPrimitive() || !request.getAsJsonPrimitive("method").isString()
                || !validId) {
            return rpcError(400, validId ? id : null, -32600, "Invalid JSON-RPC request.");
        }
        String method = request.get("method").getAsString();
        if (request.has("params") && !request.get("params").isJsonObject()) {
            return id == null ? new Reply(202, null, null) : rpcError(200, id, -32602, "Expected object parameters.");
        }
        JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();
        // Never hold the session monitor while calling the root-owned query service: shutdown closes both owners.
        synchronized (this) {
            if (closed) return id == null ? new Reply(202, null, null) : rpcError(503, id, -32000, "MCP service is closed.");
            if (!stdio) expireSessions();
            if (method.equals("initialize")) {
                if (id == null) return new Reply(202, null, null);
                if (stdio && stdioSession != null) return rpcError(200, id, -32600, "This stdio connection has already been initialized.");
                if (!params.has("protocolVersion") || !params.get("protocolVersion").isJsonPrimitive()
                        || !params.getAsJsonPrimitive("protocolVersion").isString() || !params.has("capabilities") || !params.get("capabilities").isJsonObject()
                        || !params.has("clientInfo") || !params.get("clientInfo").isJsonObject()) return rpcError(200, id, -32602, "Initialization metadata is missing.");
                try {
                    AuthoringJson.string(params.getAsJsonObject("clientInfo"), "name", "/params/clientInfo");
                    AuthoringJson.string(params.getAsJsonObject("clientInfo"), "version", "/params/clientInfo");
                } catch (AuthoringException e) { return rpcError(200, id, -32602, e.getMessage()); }
                if (!stdio && sessions.size() >= 32) return rpcError(429, id, -32000, "MCP session limit reached.");
                String created = stdio ? null : UUID.randomUUID().toString();
                if (stdio) stdioSession = new Session(false, 0);
                else sessions.put(created, new Session(false, clock.millis() + 1_800_000));
                JsonObject result = AuthoringJson.obj("protocolVersion", VERSION,
                        "capabilities", AuthoringJson.obj("tools", new JsonObject(), "resources", new JsonObject()),
                        "serverInfo", AuthoringJson.obj("name", "nekojs-authoring", "version", bundle.manifest().get("version")),
                        "instructions", "Read context and capabilities first. Queries are read-only; incomplete coverage never proves an API absent. Live controls require explicit operator opt-in.");
                return new Reply(200, AuthoringJson.obj("jsonrpc", "2.0", "id", id, "result", result), created);
            }
            Session session;
            if (stdio) {
                session = stdioSession;
                if (session == null) {
                    if (id == null) return new Reply(202, null, null);
                    if (!method.equals("ping")) return rpcError(200, id, -32000, "Initialize this stdio connection before using tools or resources.");
                }
            } else {
                if (sessionId == null) return new Reply(400, null, null);
                session = sessions.get(sessionId);
                if (session == null) return new Reply(404, null, null);
                if (!VERSION.equals(protocol)) return new Reply(400, null, null);
            }
            if (method.equals("notifications/initialized") && id == null) {
                if (stdio) stdioSession = new Session(true, 0);
                else sessions.put(sessionId, new Session(true, clock.millis() + 1_800_000));
                return new Reply(202, null, null);
            }
            if (id == null) return new Reply(202, null, null);
            if (session != null && !session.initialized() && !method.equals("ping")) return rpcError(200, id, -32000, "Complete initialization before using tools or resources.");
            if (!stdio) sessions.put(sessionId, new Session(session.initialized(), clock.millis() + 1_800_000));
        }
        try {
            JsonObject result = switch (method) {
                case "ping" -> new JsonObject();
                case "tools/list" -> { unpaginated(params); yield tools(); }
                case "tools/call" -> call(params);
                case "resources/list" -> { unpaginated(params); yield resources(); }
                case "resources/templates/list" -> { unpaginated(params); yield AuthoringJson.obj("resourceTemplates", List.of()); }
                case "resources/read" -> read(params);
                default -> null;
            };
            if (result == null) return rpcError(200, id, -32601, "Method is not supported.");
            return new Reply(200, AuthoringJson.obj("jsonrpc", "2.0", "id", id, "result", result), null);
        } catch (AuthoringException e) { return rpcError(200, id, -32602, e.getMessage()); }
        catch (RuntimeException e) {
            // Host exceptions can contain command arguments or feedback. Do not relay their messages or causes.
            errors.accept("[NEKO-1019] Authoring MCP request failed; operation outcome may be unknown",
                    new IllegalStateException("Authoring dispatcher failed with " + e.getClass().getSimpleName() + "."));
            return rpcError(200, id, -32603, "Authoring request failed.");
        }
    }

    private void expireSessions() { sessions.entrySet().removeIf(entry -> entry.getValue().expires() <= clock.millis()); }
    synchronized boolean delete(String session) {
        expireSessions();
        return session != null && sessions.remove(session) != null;
    }
    private static void unpaginated(JsonObject params) {
        if (params.has("cursor")) throw AuthoringJson.invalid("/params/cursor", "This list has no continuation cursor.");
    }
    private JsonObject tools() {
        JsonArray tools = new JsonArray();
        for (String operation : READ_OPERATIONS) {
            tools.add(AuthoringJson.obj("name", "nekojs_" + operation, "description", description(operation),
                    "inputSchema", AuthoringSchema.input(operation),
                    "annotations", AuthoringJson.obj("readOnlyHint", true, "destructiveHint", false, "idempotentHint", true, "openWorldHint", false)));
        }
        for (String operation : CONTROL_OPERATIONS) {
            if (!availability.test(operation)) continue;
            tools.add(AuthoringJson.obj("name", "nekojs_" + operation, "description", description(operation),
                    "inputSchema", controlSchema(operation),
                    "annotations", AuthoringJson.obj("readOnlyHint", false, "destructiveHint", true, "idempotentHint", false, "openWorldHint", true)));
        }
        return AuthoringJson.obj("tools", tools);
    }
    private static String description(String operation) {
        return switch (operation) {
            case "execute_game_command" -> "Execute one explicitly allowlisted command in the live server runtime. Plugin effects are not sandboxed; timeouts may leave effects unknown.";
            case "reload_scripts" -> "Reload a full SERVER, CLIENT or TEST script generation on its owning thread. Read liveControls.reloadTypes for current availability. STARTUP requires a process restart; timeouts do not roll back started effects.";
            case "context" -> "Get this instance's environment, documentation and current API snapshot status.";
            case "capabilities" -> "Discover supported query resources, typed fields, operators, coverage and limits.";
            case "schema" -> "Get structural query request schemas and resource-specific field catalogs.";
            case "resolve" -> "Resolve exact or prefix Binding/class names in an explicit environment and Script Type; inspect all ambiguous candidates.";
            case "members" -> "Filter emitted declared members by name, kind and accepted call arity. Inherited generic views are unsupported.";
            case "query" -> "Query one snapshot with typed all/any/not conditions, correlated parameter some, selection and stable cursors. Inspect coverage before inferring absence.";
            default -> "Run up to ten read-only queries in one shared scope and snapshot, retaining each result's independent error state.";
        };
    }
    private static JsonObject controlSchema(String operation) {
        JsonObject properties = AuthoringJson.obj("timeoutMs", AuthoringJson.obj("type", "integer",
                "minimum", 1, "maximum", 8000, "default", 5000));
        String required;
        if (operation.equals("execute_game_command")) {
            required = "command";
            properties.add("command", AuthoringJson.obj("type", "string", "minLength", 1, "maxLength", 2048,
                    "description", "One command line with an optional leading slash, no control characters or surrounding whitespace, and an exactly allowlisted root."));
        } else {
            required = "type";
            properties.add("type", AuthoringJson.obj("type", "string", "enum", List.of("server", "client", "test")));
        }
        return AuthoringJson.obj("type", "object", "properties", properties, "required", List.of(required), "additionalProperties", false);
    }
    private JsonObject call(JsonObject params) {
        String name = AuthoringJson.string(params, "name", "/params");
        if (!name.startsWith("nekojs_") || !(READ_OPERATIONS.contains(name.substring(7)) || CONTROL_OPERATIONS.contains(name.substring(7)))) {
            throw AuthoringJson.invalid("/params/name", "Unknown MCP tool.");
        }
        JsonObject arguments = params.has("arguments") ? AuthoringJson.object(params, "arguments", "/params") : new JsonObject();
        try {
            String operation = name.substring(7);
            // Advertisement is not authorization: even hidden controls must reach the core admission policy.
            JsonObject result = query.apply(operation, arguments);
            boolean failedControl = CONTROL_OPERATIONS.contains(operation) && !new JsonPrimitive("succeeded").equals(result.get("status"));
            return AuthoringJson.obj("content", List.of(AuthoringJson.obj("type", "text", "text", result.toString())), "structuredContent", result, "isError", failedControl);
        } catch (AuthoringException e) {
            return AuthoringJson.obj("content", List.of(AuthoringJson.obj("type", "text", "text", AuthoringQuery.error(e).toString())), "isError", true);
        }
    }
    private JsonObject resources() {
        JsonArray resources = new JsonArray();
        for (String path : bundle.paths()) resources.add(AuthoringJson.obj("uri", "nekojs://docs/" + bundle.id() + "/" + path,
                "name", path, "mimeType", mimeType(path)));
        return AuthoringJson.obj("resources", resources);
    }
    private JsonObject read(JsonObject params) {
        String uri = AuthoringJson.string(params, "uri", "/params");
        String prefix = "nekojs://docs/" + bundle.id() + "/";
        if (!uri.startsWith(prefix)) throw new AuthoringException(404, "DOCUMENT_NOT_FOUND", "/params/uri", "Unknown documentation bundle URI.");
        String path = uri.substring(prefix.length());
        return AuthoringJson.obj("contents", List.of(AuthoringJson.obj("uri", uri, "mimeType", mimeType(path), "text", bundle.read(path))));
    }
    private static Reply rpcError(int status, JsonElement id, int code, String message) {
        return new Reply(status, AuthoringJson.obj("jsonrpc", "2.0", "id", id, "error", AuthoringJson.obj("code", code, "message", message)), null);
    }
    private static String mimeType(String path) {
        return path.endsWith(".md") ? "text/markdown" : path.endsWith(".json") ? "application/json" : "text/javascript";
    }
    @Override public synchronized void close() { closed = true; sessions.clear(); stdioSession = null; }
}
