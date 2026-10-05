//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.wrapper.client.McFontAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket41MouseSelectionTest {
    @Test
    void clickingBetweenUnequalGlyphsPlacesTheCaretAtThePaintedBoundary() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            assertTrue(fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WXiZ", fixture.paint().text);
            assertTrue(fixture.paint().fills.contains(new Fill(30, 24, 31, 40, 0xFFFFFFFF)));
        }
    }

    @Test
    void draggingOutsideTheInputExtendsSelectionUntilTheMatchingButtonIsReleased() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("Wi😀Z")) {
            assertTrue(fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false));
            assertTrue(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertTrue(fixture.paint().fills.contains(new Fill(24, 24, 40, 40, 0xFF4A6A95)));
            assertTrue(fixture.screen.mouseReleased(mouse(200, 100, 0, 0)));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WX", fixture.paint().text);
        }
    }

    @Test
    void disablingThenReenablingTheInputCancelsThePreviousDrag() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            assertTrue(fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false));
            fixture.updateInput(Map.of("value", "WiZ", "disabled", true));
            fixture.updateInput(Map.of("value", "WiZ", "disabled", false));
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertFalse(fixture.screen.mouseReleased(mouse(200, 100, 0, 0)));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WXiZ", fixture.paint().text);
        }
    }

    @Test
    void shiftClickExtendsTheAnchorAndKeyboardSelectionStaysOnCodePointBoundaries() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("Wi😀Z")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.screen.mouseReleased(mouse(24.25, 25, 0, 0));
            fixture.screen.mouseClicked(mouse(33.9, 25, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT), false);
            assertTrue(fixture.paint().fills.contains(new Fill(24, 24, 34, 40, 0xFF4A6A95)));
            assertTrue(fixture.screen.keyPressed(new net.minecraft.client.input.KeyEvent(
                    org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0, org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT)));
            assertTrue(fixture.paint().fills.contains(new Fill(24, 24, 26, 40, 0xFF4A6A95)));
            fixture.screen.charTyped(new CharacterEvent('X'));
            assertEquals("WX😀Z", fixture.paint().text);
        }
    }

    @Test
    void clickingInsideASupplementaryGlyphNeverSplitsItsSurrogatePair() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("Wi😀Z")) {
            fixture.screen.mouseClicked(mouse(29.9, 25, 0, 0), false);
            fixture.screen.charTyped(new CharacterEvent('X'));
            assertEquals("WiX😀Z", fixture.paint().text);
        }
    }

    @Test
    void secondaryMouseEventsCannotStealOrReleaseAPrimarySelectionGesture() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            assertFalse(fixture.screen.mouseClicked(mouse(24.25, 25, 1, 0), false));
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 1, 0), 175.75, 75));
            assertFalse(fixture.screen.mouseReleased(mouse(200, 100, 1, 0)));
            assertTrue(fixture.screen.mouseDragged(mouse(-20, 100, 0, 0), -44.25, 75));
            assertTrue(fixture.paint().fills.contains(new Fill(14, 24, 24, 40, 0xFF4A6A95)));
            assertTrue(fixture.screen.mouseReleased(mouse(-20, 100, 0, 0)));
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 220, 0));
            fixture.screen.charTyped(new CharacterEvent('X'));
            assertEquals("XiZ", fixture.paint().text);
        }
    }

    @Test
    void retainedInputKeepsItsDragAcrossReconciliation() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.updateInput(Map.of("value", "WiZ", "id", "field", "placeholder", "updated"));
            assertTrue(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            fixture.screen.charTyped(new CharacterEvent('X'));
            assertEquals("WX", fixture.paint().text);
        }
    }

    @Test
    void replacementWithTheSameScriptIdCannotInheritTheOldSelectionCapture() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.replaceInput("replacement");
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertFalse(fixture.screen.mouseReleased(mouse(200, 100, 0, 0)));
            assertFalse(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("replacement", fixture.paint().text);
        }
    }

    @Test
    void matchingReleaseUsesItsFinalPointerPositionForSelection() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.screen.mouseDragged(mouse(26, 25, 0, 0), 1.75, 0);
            fixture.screen.mouseReleased(mouse(200, 100, 0, 0));
            fixture.screen.charTyped(new CharacterEvent('X'));
            assertEquals("WX", fixture.paint().text);
        }
    }

    @Test
    void screenRemovalDropsTheCapturedGestureAndStopsFurtherInput() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.screen.removed();
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertFalse(fixture.screen.mouseReleased(mouse(200, 100, 0, 0)));
            assertFalse(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals(null, fixture.paint().text);
        }
    }

    @Test
    void anInvalidatedGenerationCannotContinueACapturedDrag() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.globals.close();
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertTrue(failure.getMessage().contains("NEKO-7001"));
            assertEquals(null, fixture.paint().text);
        }
    }

    @Test
    void focusDrivenReplacementCannotReceiveTheOldInputsClick() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            java.util.concurrent.atomic.AtomicInteger clicks = new java.util.concurrent.atomic.AtomicInteger();
            fixture.onEvent(event -> {
                if ("focus".equals(event)) fixture.replaceInput("replacement");
                if ("click".equals(event)) clicks.incrementAndGet();
            });
            assertTrue(fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false));
            assertEquals(0, clicks.get());
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertEquals("replacement", fixture.paint().text);
        }
    }

    @Test
    void blurReconciliationKeepsTheClickedRetainedInputFocused() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.addSecondInput("WiZ");
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.screen.mouseReleased(mouse(24.25, 25, 0, 0));
            fixture.onEvent((id, event) -> {
                if ("field".equals(id) && "blur".equals(event)) fixture.refreshInputs();
            });
            assertTrue(fixture.screen.mouseClicked(mouse(24.25, 65, 0, 0), false));
            assertTrue(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 35));
            fixture.screen.mouseReleased(mouse(200, 100, 0, 0));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WX", fixture.paint().text);
        }
    }

    @Test
    void blurReplacementCannotReceiveFocusForTheRemovedInput() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            Object second = fixture.addSecondInput("WiZ");
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.screen.mouseReleased(mouse(24.25, 25, 0, 0));
            java.util.concurrent.atomic.AtomicInteger replacementFocus = new java.util.concurrent.atomic.AtomicInteger();
            fixture.onEvent((id, event) -> {
                if ("field".equals(id) && "blur".equals(event)) fixture.replaceSecondInput(second, "Replacement");
                if ("second".equals(id) && "focus".equals(event)) replacementFocus.incrementAndGet();
            });
            assertTrue(fixture.screen.mouseClicked(mouse(24.25, 65, 0, 0), false));
            assertEquals(0, replacementFocus.get());
            assertFalse(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("Replacement", fixture.paint().text);
        }
    }

    @Test
    void tabTraversalSkipsHiddenInputs() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.addSecondInput("WiZ");
            fixture.hideFirstInput();
            assertTrue(fixture.screen.keyPressed(new net.minecraft.client.input.KeyEvent(
                    org.lwjgl.glfw.GLFW.GLFW_KEY_TAB, 0, 0)));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WiZX", fixture.paint().text);
        }
    }

    @Test
    void textFitsAnIntrinsicallySizedButtonWithoutAFixedVerticalOffset() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.replaceWithShortButton();
            RecordingGraphics graphics = fixture.paint();
            assertEquals("Reset", graphics.text);
            assertEquals(20, graphics.textY);
        }
    }

    @Test
    void hidingThenShowingAnAncestorCancelsTheDescendantsCapturedDrag() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            Object parent = fixture.wrapInput();
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            fixture.setParentVisible(parent, false);
            fixture.setParentVisible(parent, true);
            assertFalse(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertFalse(fixture.screen.mouseReleased(mouse(200, 100, 0, 0)));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WXiZ", fixture.paint().text);
        }
    }

    @Test
    void offOwnerThreadNativeInputsRejectBeforeMutatingTheCapturedField() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ");
             var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
            fixture.onEvent(event -> callbacks.incrementAndGet());
            List<java.util.concurrent.Callable<Boolean>> inputs = List.of(
                    () -> fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false),
                    () -> fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75),
                    () -> fixture.screen.mouseReleased(mouse(200, 100, 0, 0)),
                    () -> fixture.screen.charTyped(new CharacterEvent('Q')),
                    () -> fixture.screen.keyPressed(new net.minecraft.client.input.KeyEvent(
                            org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, 0)),
                    () -> fixture.screen.mouseScrolled(24.25, 25, 0, -1));
            for (java.util.concurrent.Callable<Boolean> inputEvent : inputs) {
                java.util.concurrent.ExecutionException rejected = assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> worker.submit(inputEvent).get(5, java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(rejected.getCause() instanceof IllegalStateException);
                assertTrue(rejected.getCause().getMessage().contains("NEKO-7004"));
            }
            assertEquals(0, callbacks.get());
            assertEquals("WiZ", fixture.paint().text);
            assertTrue(fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75));
            assertTrue(fixture.screen.mouseReleased(mouse(200, 100, 0, 0)));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("WX", fixture.paint().text);
        }
    }

    @Test
    void closedGenerationRejectsEveryNativeInputWithoutInvokingOldCallbacks() throws Exception {
        try (ScreenFixture fixture = new ScreenFixture("WiZ")) {
            fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false);
            java.util.concurrent.atomic.AtomicInteger callbacks = new java.util.concurrent.atomic.AtomicInteger();
            fixture.onEvent(event -> callbacks.incrementAndGet());
            fixture.globals.close();
            List<java.util.concurrent.Callable<Boolean>> inputs = List.of(
                    () -> fixture.screen.mouseClicked(mouse(24.25, 25, 0, 0), false),
                    () -> fixture.screen.mouseDragged(mouse(200, 100, 0, 0), 175.75, 75),
                    () -> fixture.screen.mouseReleased(mouse(200, 100, 0, 0)),
                    () -> fixture.screen.charTyped(new CharacterEvent('Q')),
                    () -> fixture.screen.keyPressed(new net.minecraft.client.input.KeyEvent(
                            org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE, 0, 0)),
                    () -> fixture.screen.mouseScrolled(24.25, 25, 0, -1));
            for (java.util.concurrent.Callable<Boolean> inputEvent : inputs) {
                IllegalStateException rejected = assertThrows(IllegalStateException.class, inputEvent::call);
                assertTrue(rejected.getMessage().contains("NEKO-7001"));
            }
            assertEquals(0, callbacks.get());
            assertEquals(null, fixture.paint().text);
        }
    }

    private static MouseButtonEvent mouse(double x, double y, int button, int modifiers) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
    }

    private record Fill(int left, int top, int right, int bottom, int color) { }

    private static final class RecordingFont extends Font {
        private RecordingFont() { super(null); }

        @Override
        public String plainSubstrByWidth(String text, int maximumWidth) {
            int width = 0;
            int index = 0;
            while (index < text.length()) {
                int next = text.offsetByCodePoints(index, 1);
                int glyphWidth = width(text.substring(index, next));
                if (width + glyphWidth > maximumWidth) break;
                width += glyphWidth;
                index = next;
            }
            return text.substring(0, index);
        }

        @Override
        public int width(String text) {
            return text.codePoints().map(codepoint -> switch (codepoint) {
                case 'W' -> 10;
                case 'i' -> 2;
                case 0x1F600 -> 8;
                default -> 6;
            }).sum();
        }
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<Fill> fills;
        private String text;
        private int textY;

        private RecordingGraphics() { super(null, null, 0, 0); }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new Fill(left, top, right, bottom, color));
        }

        @Override
        public void outline(int left, int top, int right, int bottom, int color) { }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            this.text = text;
            this.textY = y;
        }

        @Override
        public void centeredText(Font font, String text, int x, int y, int color) {
            this.text = text;
            this.textY = y;
        }
    }

    private static final class ScreenFixture implements AutoCloseable {
        private final Minecraft previous;
        private final Field clientField;
        private final JsxHostTree tree = new JsxHostTree();
        private final com.tkisor.nekojs.core.state.GenerationGlobals globals =
                new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        private final JsxScreen screen;
        private final JsxHostAdapter adapter;
        private graal.graalvm.polyglot.Context callbackContext;
        private final Object input;

        private ScreenFixture(String value) throws Exception {
            clientField = Minecraft.class.getDeclaredField("instance");
            clientField.setAccessible(true);
            previous = (Minecraft) clientField.get(null);
            Minecraft minecraft = allocate(Minecraft.class);
            RecordingFont font = allocate(RecordingFont.class);
            Field lineHeight = Font.class.getDeclaredField("lineHeight");
            lineHeight.setAccessible(true);
            lineHeight.set(font, 9);
            setField(minecraft, "font", font);
            setField(minecraft, "gui", allocate(net.minecraft.client.gui.Gui.class));
            clientField.set(null, minecraft);
            adapter = allocate(JsxHostAdapter.class);
            setField(adapter, "minecraft", minecraft);
            setField(adapter, "ownerThread", Thread.currentThread());
            setField(adapter, "tree", tree);
            setField(adapter, "fontAdapter", new McFontAdapter(font));
            ScriptManager manager = new ScriptManager(ScriptType.CLIENT, null, null, null, null,
                    null, null, null, List.of(), null);
            setField(adapter, "manager", manager);
            setField(adapter, "globals", globals);
            setField(adapter, "lifecycle", new UiRootLifecycle(false, 0, globals));
            setField(adapter, "resources", new MinecraftUiResourceResolver(null, new UiTextureBackend() {
                @Override
                public Size upload(net.minecraft.resources.Identifier slot,
                        net.minecraft.resources.Identifier resource, byte[] bytes) {
                    throw new AssertionError("Mouse fixture must not upload textures");
                }

                @Override
                public void release(net.minecraft.resources.Identifier slot) {
                    throw new AssertionError("Mouse fixture owns no textures");
                }
            }, List::of));
            globals.registerUiRoot(adapter);
            screen = allocate(JsxScreen.class);
            setField(screen, "adapter", adapter);
            setField(adapter, "screen", screen);
            JsxHostTree.Transaction transaction = tree.begin();
            input = transaction.create("input", "field", Map.of("id", "field", "value", value));
            transaction.order(null, List.of(input));
            transaction.commit(List.of(input), nodes -> {
                JsxHostTree.Node node = nodes.getFirst();
                node.x = 10;
                node.y = 20;
                node.width = 100;
                node.height = 24;
            });
        }

        private Object wrapInput() {
            JsxHostTree.Transaction transaction = tree.begin();
            Object parent = transaction.create("column", "parent", Map.of("id", "parent", "visible", true));
            transaction.order(parent, List.of(input));
            transaction.order(null, List.of(parent));
            transaction.commit(List.of(parent), nodes -> {
                JsxHostTree.Node node = nodes.getFirst();
                node.x = 0;
                node.y = 0;
                node.width = 120;
                node.height = 50;
            });
            return parent;
        }

        private void setParentVisible(Object parent, boolean visible) {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.update(parent, "column", "parent", Map.of("id", "parent", "visible", visible));
            transaction.commit(tree.roots());
        }

        private Object addSecondInput(String value) {
            JsxHostTree.Transaction transaction = tree.begin();
            Object second = transaction.create("input", "second", Map.of("id", "second", "value", value));
            transaction.order(null, List.of(input, second));
            transaction.commit(List.of(input, second), ScreenFixture::positionSecond);
            return second;
        }

        private void hideFirstInput() {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.update(input, "input", "field", Map.of("id", "field", "value", "WiZ", "visible", false));
            transaction.commit(tree.roots());
        }

        private void refreshInputs() {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.commit(tree.roots());
        }

        private void replaceSecondInput(Object second, String value) {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.remove(second);
            Object replacement = transaction.create("input", "new-second", Map.of("id", "second", "value", value));
            transaction.order(null, List.of(input, replacement));
            transaction.commit(List.of(input, replacement), ScreenFixture::positionSecond);
        }

        private static void positionSecond(List<JsxHostTree.Node> nodes) {
            JsxHostTree.Node node = nodes.getLast();
            node.x = 10;
            node.y = 60;
            node.width = 100;
            node.height = 24;
        }

        private void onEvent(java.util.function.Consumer<String> callback) {
            onEvent((id, event) -> callback.accept(event));
        }

        private void onEvent(java.util.function.BiConsumer<String, String> callback) {
            callbackContext = graal.graalvm.polyglot.Context.create("js");
            graal.graalvm.polyglot.proxy.ProxyExecutable dispatch = arguments -> {
                callback.accept(arguments[0].asString(), arguments[1].asString());
                return true;
            };
            graal.graalvm.polyglot.proxy.ProxyExecutable close = arguments -> null;
            adapter.bindRoot(callbackContext.asValue(graal.graalvm.polyglot.proxy.ProxyObject.fromMap(
                    Map.of("id", "fixture", "dispatch", dispatch, "close", close))));
        }

        private void updateInput(Map<String, Object> props) {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.update(input, "input", "field", props);
            transaction.commit(List.of(input));
        }

        private void replaceWithShortButton() {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.remove(input);
            Object button = transaction.create("button", "reset", Map.of("id", "reset", "text", "Reset"));
            transaction.order(null, List.of(button));
            transaction.commit(List.of(button), nodes -> {
                JsxHostTree.Node node = nodes.getFirst();
                node.x = 10;
                node.y = 20;
                node.width = 100;
                node.height = 9;
            });
        }

        private void replaceInput(String value) {
            JsxHostTree.Transaction transaction = tree.begin();
            transaction.remove(input);
            Object replacement = transaction.create("input", "replacement", Map.of("id", "field", "value", value));
            transaction.order(null, List.of(replacement));
            transaction.commit(List.of(replacement), nodes -> {
                JsxHostTree.Node node = nodes.getFirst();
                node.x = 10;
                node.y = 20;
                node.width = 100;
                node.height = 24;
            });
        }

        private RecordingGraphics paint() throws Exception {
            RecordingGraphics graphics = allocate(RecordingGraphics.class);
            graphics.fills = new ArrayList<>();
            screen.extractRenderState(graphics, -1, -1, 0);
            return graphics;
        }

        @Override
        public void close() throws IllegalAccessException {
            try {
                globals.close();
            } finally {
                try {
                    if (callbackContext != null) callbackContext.close();
                } finally {
                    clientField.set(null, previous);
                }
            }
        }
    }

    private static <ValueType> ValueType allocate(Class<ValueType> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
//?}
