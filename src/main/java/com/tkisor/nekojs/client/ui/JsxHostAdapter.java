//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.platform.compat.McClientCompat;
import graal.graalvm.polyglot.Value;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retained host transaction and owner-thread bridge for the common JSX runtime. */
public final class JsxHostAdapter {
    private static final Set<String> PRIMITIVES = Set.of(
            "screen", "panel", "row", "column", "stack", "scroll", "label", "button", "input", "image", "spacer");

    private final Minecraft minecraft;
    private final Thread ownerThread;
    private final JsxScreen screen;
    private final JsxHostTree tree = new JsxHostTree();
    private Value root;
    private Object lastDiagnostic;
    private int viewportWidth;
    private int viewportHeight;

    public JsxHostAdapter(String title, boolean pausesGame) {
        minecraft = Minecraft.getInstance();
        ownerThread = Thread.currentThread();
        screen = new JsxScreen(title, pausesGame, this);
    }

    JsxScreen screen() {
        return screen;
    }

    public void bindRoot(Object root) {
        this.root = root == null ? null : Value.asValue(root);
    }

    public boolean isOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    public boolean enqueue(Object action) {
        if (action == null) return false;
        minecraft.execute(() -> Value.asValue(action).executeVoid());
        return true;
    }

    public boolean supportsPrimitive(String type) {
        return PRIMITIVES.contains(type);
    }

    /** Receives the frozen common layout candidate; layout is committed without mutating guest data. */
    public void layout(Object tree) {
        if (tree != null) {
            // Materialize the candidate to validate the interop boundary before the transaction begins.
            readArray(tree);
        }
    }

    public JsxHostTransaction begin() {
        return new JsxHostTransaction(tree.begin());
    }

    public void reportDiagnostic(Object diagnostic) {
        lastDiagnostic = diagnostic;
    }

    public Object lastDiagnostic() {
        return lastDiagnostic;
    }

    public void resize(int width, int height) {
        viewportWidth = Math.max(0, width);
        viewportHeight = Math.max(0, height);
        relayout();
    }

    boolean closeOnEscape() {
        for (JsxHostTree.Node node : tree.roots()) if ("screen".equals(node.type)) return !node.props.containsKey("closeOnEscape") || bool(node.props.get("closeOnEscape"));
        return true;
    }

    void paint(Object graphics, int mouseX, int mouseY) {
        // Paint is implemented by JsxScreen so normal frames never execute a guest render function.
    }

    List<JsxHostTree.Node> roots() {
        return tree.roots();
    }

