package com.tkisor.nekojs.api.ui;

/**
 * One locatable UI diagnostic: a {@link UiErrorCodes NEKO-} code, an English
 * summary, and the position (UI root, node, resource, generation) the failure
 * came from. Diagnostics are data; the reporting seam decides where they surface.
 */
public record UiDiagnostic(String code, String message, String rootId, String nodeType, String nodeKey,
                           String resourceId, long generation) {
    /** Where the resolving node lives; stamped into every diagnostic it produces. */
    public record Location(String rootId, String nodeType, String nodeKey, long generation) {
    }

    /**
     * Single-line log form: {@code [NEKO-6004] summary — root=<..> node=<type#key>
     * resource=<id> generation=<n>}; empty segments are omitted.
     */
    public String logLine() {
        StringBuilder where = new StringBuilder();
        append(where, "root", rootId);
        if (nodeType != null || nodeKey != null) {
            append(where, "node", (nodeType == null ? "" : nodeType) + "#" + (nodeKey == null ? "" : nodeKey));
        }
        append(where, "resource", resourceId);
        if (generation >= 0) where.append(" generation=").append(generation);
        return "[" + code + "] " + message + (where.isEmpty() ? "" : " — " + where);
    }

    private static void append(StringBuilder builder, String name, String value) {
        if (value != null && !value.isEmpty()) builder.append(builder.isEmpty() ? "" : " ").append(name).append("=").append(value);
    }
}
