package com.tkisor.nekojs.api.ui;

import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared collection path for every {@link UiInspector} implementation (ticket 45).
 * {@link #read} converts the common JSX runtime's public layout snapshot — the frozen
 * object handed to {@code NekoUiHostAdapter.layout} — into {@link InspectorSnapshot}
 * records; {@link #decorate} fills the host-only facts (focus, resource statuses,
 * retained phase errors, capture metadata); {@link #canonical} renders the stable text
 * form used by golden files and reports. Fake hosts and the NeoForge host run this same
 * code, so their records are isomorphic by construction rather than by convention.
 */
public final class InspectorSnapshots {
    private InspectorSnapshots() {
    }

    /**
     * Reads one frozen runtime layout snapshot into records.
     *
     * @param rootId   id of the measured UI root, known by the host
     * @param source   collecting environment label, e.g. {@code fake-host}
     * @param snapshot the guest layout snapshot object
     * @return frozen record tree
     * @throws IllegalStateException with {@code NEKO-8001} when the object does not match
     *         the runtime contract (runtime/host version skew)
     */
    public static InspectorSnapshot read(String rootId, String source, Value snapshot) {
        return read(rootId, source, (Object) snapshot);
    }

    /**
     * Reads a layout snapshot regardless of which side of the interop boundary it came
     * from: a guest {@link Value} or plain host data ({@code Map}/{@code List}/scalars
     * in the same shape). Both hosts collect through this one entry point.
     */
    public static InspectorSnapshot read(String rootId, String source, Object snapshot) {
        try {
            Reader reader = Reader.of(snapshot);
            if (!reader.isObject()) {
                throw malformed("snapshot is not an object");
            }
            return new InspectorSnapshot(rootId, source, readViewport(require(reader, "viewport")),
                    readNodes(require(reader, "nodes")), readDiagnostics(reader), List.of(), null);
        } catch (IllegalStateException contractFailure) {
            if (contractFailure.getMessage() != null
                    && contractFailure.getMessage().contains(UiErrorCodes.INSPECTOR_SNAPSHOT_MALFORMED)) {
                throw contractFailure;
            }
            // Guest/host scalar coercions (asBoolean/asString/...) fail with their own
            // runtime exceptions; a failed coercion is still a contract mismatch.
            throw malformed(String.valueOf(contractFailure.getMessage()));
        } catch (RuntimeException coercionFailure) {
            throw malformed(String.valueOf(coercionFailure.getMessage()));
        }
    }

    /**
     * Decorates a base snapshot with host-only facts. The same decoration rules run on
     * every host, keeping fake and NeoForge outputs isomorphic.
     *
     * @param base       snapshot from {@link #read}
     * @param focusedIds ids of nodes currently holding host focus
     * @param resources  resolves a controlled resource id to its status, null to skip
     * @param errors     retained phase-tagged failures, oldest first
     * @param screenshot capture metadata, null when no frame was captured
     * @return decorated frozen snapshot
     */
    public static InspectorSnapshot decorate(InspectorSnapshot base, Set<String> focusedIds,
            Function<String, ResourceStatus> resources, List<InspectorSnapshot.PhaseError> errors,
            InspectorScreenshot screenshot) {
        return new InspectorSnapshot(base.rootId(), base.source(), base.viewport(),
                base.nodes().stream().map(node -> decorateNode(node, focusedIds, resources)).toList(),
                base.diagnostics(), errors, screenshot);
    }

    /**
     * Canonical stable text form of a snapshot, one line per frame fact plus one line per
     * node (children indented). This is the golden-file format: deterministic in field
     * order and number formatting, so two isomorphic snapshots render identical text.
     */
    public static String canonical(InspectorSnapshot snapshot) {
        StringBuilder text = new StringBuilder();
        InspectorViewport viewport = snapshot.viewport();
        text.append("root=").append(escape(snapshot.rootId()))
                .append(" source=").append(escape(snapshot.source()))
                .append(" profile=").append(viewport.profile())
                .append(" viewport=").append(num(viewport.width())).append('x').append(num(viewport.height()))
                .append(" content=").append(num(viewport.contentWidth())).append('x').append(num(viewport.contentHeight()))
                .append(" safe=").append(num(viewport.safeArea().top())).append('/')
                .append(num(viewport.safeArea().right())).append('/')
                .append(num(viewport.safeArea().bottom())).append('/')
                .append(num(viewport.safeArea().left()))
                .append(" gui=").append(viewport.guiScale() == null ? "-" : num(viewport.guiScale()))
                .append(" design=").append(num(viewport.designScale())).append(LINE);
        text.append("diagnostics=").append(snapshot.diagnostics().isEmpty()
                ? "-" : escape(String.join(";", snapshot.diagnostics()))).append(LINE);
        text.append("errors=").append(phaseErrors(snapshot.errors())).append(LINE);
        text.append("screenshot=").append(snapshot.screenshot() == null ? "-"
                : escape(snapshot.screenshot().source()) + ' ' + num(snapshot.screenshot().width())
                        + 'x' + num(snapshot.screenshot().height())).append(LINE);
        appendNodes(text, snapshot.nodes(), 0);
        return text.toString();
    }

    /** Canonical number text: integral values without a decimal point. */
    public static String num(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** Canonical value text for style and discrete fields; nested data stays structured. */
    public static String valueText(Object value) {
        if (value == null) return "null";
        if (value instanceof Number number) return num(number.doubleValue());
        if (value instanceof List<?> list) {
            StringBuilder text = new StringBuilder("[");
            for (Object item : list) {
                if (text.length() > 1) text.append(',');
                text.append(valueText(item));
            }
            return text.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder text = new StringBuilder("{");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (text.length() > 1) text.append(',');
                text.append(entry.getKey()).append('=').append(valueText(entry.getValue()));
            }
            return text.append('}').toString();
        }
        return escape(String.valueOf(value));
    }

    private static void appendNodes(StringBuilder text, List<InspectorNode> nodes, int depth) {
        for (InspectorNode node : nodes) {
            text.append("  ".repeat(depth)).append("node type=").append(escape(node.type()))
                    .append(" key=").append(node.key() == null ? "-" : escape(node.key()))
                    .append(" id=").append(node.id() == null ? "-" : escape(node.id()))
                    .append(" visible=").append(node.visible())
                    .append(" focused=").append(node.focused())
                    .append(" rect=").append(rect(node.rect()))
                    .append(" clip=").append(rect(node.clip()))
                    .append(" overflow=").append(num(node.overflow().left())).append(',')
                    .append(num(node.overflow().top())).append(',')
                    .append(num(node.overflow().right())).append(',')
                    .append(num(node.overflow().bottom()))
                    .append(" scroll=").append(node.scrollOffset() == null ? "-" : num(node.scrollOffset()))
                    .append(" bindings=").append(node.bindings().isEmpty() ? "-" : String.join(",", node.bindings()))
                    .append(" resources=").append(node.resources().isEmpty() ? "-"
                            : node.resources().stream()
                            .map(status -> status.state() + ":" + escape(status.id())).toList().toString())
                    .append(" style=").append(styleText(node.style()))
                    .append(LINE);
            appendNodes(text, node.children(), depth + 1);
        }
    }

    private static String styleText(Map<String, Object> style) {
        StringBuilder text = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : style.entrySet()) {
            if (text.length() > 1) text.append(',');
            text.append(entry.getKey()).append('=').append(valueText(entry.getValue()));
        }
        return text.append('}').toString();
    }

    private static String rect(InspectorNode.Rect rect) {
        return num(rect.x()) + "," + num(rect.y()) + "," + num(rect.width()) + "," + num(rect.height());
    }

    private static String phaseErrors(List<InspectorSnapshot.PhaseError> errors) {
        if (errors.isEmpty()) return "-";
        StringBuilder text = new StringBuilder();
        for (InspectorSnapshot.PhaseError error : errors) {
            if (text.length() > 0) text.append('|');
            text.append(error.phase()).append(':').append(escape(error.rootId()))
                    .append(':').append(escape(error.message()));
        }
        return text.toString();
    }

    private static InspectorNode decorateNode(InspectorNode node, Set<String> focusedIds,
            Function<String, ResourceStatus> resources) {
        List<ResourceStatus> statuses = new ArrayList<>();
        if (resources != null) {
            addResource(statuses, resources, node.style().get("resource"));
            addResource(statuses, resources, node.style().get("icon"));
        }
        return new InspectorNode(node.id(), node.type(), node.key(), node.visible(),
                node.id() != null && focusedIds.contains(node.id()),
                node.rect(), node.clip(), node.overflow(), node.scrollOffset(), node.style(),
                node.bindings(), statuses,
                node.children().stream().map(child -> decorateNode(child, focusedIds, resources)).toList());
    }

    private static void addResource(List<ResourceStatus> statuses, Function<String, ResourceStatus> resources,
            Object id) {
        if (id instanceof String name && !name.isEmpty()) statuses.add(resources.apply(name));
    }

    private static InspectorViewport readViewport(Reader viewport) {
        if (!viewport.isObject()) throw malformed("viewport is not an object");
        Reader safeArea = require(viewport, "safeArea");
        Integer guiScale = viewport.hasMember("guiScale") && !viewport.member("guiScale").isNull()
                ? (int) requireNumber(viewport, "guiScale") : null;
        return new InspectorViewport(
                (int) requireNumber(viewport, "width"),
                (int) requireNumber(viewport, "height"),
                new InspectorViewport.SafeArea(
                        (int) requireNumber(safeArea, "top"),
                        (int) requireNumber(safeArea, "right"),
                        (int) requireNumber(safeArea, "bottom"),
                        (int) requireNumber(safeArea, "left")),
                (int) requireNumber(viewport, "contentWidth"),
                (int) requireNumber(viewport, "contentHeight"),
                (int) requireNumber(viewport, "profile"),
                guiScale,
                requireNumber(viewport, "designScale"));
    }

    private static List<InspectorNode> readNodes(Reader nodes) {
        if (!nodes.isArray()) throw malformed("nodes is not an array");
        List<InspectorNode> result = new ArrayList<>();
        for (long i = 0; i < nodes.arraySize(); i++) {
            result.add(readNode(nodes.element(i)));
        }
        return result;
    }

    private static InspectorNode readNode(Reader node) {
        if (!node.isObject()) throw malformed("node is not an object");
        Map<String, Object> style = require(node, "style").plainMap();
        Double scrollOffset = style.get("scrollOffset") instanceof Number number
                ? number.doubleValue() : null;
        return new InspectorNode(
                textOrNull(node, "id"),
                requireText(node, "type"),
                textOrNull(node, "key"),
                !node.hasMember("visible") || node.member("visible").asBoolean(),
                false,
                readRect(node, "rect"),
                readRect(node, "clip"),
                readOverflow(require(node, "overflow")),
                scrollOffset,
                style,
                readBindings(node),
                List.of(),
                readNodes(require(node, "children")));
    }

    private static InspectorNode.Rect readRect(Reader node, String name) {
        Reader rect = require(node, name);
        return new InspectorNode.Rect(
                requireNumber(rect, "x"), requireNumber(rect, "y"),
                requireNumber(rect, "width"), requireNumber(rect, "height"));
    }

    private static InspectorNode.Overflow readOverflow(Reader overflow) {
        return new InspectorNode.Overflow(
                requireNumber(overflow, "left"), requireNumber(overflow, "top"),
                requireNumber(overflow, "right"), requireNumber(overflow, "bottom"));
    }

    private static List<String> readBindings(Reader node) {
        if (!node.hasMember("bindings")) return List.of();
        Reader bindings = node.member("bindings");
        if (!bindings.isArray()) throw malformed("bindings is not an array");
        List<String> result = new ArrayList<>();
        for (long i = 0; i < bindings.arraySize(); i++) {
            result.add(bindings.element(i).asString());
        }
        return result;
    }

    private static List<String> readDiagnostics(Reader snapshot) {
        if (!snapshot.hasMember("diagnostics")) return List.of();
        Reader diagnostics = snapshot.member("diagnostics");
        if (!diagnostics.isArray()) throw malformed("diagnostics is not an array");
        List<String> result = new ArrayList<>();
        for (long i = 0; i < diagnostics.arraySize(); i++) {
            result.add(diagnostics.element(i).asString());
        }
        return result;
    }

    private static Reader require(Reader value, String name) {
        if (!value.hasMember(name)) throw malformed("member '" + name + "' is absent");
        return value.member(name);
    }

    private static double requireNumber(Reader value, String name) {
        Reader member = require(value, name);
        if (!member.isNumber()) throw malformed("member '" + name + "' is not a number");
        return member.asDouble();
    }

    private static String requireText(Reader value, String name) {
        Reader member = require(value, name);
        if (!member.isString()) throw malformed("member '" + name + "' is not a string");
        return member.asString();
    }

    private static String textOrNull(Reader value, String name) {
        return value.hasMember(name) && !value.member(name).isNull()
                ? value.member(name).asString() : null;
    }

    /**
     * Uniform member access over a guest {@link Value} or plain host data in the same
     * shape, so the collection path has exactly one implementation. Scalars keep their
     * natural Java types; nested objects and arrays stay {@code Map}/{@code List}.
     */
    private interface Reader {
        boolean isObject();

        boolean isArray();

        boolean isNull();

        boolean asBoolean();

        boolean isString();

        String asString();

        boolean isNumber();

        double asDouble();

        boolean hasMember(String name);

        Reader member(String name);

        long arraySize();

        Reader element(long index);

        Map<String, Object> plainMap();

        static Reader of(Object value) {
            if (value instanceof Reader reader) return reader;
            if (value instanceof Value guest) return new GuestReader(guest);
            return new HostReader(value);
        }
    }

    private record GuestReader(Value value) implements Reader {
        @Override public boolean isObject() { return !value.isNull() && value.hasMembers() && !value.hasArrayElements(); }
        @Override public boolean isArray() { return value.hasArrayElements(); }
        @Override public boolean isNull() { return value.isNull(); }
        @Override public boolean asBoolean() { return value.asBoolean(); }
        @Override public boolean isString() { return value.isString(); }
        @Override public String asString() { return value.asString(); }
        @Override public boolean isNumber() { return value.isNumber(); }
        @Override public double asDouble() { return value.asDouble(); }
        private int asInt() { return value.asInt(); }
        @Override public boolean hasMember(String name) { return value.hasMember(name); }
        @Override public Reader member(String name) { return new GuestReader(value.getMember(name)); }
        @Override public long arraySize() { return value.getArraySize(); }
        @Override public Reader element(long index) { return new GuestReader(value.getArrayElement(index)); }

        @Override public Map<String, Object> plainMap() {
            if (!isObject()) throw malformed("expected an object");
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                result.put(key, new GuestReader(value.getMember(key)).plain());
            }
            return result;
        }

        private Object plain() {
            if (isNull()) return null;
            if (value.isBoolean()) return value.asBoolean();
            if (isString()) return asString();
            if (isNumber()) return value.fitsInInt() ? asInt() : asDouble();
            if (isArray()) {
                List<Object> result = new ArrayList<>();
                for (long i = 0; i < arraySize(); i++) result.add(new GuestReader(value.getArrayElement(i)).plain());
                return result;
            }
            if (value.hasMembers()) return plainMap();
            return value.toString();
        }
    }

    private record HostReader(Object value) implements Reader {
        @Override public boolean isObject() { return value instanceof Map; }
        @Override public boolean isArray() { return value instanceof List; }
        @Override public boolean isNull() { return value == null; }
        // Strict like the guest path: a non-boolean is a contract mismatch, not false.
        @Override public boolean asBoolean() {
            if (!(value instanceof Boolean bool)) throw malformed("expected a boolean, got " + value);
            return bool;
        }
        @Override public boolean isString() { return value instanceof String; }
        @Override public String asString() { return String.valueOf(value); }
        @Override public boolean isNumber() { return value instanceof Number; }
        @Override public double asDouble() { return ((Number) value).doubleValue(); }
        @Override public boolean hasMember(String name) { return value instanceof Map<?, ?> map && map.containsKey(name); }
        @Override public Reader member(String name) { return new HostReader(((Map<?, ?>) value).get(name)); }
        @Override public long arraySize() { return ((List<?>) value).size(); }
        @Override public Reader element(long index) { return new HostReader(((List<?>) value).get((int) index)); }

        @Override public Map<String, Object> plainMap() {
            if (!isObject()) throw malformed("expected an object");
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()),
                        entry.getValue() instanceof Value guest ? new GuestReader(guest).plain() : entry.getValue());
            }
            return result;
        }
    }

    private static IllegalStateException malformed(String detail) {
        return new IllegalStateException("[" + UiErrorCodes.INSPECTOR_SNAPSHOT_MALFORMED
                + "] UI inspector snapshot does not match the runtime contract: " + detail);
    }

    private static String escape(String value) {
        return value.replace(BACKSLASH, ESCAPED_BACKSLASH).replace(LINE, ESCAPED_NEWLINE)
                .replace(";", ESCAPED_SEMICOLON).replace("|", ESCAPED_PIPE);
    }

    private static final String LINE = String.valueOf((char) 10);
    private static final String BACKSLASH = String.valueOf((char) 92);
    private static final String ESCAPED_BACKSLASH = BACKSLASH + BACKSLASH;
    private static final String ESCAPED_NEWLINE = BACKSLASH + "n";
    private static final String ESCAPED_SEMICOLON = BACKSLASH + ";";
    private static final String ESCAPED_PIPE = BACKSLASH + "|";
}
