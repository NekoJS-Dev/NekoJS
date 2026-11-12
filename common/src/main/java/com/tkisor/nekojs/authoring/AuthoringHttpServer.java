package com.tkisor.nekojs.authoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Authenticated loopback JSON HTTP and MCP with bounded work and owner-controlled shutdown. */
public final class AuthoringHttpServer implements AutoCloseable {
    private static final int MAX_BYTES = 64 * 1024;
    private final HttpServer server;
    private final ThreadPoolExecutor workers;
    private final ScheduledThreadPoolExecutor deadlines;
    private final AuthoringMcp mcp;
    private final boolean httpEnabled;
    private final boolean mcpEnabled;
    private final byte[] authorization;
    private final BiFunction<String, JsonObject, JsonObject> query;
    private final Semaphore admitted = new Semaphore(2);

    public AuthoringHttpServer(int port, String token, boolean http, boolean enableMcp, AuthoringBundle bundle,
                                BiFunction<String, JsonObject, JsonObject> query) throws IOException {
        this(port, token, http, enableMcp, bundle, query, operation -> false);
    }
    public AuthoringHttpServer(int port, String token, boolean http, boolean enableMcp, AuthoringBundle bundle,
                                BiFunction<String, JsonObject, JsonObject> query, Predicate<String> availability) throws IOException {
        this(port, token, http, enableMcp, bundle, query, availability, operation -> false);
    }
    public AuthoringHttpServer(int port, String token, boolean http, boolean enableMcp, AuthoringBundle bundle,
                                BiFunction<String, JsonObject, JsonObject> query, Predicate<String> availability, Predicate<String> liveQueryAvailability) throws IOException {
        this.authorization = ("Bearer " + token).getBytes(StandardCharsets.US_ASCII);
        this.httpEnabled = http;
        this.mcpEnabled = enableMcp;
        this.query = query;
        this.mcp = new AuthoringMcp(bundle, query, java.time.Clock.systemUTC(), (message, error) -> com.tkisor.nekojs.NekoJS.LOGGER.warn(message, error), availability, liveQueryAvailability);
        this.workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                runnable -> { Thread thread = new Thread(runnable, "NekoJS authoring HTTP"); thread.setDaemon(true); return thread; });
        this.deadlines = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "NekoJS authoring deadline"); thread.setDaemon(true); return thread;
        });
        deadlines.setRemoveOnCancelPolicy(true);
        HttpServer created = null;
        try {
            created = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
            created.createContext("/", this::handle);
            created.setExecutor(workers);
            created.start();
            this.server = created;
        } catch (IOException | RuntimeException failure) {
            if (created != null) created.stop(0);
            workers.shutdownNow(); deadlines.shutdownNow(); mcp.close();
            throw failure;
        }
    }
    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        ScheduledFuture<?> deadline = deadlines.schedule(exchange::close, 10, TimeUnit.SECONDS);
        boolean permit = false;
        try {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            checkAddress(exchange);
            List<String> credentials = exchange.getRequestHeaders().get("Authorization");
            if (credentials == null || credentials.size() != 1 || !MessageDigest.isEqual(authorization, credentials.getFirst().getBytes(StandardCharsets.US_ASCII))) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                throw new AuthoringException(401, "UNAUTHORIZED", "/", "A valid local bearer token is required.");
            }
            permit = admitted.tryAcquire();
            if (!permit) throw new AuthoringException(429, "QUERY_BUSY", "/", "Authoring request concurrency limit reached.");
            String path = exchange.getRequestURI().getRawPath();
            if (exchange.getRequestURI().getRawQuery() != null) throw AuthoringJson.invalid("/", "URL query parameters are not supported.");
            if (path.equals("/mcp") && mcpEnabled) { handleMcp(exchange); return; }
            String prefix = "/api/v1/authoring/";
            if (!httpEnabled || !path.startsWith(prefix)) throw new AuthoringException(404, "NOT_FOUND", "/", "Unknown authoring endpoint.");
            String operation = path.substring(prefix.length());
            boolean get = List.of("context", "capabilities", "schema").contains(operation);
            if (!List.of("context", "capabilities", "schema", "resolve", "members", "query", "batch", "errors", "operation_status").contains(operation)) throw new AuthoringException(404, "NOT_FOUND", "/", "Unknown authoring endpoint.");
            if (!exchange.getRequestMethod().equals(get ? "GET" : "POST")) {
                exchange.getResponseHeaders().set("Allow", get ? "GET" : "POST");
                throw new AuthoringException(405, "METHOD_NOT_ALLOWED", "/", "Unsupported HTTP method.");
            }
            send(exchange, 200, query.apply(operation, get ? new JsonObject() : body(exchange)));
        } catch (AuthoringException e) { send(exchange, e.status(), AuthoringQuery.error(e)); }
        catch (RuntimeException e) {
            com.tkisor.nekojs.NekoJS.LOGGER.warn("[NEKO-1019] Authoring request failed; operation outcome may be unknown (failureType={})", e.getClass().getSimpleName());
            send(exchange, 500, AuthoringJson.obj("code", "INTERNAL_ERROR", "message", "Authoring request failed."));
        } finally {
            if (permit) admitted.release();
            deadline.cancel(false);
            exchange.close();
        }
    }

    private void checkAddress(HttpExchange exchange) {
        String port = ":" + port();
        List<String> hosts = exchange.getRequestHeaders().get("Host");
        if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress() || hosts == null || hosts.size() != 1
                || !List.of("127.0.0.1" + port, "localhost" + port).contains(hosts.getFirst())) {
            throw new AuthoringException(403, "INVALID_HOST", "/", "Request host must match this loopback listener.");
        }
        List<String> origins = exchange.getRequestHeaders().get("Origin");
        if (origins != null && (origins.size() != 1 || !List.of("http://127.0.0.1" + port, "http://localhost" + port).contains(origins.getFirst()))) {
            throw new AuthoringException(403, "INVALID_ORIGIN", "/", "Request origin is not allowed.");
        }
    }
    private static JsonObject body(HttpExchange exchange) throws IOException {
        return AuthoringJson.parse(bodyText(exchange));
    }
    private static String bodyText(HttpExchange exchange) throws IOException {
        String type = singleHeader(exchange, "Content-Type");
        if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) throw new AuthoringException(415, "UNSUPPORTED_MEDIA_TYPE", "/", "Content-Type must be application/json.");
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new AuthoringException(413, "REQUEST_TOO_LARGE", "/", "Request exceeds 64 KiB.");
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { throw AuthoringJson.invalid("/", "Request body must be valid UTF-8."); }
    }
    private void handleMcp(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String session = singleHeader(exchange, "Mcp-Session-Id");
        String protocol = singleHeader(exchange, "MCP-Protocol-Version");
        if (method.equals("DELETE")) {
            if (!AuthoringMcp.VERSION.equals(protocol)) throw AuthoringJson.invalid("/", "Unsupported MCP protocol version.");
            send(exchange, mcp.delete(session) ? 204 : 404, null); return;
        }
        if (!method.equals("POST")) { exchange.getResponseHeaders().set("Allow", "POST, DELETE"); send(exchange, 405, null); return; }
        List<String> accept = exchange.getRequestHeaders().get("Accept");
        if (!accepts(accept, "application/json") || !accepts(accept, "text/event-stream")) throw new AuthoringException(406, "UNSUPPORTED_ACCEPT", "/", "MCP clients must accept application/json and text/event-stream.");
        JsonElement message;
        try { message = AuthoringJson.parseElement(bodyText(exchange)); }
        catch (AuthoringException e) {
            if (e.status() != 400) throw e;
            send(exchange, 400, AuthoringJson.obj("jsonrpc", "2.0", "id", null,
                    "error", AuthoringJson.obj("code", -32700, "message", e.getMessage())));
            return;
        }
        if (!message.isJsonObject()) {
            send(exchange, 400, AuthoringJson.obj("jsonrpc", "2.0", "id", null,
                    "error", AuthoringJson.obj("code", -32600, "message", "Expected one JSON-RPC request object; batches are not supported.")));
            return;
        }
        AuthoringMcp.Reply reply = mcp.handle(message.getAsJsonObject(), session, protocol);
        if (reply.session() != null) exchange.getResponseHeaders().set("Mcp-Session-Id", reply.session());
        send(exchange, reply.status(), reply.body());
    }
    private static String singleHeader(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null) return null;
        if (values.size() != 1) throw AuthoringJson.invalid("/", "Expected one " + name + " header.");
        return values.getFirst();
    }
    private static boolean accepts(List<String> headers, String mediaType) {
        if (headers == null) return false;
        for (String header : headers) for (String range : header.split(",")) {
            String[] parts = range.trim().split(";");
            if (!parts[0].trim().equalsIgnoreCase(mediaType)) continue;
            double quality = 1;
            for (int i = 1; i < parts.length; i++) {
                String[] parameter = parts[i].trim().split("=", 2);
                if (parameter[0].equalsIgnoreCase("q")) {
                    try { quality = parameter.length == 2 ? Double.parseDouble(parameter[1].trim()) : 0; }
                    catch (NumberFormatException e) { quality = 0; }
                }
            }
            if (quality > 0 && quality <= 1) return true;
        }
        return false;
    }
    private static void send(HttpExchange exchange, int status, JsonObject object) throws IOException {
        if (object == null) { exchange.sendResponseHeaders(status, -1); return; }
        byte[] bytes = object.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            status = 422;
            JsonObject error = AuthoringJson.obj("code", "RESPONSE_TOO_LARGE", "message", "Select fewer fields, reduce page size, or read the exported declaration locally.");
            if (object.has("jsonrpc")) {
                status = 200;
                if (object.has("result") && object.getAsJsonObject("result").has("content")) {
                    error = AuthoringJson.obj("jsonrpc", "2.0", "id", object.get("id"), "result", AuthoringJson.obj("isError", true,
                            "content", List.of(AuthoringJson.obj("type", "text", "text", error.toString()))));
                } else error = AuthoringJson.obj("jsonrpc", "2.0", "id", object.get("id"), "error", AuthoringJson.obj("code", -32603, "message", error.get("message")));
            }
            bytes = error.toString().getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    @Override public void close() { server.stop(0); workers.shutdownNow(); deadlines.shutdownNow(); mcp.close(); }
}
