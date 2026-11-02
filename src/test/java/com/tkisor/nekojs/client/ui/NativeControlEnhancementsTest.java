//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Native adapter checks use an in-memory clipboard and a recording painter, never an OS window. */
class NativeControlEnhancementsTest {
    @Test
    void copyCutAndPastePreserveUnicodeAndCodePointCapacity() throws Exception {
        try (Harness fixture = new Harness("input", Map.of("value", "a😀bc", "maxLength", 4))) {
            JsxHostTree.Node node = fixture.node();
            node.focused = true;
            node.selectionStart = 1;
            node.selectionEnd = 3;
            node.cursor = 3;
            assertTrue(fixture.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL));
            assertEquals("😀", fixture.clipboard.value);
            assertEquals("a😀bc", node.inputValue);
            assertTrue(fixture.changes.isEmpty());
            assertTrue(fixture.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_SUPER));
            assertEquals("abc", node.inputValue);
            fixture.clipboard.value = "\n😀\tZ\u0000\uD800";
            assertTrue(fixture.key(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL));
            assertEquals("a😀bc", node.inputValue);
            assertEquals(List.of("abc", "a😀bc"), fixture.changes);
            assertEquals(4, node.inputValue.codePointCount(0, node.inputValue.length()));
            assertEquals(3, node.cursor);
        }
    }

    @Test
    void clipboardFailureNeverDeletesOrReplacesTheSelection() throws Exception {
        try (Harness fixture = new Harness("input", Map.of("value", "abc"))) {
            JsxHostTree.Node node = fixture.node();
            node.focused = true;
            node.selectAll();
            fixture.clipboard.failWrite = true;
            assertTrue(fixture.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL));
            assertEquals("abc", node.inputValue);
            assertTrue(node.hasSelection());
            fixture.clipboard.failRead = true;
            assertTrue(fixture.key(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL));
            assertEquals("abc", node.inputValue);
            assertTrue(node.hasSelection());
            assertTrue(fixture.changes.isEmpty());
        }
    }

    @Test
    void disabledClosedAndOffOwnerThreadInputsNeverReachTheClipboard() throws Exception {
        try (Harness fixture = new Harness("input", Map.of("value", "abc"));
             var executor = Executors.newSingleThreadExecutor()) {
            fixture.node().focused = true;
            fixture.node().selectAll();
            fixture.update(Map.of("value", "abc", "disabled", true));
            assertFalse(fixture.key(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL));
            fixture.update(Map.of("value", "abc"));
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> executor.submit(() -> fixture.key(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL)).get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause().getMessage().contains("NEKO-7004"));
            fixture.base.globals.close();
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> fixture.key(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL)).getMessage().contains("NEKO-7001"));
            assertEquals(0, fixture.clipboard.reads);
            assertEquals(0, fixture.clipboard.writes);
        }
    }

    @Test
    void invalidPasteIsANoOpAndReplacementCapacityIncludesTheRemovedSelection() {
        JsxHostTree.Node input = new JsxHostTree.Node(1, "input", "field",
                new LinkedHashMap<>(Map.of("value", "a😀c")));
        input.selectionStart = 1;
        input.selectionEnd = 3;
        input.cursor = 3;
        input.insertText("\r\n\t\u007F\u0085\uD800", 3);
        assertEquals("a😀c", input.inputValue);
        assertTrue(input.hasSelection());
        input.insertText("🦊Z", 3);
        assertEquals("a🦊c", input.inputValue);
        assertEquals(3, input.cursor);
    }

    @Test
    void horizontalOffsetFollowsCaretAndMouseUsesTheSamePaintedCoordinates() throws Exception {
        try (Harness fixture = new Harness("input", Map.of("value", "abcdefghij"))) {
            JsxHostTree.Node node = fixture.node();
            node.width = 32;
            node.focused = true;
            Painter painter = fixture.paint(-1, -1);
            assertEquals(37, node.inputScroll);
            assertEquals(-23, painter.texts.getFirst().x);
            assertTrue(painter.fills.contains(new Fill(37, 24, 38, 40, 0xFFFFFFFF)));
            assertTrue(fixture.base.adapter.dispatchAt(14, 25, "click", 0, 0));
            assertEquals(6, fixture.node().cursor);
            assertTrue(fixture.base.adapter.textInput('X'));
            assertEquals("abcdefXghij", fixture.node().inputValue);
            fixture.key(GLFW.GLFW_KEY_END, 0);
            fixture.paint(-1, -1);
            assertTrue(fixture.node().inputScroll > 0);
            fixture.key(GLFW.GLFW_KEY_HOME, 0);
            fixture.paint(-1, -1);
            assertEquals(0, fixture.node().inputScroll);
            fixture.node().collapseSelection(fixture.node().inputValue.length());
            fixture.paint(-1, -1);
            fixture.update(Map.of("value", "x"));
            fixture.paint(-1, -1);
            assertEquals(0, fixture.node().inputScroll);
        }
    }

    @Test
    void inputSelectionAndCaretStayWithinBalancedViewportAndParentScrollScissors() throws Exception {
        try (Harness fixture = new Harness("input", Map.of("value", "abcdefghij"))) {
            JsxHostTree.Node input = fixture.node();
            input.focused = true;
            input.selectAll();
            JsxHostTree.Transaction transaction = fixture.tree.begin();
            Object parent = transaction.create("scroll", "parent", Map.of());
            transaction.order(parent, List.of(input));
            transaction.order(null, List.of(parent));
            transaction.commit(List.of(parent));
            JsxHostTree.Node scroll = fixture.tree.roots().getFirst();
            scroll.width = 30;
            scroll.height = 60;
            JsxHostTree.Node retained = scroll.children.getFirst();
            retained.hasClip = true;
            retained.clipX = 10;
            retained.clipY = 20;
            retained.clipWidth = 20;
            retained.clipHeight = 24;
            Painter painter = fixture.paint(-1, -1);
            assertEquals(0, painter.clips.size());
            assertEquals(painter.pushes, painter.pops);
            assertTrue(painter.pushes >= 3);
            TextDraw text = painter.texts.getFirst();
            assertEquals(new Clip(14, 24, 30, 40), text.clip);
            assertTrue(painter.fills.stream().filter(fill -> fill.color == 0xFF4A6A95)
                    .allMatch(fill -> fill.left >= 14 && fill.right <= 30 && fill.top >= 24 && fill.bottom <= 40));
        }
    }

    @Test
    void sliderRejectsInvalidAndOverflowingNumericalRanges() {
        List<Map<String, Object>> invalid = List.of(
                Map.of("min", 1, "max", 1), Map.of("min", 2, "max", 1), Map.of("step", 0),
                Map.of("step", -1), Map.of("value", Double.NaN), Map.of("max", Double.POSITIVE_INFINITY),
                Map.of("value", "0"), Map.of("value", 101), Map.of("min", 1),
                Map.of("min", -Double.MAX_VALUE, "max", Double.MAX_VALUE),
                Map.of("max", Double.MAX_VALUE, "step", Double.MIN_VALUE));
        for (Map<String, Object> props : invalid) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> new JsxHostTree.Node(
                    1, "slider", "range", new LinkedHashMap<>(props))).getMessage().contains("NEKO-7011"));
        }
        JsxHostTree.Node huge = new JsxHostTree.Node(1, "slider", "huge",
                new LinkedHashMap<>(Map.of("max", 1e30, "step", 1)));
        assertTrue(huge.updateSlider(5e29, true));
        assertEquals(5e29, huge.sliderValue);
        assertTrue(Double.isFinite(huge.sliderValue));
    }

    @Test
    void sliderKeysClampAndPublishStringValuesWithoutDuplicateChanges() throws Exception {
        try (Harness fixture = new Harness("slider", Map.of("value", 0, "min", 0, "max", 10, "step", 3))) {
            fixture.node().focused = true;
            fixture.key(GLFW.GLFW_KEY_RIGHT, 0);
            fixture.key(GLFW.GLFW_KEY_UP, 0);
            fixture.key(GLFW.GLFW_KEY_END, 0);
            fixture.key(GLFW.GLFW_KEY_HOME, 0);
            fixture.key(GLFW.GLFW_KEY_LEFT, 0);
            assertEquals(List.of("3.0", "6.0", "10.0", "0.0"), fixture.changes);
            fixture.update(Map.of("value", 0, "min", 0, "max", 10, "step", 3, "disabled", true));
            assertFalse(fixture.key(GLFW.GLFW_KEY_RIGHT, 0));
            assertFalse(fixture.base.adapter.dispatchAt(50, 25, "click", 0, 0));
        }
    }

    @Test
    void sliderOnlyCapturesPrimaryDragAndCancelsDisabledOrReplacedGestures() throws Exception {
        try (Harness fixture = new Harness("slider", Map.of("max", 10, "step", 3))) {
            assertFalse(fixture.base.adapter.dispatchAt(60, 25, "click", 1, 0));
            assertTrue(fixture.base.adapter.dispatchAt(60, 25, "click", 0, 0));
            assertEquals(6, fixture.node().sliderValue);
            assertFalse(fixture.base.adapter.dispatchRelease(60, 25, 1));
            assertTrue(fixture.base.adapter.dispatchDrag(1000, 200, 0));
            assertEquals(10, fixture.node().sliderValue);
            fixture.update(Map.of("max", 10, "step", 3, "disabled", true));
            fixture.update(Map.of("max", 10, "step", 3));
            assertFalse(fixture.base.adapter.dispatchDrag(0, 25, 0));
            assertTrue(fixture.base.adapter.dispatchAt(60, 25, "click", 0, 0));
            JsxHostTree.Transaction transaction = fixture.tree.begin();
            transaction.update(fixture.node(), "button", "control", Map.of("text", "Replacement"));
            transaction.commit(fixture.tree.roots());
            assertFalse(fixture.base.adapter.dispatchDrag(1000, 25, 0));
            assertFalse(fixture.node().pressed);
        }
    }

    @Test
    void decimalSliderEndpointRemainsExactOnClickDragAndRelease() throws Exception {
        try (Harness fixture = new Harness("slider", Map.of("min", -10, "max", 0.1, "step", 1, "value", 0))) {
            assertTrue(fixture.base.adapter.dispatchAt(106, 25, "click", 0, 0));
            assertEquals(0.1, fixture.node().sliderValue);
            assertTrue(fixture.base.adapter.dispatchDrag(-1000, 25, 0));
            assertEquals(-10, fixture.node().sliderValue);
            assertTrue(fixture.base.adapter.dispatchRelease(1000, 25, 0));
            assertEquals(0.1, fixture.node().sliderValue);
            assertEquals(List.of("0.1", "-10.0", "0.1"), fixture.changes);
        }
    }

    @Test
    void actualJsRuntimeReceivesTheNativeSliderStringValue() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.values = [];
                    globalThis.root = UI.createRoot(() => UI.element('slider', {
                      id: 'slider', value: 0, min: -10, max: 0.1, step: 1, width: 100, height: 24,
                      onChange: event => values.push([typeof event.value, event.value])
                    }), host, { id: 'slider-event-root', viewport: { width: 100, height: 100 } });
                    host.bindRoot(root);
                    """);
            assertTrue(fixture.adapter.dispatchAt(96, 10, "click", 0, 0));
            assertEquals("[[\"string\",\"0.1\"]]", fixture.context.eval("js", "JSON.stringify(values)").asString());
        }
    }

    @Test
    void sliderCallbackRemovalCannotContinueDispatchingTheCapturedNode() throws Exception {
        try (Harness fixture = new Harness("slider", Map.of())) {
            fixture.onChange = fixture.tree::clear;
            assertTrue(fixture.base.adapter.dispatchAt(60, 25, "click", 0, 0));
            assertTrue(fixture.tree.roots().isEmpty());
            assertFalse(fixture.base.adapter.dispatchDrag(70, 25, 0));
            assertFalse(fixture.base.adapter.dispatchRelease(70, 25, 0));
            assertEquals(1, fixture.changes.size());
        }
    }

    @Test
    void controlAppearanceReachesNativeButtonInputAndSliderPaintWithoutWhiteFocusOverwrite() throws Exception {
        Map<String, Object> appearance = Map.of("background", "red", "borderColor", "blue", "color", "lime",
                "focused", Map.of("borderColor", "yellow"), "pressed", Map.of("borderColor", "aqua"),
                "disabled", Map.of("borderColor", "fuchsia"), "trackColor", "navy", "thumbColor", "teal",
                "selectionColor", "purple", "cursorColor", "maroon");
        for (String type : List.of("button", "input", "slider")) {
            try (Harness fixture = new Harness(type, Map.of("appearance", appearance))) {
                fixture.node().focused = true;
                fixture.node().pressed = true;
                fixture.node().props.put("disabled", true);
                Painter painter = fixture.paint(20, 25);
                assertTrue(painter.fills.stream().anyMatch(fill -> fill.color == 0xFFFF0000));
                assertEquals(List.of(0xFFFF00FF), painter.outlines);
                if (type.equals("slider")) {
                    assertTrue(painter.fills.stream().anyMatch(fill -> fill.color == 0xFF000080));
                    assertTrue(painter.fills.stream().anyMatch(fill -> fill.color == 0xFF008080));
                }
                if (type.equals("input")) {
                    assertTrue(painter.fills.stream().anyMatch(fill -> fill.color == 0xFF800000));
                }
            }
        }
    }

    @Test
    void invalidAppearanceAndSliderCandidatesLeaveThePublishedTreeIntact() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object original = initial.create("input", "field", Map.of("value", "safe"));
        initial.order(null, List.of(original));
        initial.commit(List.of(original));
        JsxHostTree.Node published = tree.roots().getFirst();
        JsxHostTree.Transaction candidate = tree.begin();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> candidate.create("button", "bad",
                Map.of("appearance", Map.of("hover", Map.of("pressed", Map.of("color", "red"))))))
                .getMessage().contains("NEKO-7008"));
        assertThrows(IllegalArgumentException.class, () -> candidate.create("slider", "bad", Map.of("step", 0)));
        candidate.rollback();
        assertSame(published, tree.roots().getFirst());
        assertEquals("safe", published.inputValue);
    }

    private static final class Harness implements AutoCloseable {
        final NativeUiScreenFixture base = new NativeUiScreenFixture();
        final JsxHostTree tree = new JsxHostTree();
        final MemoryClipboard clipboard = new MemoryClipboard();
        final List<String> changes = new ArrayList<>();
        Runnable onChange = () -> { };

        Harness(String type, Map<String, Object> props) throws Exception {
            Field field = JsxHostAdapter.class.getDeclaredField("tree");
            field.setAccessible(true);
            field.set(base.adapter, tree);
            base.adapter.clipboard(clipboard);
            ProxyExecutable dispatch = args -> {
                if (args[1].asString().equals("change")) {
                    assertTrue(args[2].hasHashEntries());
                    assertTrue(args[2].getHashValue("value").isString());
                    changes.add(args[2].getHashValue("value").asString());
                    onChange.run();
                }
                return true;
            };
            base.adapter.bindRoot(base.context.asValue(ProxyObject.fromMap(Map.of(
                    "id", "control-test", "dispatch", dispatch, "close", (ProxyExecutable) args -> null))));
            JsxHostTree.Transaction transaction = tree.begin();
            Object handle = transaction.create(type, "control", props);
            transaction.order(null, List.of(handle));
            transaction.commit(List.of(handle));
            node().props.put("id", "control");
            node().x = 10;
            node().y = 20;
            node().width = 100;
            node().height = 24;
        }

        JsxHostTree.Node node() { return tree.roots().getFirst(); }

        boolean key(int key, int modifiers) { return base.adapter.key(key, modifiers); }

        void update(Map<String, Object> props) {
            JsxHostTree.Transaction transaction = tree.begin();
            Map<String, Object> next = new LinkedHashMap<>(props);
            next.put("id", "control");
            transaction.update(node(), node().type, "control", next);
            transaction.commit(tree.roots());
        }

        Painter paint(int mouseX, int mouseY) throws Exception {
            Painter painter = allocate(Painter.class);
            painter.clips = new ArrayDeque<>();
            painter.fills = new ArrayList<>();
            painter.outlines = new ArrayList<>();
            painter.texts = new ArrayList<>();
            base.screen.extractRenderState(painter, mouseX, mouseY, 0);
            return painter;
        }

        @Override
        public void close() throws Exception { base.close(); }
    }

    private static final class MemoryClipboard implements JsxHostAdapter.Clipboard {
        String value = "";
        boolean failRead;
        boolean failWrite;
        int reads;
        int writes;

        @Override
        public String read() {
            reads++;
            if (failRead) throw new IllegalStateException("Fixture clipboard read failed");
            return value;
        }

        @Override
        public void write(String value) {
            writes++;
            if (failWrite) throw new IllegalStateException("Fixture clipboard write failed");
            this.value = value;
        }
    }

    private record Clip(int left, int top, int right, int bottom) {
        Clip intersect(Clip other) {
            int x = Math.max(left, other.left);
            int y = Math.max(top, other.top);
            return new Clip(x, y, Math.max(x, Math.min(right, other.right)), Math.max(y, Math.min(bottom, other.bottom)));
        }
    }
    private record Fill(int left, int top, int right, int bottom, int color) { }
    private record TextDraw(String value, int x, int y, int color, Clip clip) { }

    private static final class Painter extends GuiGraphicsExtractor {
        ArrayDeque<Clip> clips;
        List<Fill> fills;
        List<Integer> outlines;
        List<TextDraw> texts;
        int pushes;
        int pops;

        private Painter() { super(null, null, 0, 0); }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) {
            Clip next = new Clip(left, top, right, bottom);
            clips.push(clips.isEmpty() ? next : clips.peek().intersect(next));
            pushes++;
        }

        @Override
        public void disableScissor() { clips.pop(); pops++; }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            Clip raw = new Clip(left, top, right, bottom);
            Clip clipped = clips.isEmpty() ? raw : clips.peek().intersect(raw);
            fills.add(new Fill(clipped.left, clipped.top, clipped.right, clipped.bottom, color));
        }

        @Override
        public void outline(int left, int top, int right, int bottom, int color) { outlines.add(color); }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            texts.add(new TextDraw(text, x, y, color, clips.peek()));
        }

        @Override
        public void centeredText(Font font, String text, int x, int y, int color) {
            texts.add(new TextDraw(text, x, y, color, clips.peek()));
        }
    }

    private static <ValueType> ValueType allocate(Class<ValueType> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }
}
//?}
