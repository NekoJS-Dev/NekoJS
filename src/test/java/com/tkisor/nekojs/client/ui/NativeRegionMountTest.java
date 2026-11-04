//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.bindings.static_access.ClientUiJS;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeRegionMountTest {
    @Test
    void factoryAndBoundsUseExplicitPositiveRegionExtents() throws Exception {
        assertEquals(JsxHostAdapter.class, ClientUiJS.class.getMethod("region", int.class, int.class,
                int.class, int.class).getReturnType());
        try (NativeUiScreenFixture fixture = region(30, 40, 60, 50)) {
            assertEquals(Map.of("width", 60, "height", 50), fixture.adapter.viewport());
            assertThrows(IllegalArgumentException.class, () -> invoke(fixture.adapter, "setRegion", 0, 0, 0, 50));
            assertEquals(Map.of("width", 60, "height", 50), fixture.adapter.viewport());
            invoke(fixture.adapter, "setRegion", 4, 5, 80, 70);
            assertEquals(Map.of("width", 80, "height", 70), fixture.adapter.viewport());
        }
    }

    @Test
    void renderTranslatesAndClipsWhileRestoringCallerStateAndTooltipCoordinates() throws Exception {
        try (NativeUiScreenFixture fixture = region(30, 40, 60, 50)) {
            fixture.commitLabel(Map.of("text", "Region", "tooltip", "Local tip"), 30, 20);
            RegionGraphics graphics = graphics();
            graphics.matrices.translate(7, 9);
            Matrix3x2f before = new Matrix3x2f(graphics.matrices);
            invoke(fixture.adapter, "render", graphics, 45, 65);
            assertFalse(graphics.texts.isEmpty());
            assertEquals(47, graphics.texts.get(0).x);
            assertEquals(69, graphics.texts.get(0).y);
            assertTrue(graphics.scissors.contains(new Rect(37, 49, 60, 50)), "region owns an outer transformed clip");
            assertEquals(before, new Matrix3x2f(graphics.matrices));
            assertTrue(graphics.clipStack.isEmpty(), "all nested scissors must be restored");
            assertEquals("Local tip", graphics.tooltip);
            assertEquals(List.of(45, 65), graphics.tooltipPosition, "deferred tooltip uses caller GUI coordinates");
            graphics.failText = true;
            assertThrows(IllegalStateException.class, () -> invoke(fixture.adapter, "render", graphics, 45, 65));
            assertEquals(before, new Matrix3x2f(graphics.matrices));
            assertTrue(graphics.clipStack.isEmpty(), "paint failure must also restore caller state");
        }
    }

    @Test
    void pointerCoordinatesFocusTabHandoffAndCaptureAreCooperative() throws Exception {
        try (NativeUiScreenFixture fixture = region(30, 40, 100, 100)) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.changes = [];
                    globalThis.root = UI.createRoot(() => UI.element('column', {children: [
                      UI.element('input', {id: 'input', width: 80, height: 20, value: '', onChange: e => changes.push(e.value)}),
                      UI.element('slider', {id: 'slider', width: 80, height: 20, min: 0, max: 10, value: 0, onChange: e => changes.push(e.value)})
                    ]}), host);
                    host.bindRoot(root);
                    """);
            assertFalse((boolean) invoke(fixture.adapter, "charTyped", (int) 'x'));
            assertTrue((boolean) invoke(fixture.adapter, "mouseClicked", 35d, 45d, 0, 0));
            assertTrue((boolean) invoke(fixture.adapter, "isFocused"));
            assertTrue((boolean) invoke(fixture.adapter, "charTyped", (int) 'x'));
            assertEquals("x", fixture.context.eval("js", "changes[0]").asString());
            assertTrue((boolean) invoke(fixture.adapter, "keyPressed", GLFW.GLFW_KEY_TAB, 0));
            assertFalse((boolean) invoke(fixture.adapter, "keyPressed", GLFW.GLFW_KEY_TAB, 0), "edge Tab hands keyboard focus to caller");
            assertFalse((boolean) invoke(fixture.adapter, "isFocused"));
            assertTrue((boolean) invoke(fixture.adapter, "mouseClicked", 35d, 65d, 0, 0));
            assertTrue((boolean) invoke(fixture.adapter, "mouseDragged", 500d, 65d, 0));
            assertTrue((boolean) invoke(fixture.adapter, "mouseReleased", 500d, 65d, 0));
            assertEquals(10d, fixture.context.eval("js", "Number(changes[changes.length - 1])").asDouble());
            assertFalse((boolean) invoke(fixture.adapter, "mouseClicked", 500d, 500d, 0, 0));
            assertFalse((boolean) invoke(fixture.adapter, "charTyped", (int) 'z'));
            invoke(fixture.adapter, "dispose");
            assertTrue(fixture.adapter.roots().isEmpty());
            assertFalse((boolean) invoke(fixture.adapter, "mouseClicked", 35d, 45d, 0, 0));
            assertFalse(fixture.screen.isDisposed(), "borrowed region must never remove a caller Screen");
        }
    }

    @Test
    void localModalDoesNotConsumeUncapturedPointerEventsOutsideTheBorrowedRegion() throws Exception {
        try (NativeUiScreenFixture fixture = region(30, 40, 100, 100)) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.root=UI.createRoot(() => UI.element(
                      'dialog',{id:'local-modal',children:UI.element('button',{id:'action',text:'Action',width:40,height:20})}),host);
                    host.bindRoot(root);
                    """);
            assertFalse((boolean) invoke(fixture.adapter, "mouseDragged", 500d, 500d, 0));
            assertFalse((boolean) invoke(fixture.adapter, "mouseReleased", 500d, 500d, 0));
            assertFalse((boolean) invoke(fixture.adapter, "isFocused"));
            assertEquals(1, fixture.adapter.roots().size(), "outside input must not dismiss the local modal");
        }
    }

    @Test
    void unhandledRegionPressDoesNotKeepCaptureAndStealTheCallersRelease() throws Exception {
        try (NativeUiScreenFixture fixture = region(30, 40, 100, 100)) {
            fixture.commitLabel(Map.of("text", "Passive label"), 80, 20);
            assertFalse((boolean) invoke(fixture.adapter, "mouseClicked", 35d, 45d, 0, 0));
            assertFalse((boolean) invoke(fixture.adapter, "mouseReleased", 500d, 500d, 0),
                    "the caller owns the release after an unhandled press");
            assertEquals(1, fixture.adapter.roots().size());
        }
    }

    private static NativeUiScreenFixture region(int x, int y, int width, int height) throws Exception {
        NativeUiScreenFixture fixture = new NativeUiScreenFixture();
        try {
            Field field = JsxHostAdapter.class.getDeclaredField("regionMount");
            field.setAccessible(true);
            field.setBoolean(fixture.adapter, true);
            field = JsxHostAdapter.class.getDeclaredField("screen");
            field.setAccessible(true);
            field.set(fixture.adapter, null);
            invoke(fixture.adapter, "setRegion", x, y, width, height);
            return fixture;
        } catch (Throwable failure) {
            fixture.close();
            throw failure;
        }
    }

    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int index = 0; index < types.length; index++) {
            Object value = arguments[index];
            types[index] = value instanceof Integer ? int.class : value instanceof Double ? double.class
                    : value instanceof Boolean ? boolean.class : value instanceof GuiGraphicsExtractor ? GuiGraphicsExtractor.class : value.getClass();
        }
        try {
            return target.getClass().getMethod(name, types).invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    private static RegionGraphics graphics() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        RegionGraphics graphics = (RegionGraphics) ((Unsafe) field.get(null)).allocateInstance(RegionGraphics.class);
        graphics.matrices = new Matrix3x2fStack(32);
        graphics.texts = new ArrayList<>();
        graphics.scissors = new ArrayList<>();
        graphics.clipStack = new ArrayDeque<>();
        return graphics;
    }

    private record Rect(int x, int y, int width, int height) { }
    private record Text(String value, int x, int y) { }

    private static final class RegionGraphics extends GuiGraphicsExtractor {
        Matrix3x2fStack matrices;
        List<Text> texts;
        List<Rect> scissors;
        ArrayDeque<Rect> clipStack;
        String tooltip;
        List<Integer> tooltipPosition;
        boolean failText;
        private RegionGraphics() { super(null, null, 0, 0); }
        @Override public Matrix3x2fStack pose() { return matrices; }
        @Override public void fill(int left, int top, int right, int bottom, int color) { }
        @Override public void outline(int left, int top, int right, int bottom, int color) { }
        @Override public void enableScissor(int left, int top, int right, int bottom) {
            Vector2f origin = matrices.transformPosition(new Vector2f(left, top));
            Rect rect = new Rect(Math.round(origin.x), Math.round(origin.y), right - left, bottom - top);
            scissors.add(rect);
            clipStack.push(rect);
        }
        @Override public void disableScissor() { clipStack.pop(); }
        @Override public void text(Font font, String text, int x, int y, int color, boolean shadow) { recordText(text, x, y); }
        @Override public void text(Font font, Component text, int x, int y, int color, boolean shadow) { recordText(text.getString(), x, y); }
        private void recordText(String text, int x, int y) {
            if (failText) throw new IllegalStateException("fixture paint failure");
            Vector2f point = matrices.transformPosition(new Vector2f(x, y));
            texts.add(new Text(text, Math.round(point.x), Math.round(point.y)));
        }
        @Override public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
            tooltip = text.getString();
            tooltipPosition = List.of(x, y);
        }
    }
}
//?}