    void paintNode(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node, int mouseX, int mouseY) {
        if (node.removed || !visible(node)) return;
        int x = node.x;
        int y = node.y;
        int width = node.width;
        int height = node.height;
        String type = node.type;
        if ("#text".equals(type)) {
            graphics.text(Minecraft.getInstance().font, text(node.props.get("text")), x, y, 0xFFFFFFFF, false);
            return;
        }
        if ("panel".equals(type) || "screen".equals(type) || "scroll".equals(type)) {
            graphics.fill(x, y, x + width, y + height, color(node.props.get("background"), 0xB0101010));
            int borderWidth = integer(node.props.get("borderWidth"), 0);
            if (borderWidth > 0) graphics.outline(x, y, x + width, y + height, color(node.props.get("borderColor"), 0xFFFFFFFF));
        } else if ("button".equals(type)) {
            boolean disabled = bool(node.props.get("disabled"));
            boolean hovered = contains(node, mouseX, mouseY);
            int fill = disabled ? 0xFF404040 : hovered ? 0xFF5A7FA8 : 0xFF3A536F;
            graphics.fill(x, y, x + width, y + height, fill);
            graphics.outline(x, y, x + width, y + height, node.focused ? 0xFFFFFFFF : 0xFF9AA7B5);
            graphics.centeredText(Minecraft.getInstance().font, text(node.props.get("text")), x + width / 2, y + 6,
                    disabled ? 0xFF888888 : 0xFFFFFFFF);
        } else if ("input".equals(type)) {
            graphics.fill(x, y, x + width, y + height, 0xFF20252B);
            graphics.outline(x, y, x + width, y + height, node.focused ? 0xFFFFFFFF : 0xFF707780);
            String value = node.inputValue == null || node.inputValue.isEmpty() ? text(node.props.get("placeholder")) : node.inputValue;
            graphics.text(Minecraft.getInstance().font, value, x + 4, y + 6,
                    node.inputValue == null || node.inputValue.isEmpty() ? 0xFF888888 : 0xFFFFFFFF, false);
        } else if ("label".equals(type)) {
            graphics.text(Minecraft.getInstance().font, text(node.props.get("text")), x, y, color(node.props.get("color"), 0xFFFFFFFF), false);
        }
        if (node.focused && ("input".equals(type) || "button".equals(type))) {
            graphics.outline(x, y, x + width, y + height, 0xFFFFFFFF);
        }
        if ("scroll".equals(type)) {
            graphics.enableScissor(x, y, x + width, y + height);
            for (JsxHostTree.Node child : node.children) paintNode(graphics, child, mouseX, mouseY);
            graphics.disableScissor();
            return;
        }
        for (JsxHostTree.Node child : node.children) paintNode(graphics, child, mouseX, mouseY);
        if (contains(node, mouseX, mouseY) && node.props.get("tooltip") != null) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                    net.minecraft.network.chat.Component.literal(text(node.props.get("tooltip"))), mouseX, mouseY);
        }
    }

    boolean dispatchAt(double mouseX, double mouseY, String eventName, int button) {
        JsxHostTree.Node node = hit(tree.roots(), mouseX, mouseY);
        if (node == null || bool(node.props.get("disabled"))) return false;
        if ("click".equals(eventName) && isFocusable(node)) focus(node);
        return dispatch(node, eventName, Map.of("x", mouseX, "y", mouseY, "button", button));
    }

    boolean dispatchScroll(double mouseX, double mouseY, double delta) {
        JsxHostTree.Node node = hit(tree.roots(), mouseX, mouseY);
        if (node == null) return false;
        if ("scroll".equals(node.type)) {
            double current = number(node.props.get("scrollOffset"), 0);
            node.props.put("scrollOffset", clamp(current - delta * 12, 0, node.scrollRange));
            relayout();
        }
        return dispatch(node, "scroll", Map.of("x", mouseX, "y", mouseY, "delta", delta));
    }

    boolean key(int key, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && screen.closeOnEscape()) {
            screen.onClose();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            focusNext((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        JsxHostTree.Node focused = focused(tree.roots());
        if (focused == null) return false;
        String keyName = keyName(key);
        if (bool(focused.props.get("disabled"))) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            dispatch(focused, "key", Map.of("key", keyName));
            if ("input".equals(focused.type)) dispatch(focused, "submit", Map.of("key", keyName));
            else dispatch(focused, "click", Map.of("key", keyName));
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && "button".equals(focused.type)) {
            dispatch(focused, "key", Map.of("key", keyName));
            dispatch(focused, "click", Map.of("key", keyName));
            return true;
        }
        return dispatch(focused, "key", Map.of("key", keyName));
    }

    boolean textInput(int codepoint) {
        JsxHostTree.Node focused = focused(tree.roots());
        if (focused == null || !"input".equals(focused.type) || bool(focused.props.get("disabled"))) return false;
        String next = (focused.inputValue == null ? "" : focused.inputValue) + new String(Character.toChars(codepoint));
        int maxLength = integer(focused.props.get("maxLength"), Integer.MAX_VALUE);
        if (next.length() > maxLength) next = next.substring(0, maxLength);
        focused.inputValue = next;
        focused.props.put("value", next);
        dispatch(focused, "textInput", Map.of("value", next));
        dispatch(focused, "change", Map.of("value", next));
        return true;
    }

    public void open() {
        if (!isOwnerThread()) throw new IllegalStateException("JSX Screen must open on the client owner thread");
        if (McClientCompat.get().currentScreen() != screen) McClientCompat.get().showScreen(screen);
    }

    void close() {
        tree.close(() -> {
            if (root != null) root.invokeMember("close");
        });
        root = null;
    }

    private boolean dispatch(JsxHostTree.Node node, String eventName, Map<String, Object> input) {
        if (root == null || node == null || node.removed || node.props.get("id") == null) return false;
        try {
            Value result = root.invokeMember("dispatch", text(node.props.get("id")), eventName, input);
            return result.isBoolean() && result.asBoolean() || result.isString() && "queued".equals(result.asString());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void focus(JsxHostTree.Node next) {
        JsxHostTree.Node current = focused(tree.roots());
        if (current == next) return;
        if (current != null) { current.focused = false; dispatch(current, "blur", Map.of()); }
        if (next != null) { next.focused = true; dispatch(next, "focus", Map.of()); }
    }

    private void focusNext(boolean reverse) {
        List<JsxHostTree.Node> focusable = new ArrayList<>();
        collectFocusable(tree.roots(), focusable);
        if (focusable.isEmpty()) return;
        JsxHostTree.Node current = focused(tree.roots());
        int index = current == null ? (reverse ? 0 : -1) : focusable.indexOf(current);
        int next = (index + (reverse ? -1 : 1) + focusable.size()) % focusable.size();
        focus(focusable.get(next));
    }

    private void collectFocusable(List<JsxHostTree.Node> values, List<JsxHostTree.Node> output) {
        for (JsxHostTree.Node node : values) {
            if (isFocusable(node) && !bool(node.props.get("disabled"))) output.add(node);
            collectFocusable(node.children, output);
        }
    }

    private static JsxHostTree.Node focused(List<JsxHostTree.Node> values) {
        for (JsxHostTree.Node node : values) {
            if (node.focused) return node;
            JsxHostTree.Node nested = focused(node.children);
            if (nested != null) return nested;
        }
        return null;
    }

    private static JsxHostTree.Node hit(List<JsxHostTree.Node> values, double x, double y) {
        for (int i = values.size() - 1; i >= 0; i--) {
            JsxHostTree.Node node = values.get(i);
            if (!visible(node) || !contains(node, x, y)) continue;
            JsxHostTree.Node child = hit(node.children, x, y);
            return child == null ? node : child;
        }
        return null;
    }

    private static boolean contains(JsxHostTree.Node node, double x, double y) {
        return x >= node.x && y >= node.y && x < node.x + node.width && y < node.y + node.height;
    }

    private static boolean isFocusable(JsxHostTree.Node node) {
        return "button".equals(node.type) || "input".equals(node.type);
    }

    private void relayout() {
        layoutRoots(tree.roots());
    }

    private void layoutRoots(List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node node : roots) layoutNode(node, 0, 0, viewportWidth, viewportHeight, 0);
    }

    private void layoutNode(JsxHostTree.Node node, int x, int y, int width, int height, int depth) {
        node.x = x;
        node.y = y;
        node.width = Math.max(0, dimension(node.props.get("width"), width, 100));
        node.height = Math.max(0, dimension(node.props.get("height"), height, defaultHeight(node)));
        if ("screen".equals(node.type)) { node.width = width; node.height = height; }
        int padding = integer(node.props.get("padding"), 0);
        int gap = integer(node.props.get("gap"), 0);
        boolean row = "row".equals(node.type);
        int cursor = row ? x + padding : y + padding;
        int available = (row ? node.width : node.height) - padding * 2;
        for (JsxHostTree.Node child : node.children) {
            int childWidth = dimension(child.props.get("width"), row ? available : node.width - padding * 2, row ? 100 : node.width - padding * 2);
            int childHeight = dimension(child.props.get("height"), row ? node.height - padding * 2 : available, defaultHeight(child));
            if ("fill".equals(child.props.get("width"))) childWidth = Math.max(0, node.width - padding * 2);
            if ("fill".equals(child.props.get("height"))) childHeight = Math.max(0, node.height - padding * 2);
            int childX = row ? cursor : x + padding;
            int childY = row ? y + padding : cursor - ("scroll".equals(node.type) ? (int) number(node.props.get("scrollOffset"), 0) : 0);
            layoutNode(child, childX, childY, childWidth, childHeight, depth + 1);
            cursor += (row ? childWidth : childHeight) + gap;
            available -= (row ? childWidth : childHeight) + gap;
        }
        if ("scroll".equals(node.type)) node.scrollRange = Math.max(0, cursor - (row ? x : y) - (row ? node.width : node.height));
    }

    private static int dimension(Object value, int available, int fallback) {
        if (value instanceof Number n) return Math.max(0, n.intValue());
        if ("fill".equals(value)) return Math.max(0, available);
        return fallback;
    }

    private static int defaultHeight(JsxHostTree.Node node) {
        return switch (node.type) { case "label", "button", "input", "#text" -> 20; default -> 80; };
    }

    private static boolean visible(JsxHostTree.Node node) { return !node.removed && (!node.props.containsKey("visible") || bool(node.props.get("visible"))); }
    private static boolean bool(Object value) { return Boolean.TRUE.equals(value); }
    private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static double number(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static int color(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return (int) Long.parseLong(s.startsWith("#") ? s.substring(1) : s, 16) | 0xFF000000; }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }
    private static String keyName(int key) { return org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0) == null ? Integer.toString(key) : org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0); }

    private static Map<String, Object> props(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), scalar(item)));
            return result;
        }
        Value guest = Value.asValue(value);
        if (!guest.hasMembers()) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : guest.getMemberKeys()) result.put(key, scalar(guest.getMember(key)));
        return result;
    }

    private static List<Object> readArray(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list.stream().map(JsxHostAdapter::interopValue).toList();
        if (value instanceof Object[] array) return List.of(array);
        Value guest = Value.asValue(value);
        if (!guest.hasArrayElements()) return List.of();
        List<Object> result = new ArrayList<>();
        for (long i = 0; i < guest.getArraySize(); i++) result.add(interopValue(guest.getArrayElement(i)));
        return result;
    }

    private static Object interopValue(Object value) {
        if (!(value instanceof Value guest)) return value;
        if (guest.isHostObject()) return guest.asHostObject();
        return scalar(guest);
    }

    private static Object scalar(Object value) {
        if (!(value instanceof Value guest)) return value;
        if (guest.isNull()) return null;
        if (guest.isBoolean()) return guest.asBoolean();
        if (guest.isString()) return guest.asString();
        if (guest.isNumber()) return guest.fitsInInt() ? guest.asInt() : guest.asDouble();
        return value;
    }

    public final class JsxHostTransaction {
        private final JsxHostTree.Transaction transaction;

        private JsxHostTransaction(JsxHostTree.Transaction transaction) {
            this.transaction = transaction;
        }

        public Object create(String type, String key, Object props) {
            return transaction.create(type, key, props(props));
        }

        public void update(Object handle, String type, String key, Object props) {
            transaction.update(interopValue(handle), type, key, props(props));
        }

        public void order(Object parent, Object children) {
            transaction.order(interopValue(parent), readArray(children));
        }

        public void remove(Object handle) {
            transaction.remove(interopValue(handle));
        }

        public void commit(Object roots) {
            transaction.commit(readArray(roots), JsxHostAdapter.this::layoutRoots);
        }

        public void rollback() {
            transaction.rollback();
        }
    }
}
//?}
