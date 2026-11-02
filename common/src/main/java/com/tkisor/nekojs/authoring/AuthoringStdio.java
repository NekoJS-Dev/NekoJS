package com.tkisor.nekojs.authoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/** Bounded newline-delimited MCP over caller-owned streams; stdout carries protocol messages only. */
final class AuthoringStdio {
    private static final int MAX_BYTES = 64 * 1024;
    private final AuthoringBundle bundle;
    private final BiFunction<String, JsonObject, JsonObject> query;

    AuthoringStdio(AuthoringBundle bundle, BiFunction<String, JsonObject, JsonObject> query) {
        this.bundle = Objects.requireNonNull(bundle);
        this.query = Objects.requireNonNull(query);
    }

    /** Runs one connection until EOF, I/O failure, or an oversized frame; never closes the supplied streams. */
    void run(InputStream input, OutputStream output, PrintStream diagnostics) throws IOException {
        Objects.requireNonNull(input); Objects.requireNonNull(output); Objects.requireNonNull(diagnostics);
        BufferedInputStream buffered = new BufferedInputStream(input);
        try (AuthoringMcp mcp = new AuthoringMcp(bundle, query, (message, error) ->
                diagnostics.println(message + " (" + error.getClass().getName() + ")"))) {
            while (true) {
                byte[] frame;
                try { frame = frame(buffered); }
                catch (FrameTooLargeException e) {
                    diagnostics.println("[NEKO-1019] Authoring stdio input exceeded 64 KiB; connection closed.");
                    send(output, error(null, -32600, "Request exceeds 64 KiB; connection closed."));
                    return;
                }
                if (frame == null) return;
                JsonElement message;
                try {
                    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(frame)).toString();
                    message = AuthoringJson.parseElement(text);
                } catch (CharacterCodingException | AuthoringException e) {
                    send(output, error(null, -32700, "Malformed UTF-8 JSON request."));
                    continue;
                }
                if (!message.isJsonObject()) {
                    send(output, error(null, -32600, "Expected one JSON-RPC request object; batches are not supported."));
                    continue;
                }
                JsonObject response = mcp.handleStdio(message.getAsJsonObject());
                if (response != null) send(output, response);
            }
        }
    }

    private static byte[] frame(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            int value = input.read();
            if (value == -1) return bytes.size() == 0 ? null : bytes.toByteArray();
            if (value == '\n') return bytes.toByteArray();
            if (bytes.size() == MAX_BYTES) throw new FrameTooLargeException();
            bytes.write(value);
        }
    }

    private static void send(OutputStream output, JsonObject response) throws IOException {
        byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            String message = "Select fewer fields, reduce page size, or read the exported declaration locally.";
            if (response.has("result") && response.getAsJsonObject("result").has("content")) {
                JsonObject detail = AuthoringJson.obj("code", "RESPONSE_TOO_LARGE", "message", message);
                response = AuthoringJson.obj("jsonrpc", "2.0", "id", response.get("id"), "result", AuthoringJson.obj("isError", true,
                        "content", List.of(AuthoringJson.obj("type", "text", "text", detail.toString()))));
            } else response = error(response.get("id"), -32603, message);
            bytes = response.toString().getBytes(StandardCharsets.UTF_8);
            // A near-limit request ID can itself exhaust the reply budget, so fail without echoing it.
            if (bytes.length > MAX_BYTES) bytes = error(null, -32600, "Request ID exceeds the response budget.").toString().getBytes(StandardCharsets.UTF_8);
        }
        output.write(bytes);
        output.write('\n');
        output.flush();
        if (output instanceof PrintStream stream && stream.checkError()) throw new IOException("[NEKO-1019] Authoring stdio output failed.");
    }

    private static JsonObject error(JsonElement id, int code, String message) {
        return AuthoringJson.obj("jsonrpc", "2.0", "id", id, "error", AuthoringJson.obj("code", code, "message", message));
    }

    private static final class FrameTooLargeException extends IOException {}
}
