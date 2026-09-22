package com.tkisor.nekojs.core.error;

import com.tkisor.nekojs.api.ScriptType;

import java.util.Objects;

/**
 * Locatable source-path/action record for one diagnostic (ticket 30 non-GUI seam).
 *
 * <p>This is the parseable hand-off consumed by ticket 27 to implement the external IDE open
 * and the read-only report GUI. It carries only attribution and location data — generation,
 * owner, ScriptType, source path, line/column — plus the action verb. It performs no GUI
 * behavior and no process dispatch; local-file validation remains the job of
 * {@link LocalErrorSource} on the consumer side.
 *
 * <p>The {@link #payload()} form is a single stable line of {@code key=value} pairs; values
 * containing whitespace are double-quoted. {@link #parse(String)} is the inverse and rejects
 * malformed input instead of guessing.
 */
public record DiagnosticOpenAction(
        String action,
        ScriptType scriptType,
        String owner,
        long generation,
        String sourcePath,
        int line,
        int column
) {
    /** Action verb: open the authored source at the recorded location. */
    public static final String ACTION_OPEN_SOURCE = "open-source";

    public DiagnosticOpenAction {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(scriptType, "scriptType");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(sourcePath, "sourcePath");
    }

    /** Human-readable, parseable single-line payload. */
    public String payload() {
        return "action=" + quote(action)
                + " type=" + quote(scriptType.name)
                + " owner=" + quote(owner)
                + " generation=" + generation
                + " source=" + quote(sourcePath)
                + " line=" + line
                + " column=" + column;
    }
    /**
     * Parse a payload produced by {@link #payload()}. Malformed input throws
     * {@link IllegalArgumentException} with a stable message.
     */
    public static DiagnosticOpenAction parse(String payload) {
        Objects.requireNonNull(payload, "payload");
        String action = null;
        ScriptType type = null;
        String owner = null;
        Long generation = null;
        String source = null;
        Integer line = null;
        Integer column = null;
        for (String token : tokenize(payload)) {
            if (token.isBlank()) continue;
            int eq = token.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("malformed diagnostic open payload token: " + token);
            }
            String key = token.substring(0, eq);
            String value = unquote(token.substring(eq + 1));
            switch (key) {
                case "action" -> action = value;
                case "type" -> type = ScriptType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                case "owner" -> owner = value;
                case "generation" -> generation = Long.parseLong(value);
                case "source" -> source = value;
                case "line" -> line = Integer.parseInt(value);
                case "column" -> column = Integer.parseInt(value);
                default -> throw new IllegalArgumentException("unknown diagnostic open payload key: " + key);
            }
        }
        if (action == null || type == null || owner == null || generation == null
                || source == null || line == null || column == null) {
            throw new IllegalArgumentException("incomplete diagnostic open payload: " + payload);
        }
        return new DiagnosticOpenAction(action, type, owner, generation, source, line, column);
    }

    /** Split on spaces that are not inside a double-quoted value (quotes may wrap spaces). */
    private static java.util.List<String> tokenize(String payload) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < payload.length(); i++) {
            char c = payload.charAt(i);
            if (c == '\\' && inQuotes && i + 1 < payload.length()) {
                current.append(c).append(payload.charAt(++i));
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
                continue;
            }
            if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (inQuotes) {
            throw new IllegalArgumentException("unterminated quoted payload value: " + payload);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String quote(String value) {
        if (value.indexOf(' ') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\\') >= 0) {
            StringBuilder escaped = new StringBuilder("\"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"' || c == '\\') {
                    escaped.append('\\');
                }
                escaped.append(c);
            }
            return escaped.append('"').toString();
        }
        return value;
    }

    private static String unquote(String value) {
        if (!value.startsWith("\"")) {
            return value;
        }
        if (!value.endsWith("\"") || value.length() < 2) {
            throw new IllegalArgumentException("unterminated quoted payload value: " + value);
        }
        StringBuilder decoded = new StringBuilder();
        String body = value.substring(1, value.length() - 1);
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                decoded.append(body.charAt(++i));
            } else {
                decoded.append(c);
            }
        }
        return decoded.toString();
    }
}
