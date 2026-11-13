//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeInputFrameRegressionTest {
    @ParameterizedTest(name = "{0}: padding={1}, focused={2}")
    @CsvSource({
            "input, 0, false",
            "input, 12, false",
            "input, 12, true",
            "button, 12, false",
            "slider, 12, false",
            "image, 12, false"
    })
    void canonicalControlsKeepAllFourFrameSidesVisible(String type, int padding, boolean focused) throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            installControl(fixture, type, padding);
            JsxHostTree.Node control = fixture.adapter.roots().getFirst().children.getFirst();
            assertEquals(padding, control.x);
            assertEquals(padding, control.y);
            assertEquals(60, control.width);
            assertEquals(20, control.height);
            if (focused) {
                assertTrue(fixture.adapter.dispatchAt(control.x + 6, control.y + 6, "click", 0, 0));
                assertTrue(control.focused);
            }
            RecordingGraphics graphics = graphics();
            fixture.screen.extractRenderState(graphics, -1, -1, 0);
            int color = focused ? 0xFFFFFFFF : "button".equals(type) ? 0xFF9AA7B5 : 0xFF707780;
            assertFrame(graphics, control.x, control.y, control.width, control.height, color);
        }
    }

    @Test
    void translatedRegionKeepsTheFrameAndRestoresTheCallersPoseAndClip() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            setField(JsxHostAdapter.class, fixture.adapter, "regionMount", true);
            setField(JsxHostAdapter.class, fixture.adapter, "screen", null);
            fixture.adapter.setRegion(30, 40, 100, 100);
            installControl(fixture, "input", 12);
            RecordingGraphics graphics = graphics();
            graphics.pose().translate(7, 9);
            graphics.enableScissor(0, 0, 150, 150);
            Matrix3x2f before = new Matrix3x2f(graphics.pose());
            fixture.adapter.render(graphics, -1, -1);
            assertEquals(before, new Matrix3x2f(graphics.pose()));
            assertTrue(graphics.containsPointInScissor(10, 12), "native clips must be popped back to the caller clip");
            assertFalse(graphics.containsPointInScissor(160, 160), "the caller clip must remain active");
            assertFrame(graphics, 49, 61, 60, 20, 0xFF707780);
            graphics.disableScissor();
            assertTrue(graphics.containsPointInScissor(160, 160));
        }
    }

    @Test
    void cornerResizePreservesControlledCaptureAndFocusedInputFrames() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.eval("js", """
                    globalThis.rect = {x: 12, y: 12, width: 90, height: 100};
                    globalThis.resizeEvents = [];
                    globalThis.root = UI.createRoot(() => UI.element('workspace', {
                      children: UI.element('window', {
                        id: 'notes', title: 'Notes', active: true, ...rect,
                        titlebarHeight: 24, titlebarButtonWidth: 18, titlebarTitle: false, resizable: true,
                        onResize: event => {
                          resizeEvents.push({x: event.x, y: event.y, width: event.width, height: event.height, edge: event.edge});
                          rect = {x: event.x, y: event.y, width: event.width, height: event.height};
                          if (root.refresh() !== true) throw new Error('Controlled resize must publish');
                        },
                        children: UI.element('column', {width: 'fill', padding: 6,
                          children: UI.element('input', {id: 'notes-text', width: 'fill', height: 20, value: 'Draft'})
                        })
                      })
                    }), host, {id: 'native-corner-resize', viewport: {width: 240, height: 200}});
                    host.bindRoot(root);
                    """);
            assertTrue(fixture.adapter.dispatchAt(24, 48, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchRelease(24, 48, 0));
            assertTrue(fixture.adapter.dispatchAt(101, 111, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchDrag(121, 126, 0), "the native corner must capture outside the original window");
            assertEquals(1, fixture.context.eval("js", "resizeEvents.length").asInt());
            assertEquals("se", fixture.context.eval("js", "resizeEvents[0].edge").asString());
            assertEquals(12d, fixture.context.eval("js", "resizeEvents[0].x").asDouble());
            assertEquals(12d, fixture.context.eval("js", "resizeEvents[0].y").asDouble());
            assertEquals(110d, fixture.context.eval("js", "resizeEvents[0].width").asDouble());
            assertEquals(115d, fixture.context.eval("js", "resizeEvents[0].height").asDouble());
            RecordingGraphics first = graphics();
            fixture.screen.extractRenderState(first, -1, -1, 0);
            assertFrame(first, 18, 42, 98, 20, 0xFFFFFFFF);
            assertEquals(Integer.valueOf(0xFF3A536F), first.pixels.get(pixel(15, 34)),
                    "the configured titlebar must paint through its effective height");

            assertTrue(fixture.adapter.dispatchDrag(141, 146, 0), "controlled publication must retain the original corner capture");
            assertEquals(2, fixture.context.eval("js", "resizeEvents.length").asInt());
            assertEquals(130d, fixture.context.eval("js", "resizeEvents[1].width").asDouble());
            assertEquals(135d, fixture.context.eval("js", "resizeEvents[1].height").asDouble());
            RecordingGraphics second = graphics();
            fixture.screen.extractRenderState(second, -1, -1, 0);
            assertFrame(second, 18, 42, 118, 20, 0xFFFFFFFF);
            assertTrue(fixture.adapter.dispatchRelease(141, 146, 0));
            assertFalse(fixture.adapter.dispatchDrag(151, 156, 0), "release must end the corner capture");
        }
    }

    @Test
    void canonicalTitlebarButtonsMaximizeRestoreAndMinimizeRetainedInput() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.eval("js", """
                    globalThis.__nekoNodeBuiltins = {'nekojs/jsx-runtime': {UI}};
                    globalThis.__nekoNodeDefine = (ids, exports) => {
                      for (const id of ids) globalThis.__nekoNodeBuiltins[id] = exports;
                    };
                    """);
            try (var source = getClass().getResourceAsStream("/nekojs/node/modules/jsx-workspace.ts")) {
                assertNotNull(source, "Canonical workspace runtime must be present");
                fixture.context.eval("js", com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler.eraseTypescript(
                        Path.of("jsx-workspace.ts"), new String(source.readAllBytes(), StandardCharsets.UTF_8)));
            }
            fixture.context.eval("js", """
                    globalThis.text = UI.createSignal('Draft');
                    globalThis.workspace = __nekoNodeBuiltins['nekojs/jsx-workspace'].createWorkspace(host,
                      {id: 'native-titlebar-actions', viewport: {width: 240, height: 200}});
                    if (workspace.add('notes', () => UI.element('column', {width: 'fill', padding: 6,
                      children: UI.element('input', {id: 'notes-text', width: 'fill', height: 20,
                        value: text.get(), onChange: event => text.set(event.value)})
                    }), {title: 'Notes', rect: {x: 12, y: 12, width: 90, height: 100},
                      titlebarHeight: 24, titlebarButtonWidth: 18, titlebarTitle: false,
                      minimizable: true, maximizable: true, resizable: true}) !== true) {
                      throw new Error('Canonical workspace window must publish before chrome input');
                    }
                    """);
            long inputIdentity = fixture.adapter.roots().getFirst().children.getFirst().children.getFirst().children.getFirst().identity;
            assertTrue(fixture.adapter.dispatchAt(24, 48, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchRelease(24, 48, 0));
            RecordingGraphics normal = graphics();
            fixture.screen.extractRenderState(normal, -1, -1, 0);
            assertFrame(normal, 18, 42, 78, 20, 0xFFFFFFFF);

            // Right-to-left slots are close, maximize/restore, then minimize; each slot is 18 GUI pixels.
            assertTrue(fixture.adapter.dispatchAt(75, 24, "click", 0, 0));
            fixture.adapter.dispatchRelease(75, 24, 0);
            assertEquals("maximized", fixture.context.eval("js", "workspace.snapshot().windows[0].mode").asString());
            JsxHostTree.Node maximized = fixture.adapter.roots().getFirst().children.getFirst();
            assertEquals(0, maximized.x);
            assertEquals(0, maximized.y);
            assertEquals(240, maximized.width);
            assertEquals(200, maximized.height);
            RecordingGraphics full = graphics();
            fixture.screen.extractRenderState(full, -1, -1, 0);
            assertFrame(full, 6, 30, 228, 20, 0xFFFFFFFF);

            assertTrue(fixture.adapter.dispatchAt(213, 12, "click", 0, 0));
            fixture.adapter.dispatchRelease(213, 12, 0);
            assertEquals("normal", fixture.context.eval("js", "workspace.snapshot().windows[0].mode").asString());
            RecordingGraphics restored = graphics();
            fixture.screen.extractRenderState(restored, -1, -1, 0);
            assertFrame(restored, 18, 42, 78, 20, 0xFFFFFFFF);

            assertTrue(fixture.adapter.dispatchAt(57, 24, "click", 0, 0));
            fixture.adapter.dispatchRelease(57, 24, 0);
            assertEquals("minimized", fixture.context.eval("js", "workspace.snapshot().windows[0].mode").asString());
            assertFalse(fixture.adapter.dispatchAt(24, 48, "click", 0, 0), "minimized content must not receive input");
            assertTrue(fixture.context.eval("js", "text.set('Saved while minimized')").asBoolean());
            JsxHostTree.Node retained = fixture.adapter.roots().getFirst().children.getFirst().children.getFirst().children.getFirst();
            assertEquals(inputIdentity, retained.identity, "minimize must retain the keyed native input");
            assertEquals("Saved while minimized", retained.inputValue, "minimized content must retain its signal subscription");
            assertTrue(fixture.context.eval("js", "workspace.restore('notes')").asBoolean());
            assertEquals("normal", fixture.context.eval("js", "workspace.snapshot().windows[0].mode").asString());
            assertEquals(inputIdentity, fixture.adapter.roots().getFirst().children.getFirst().children.getFirst().children.getFirst().identity);
            assertTrue(fixture.adapter.dispatchAt(24, 48, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchRelease(24, 48, 0));
            RecordingGraphics finalFrame = graphics();
            fixture.screen.extractRenderState(finalFrame, -1, -1, 0);
            assertFrame(finalFrame, 18, 42, 78, 20, 0xFFFFFFFF);
        }
    }

    @Test
    void maximizedPublicationFencesResizeCaptureBeforeReturningToNormal() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.eval("js", """
                    globalThis.windowMode = 'normal';
                    globalThis.resizeEvents = [];
                    globalThis.root = UI.createRoot(() => UI.element('workspace', {
                      children: UI.element('window', {
                        id: 'notes', title: 'Notes', active: true, mode: windowMode,
                        ...(windowMode === 'maximized' ? {x: 0, y: 0, width: 240, height: 200}
                          : {x: 12, y: 12, width: 90, height: 100}),
                        titlebarHeight: 24, resizable: true,
                        onResize: event => resizeEvents.push({edge: event.edge, width: event.width, height: event.height}),
                        children: UI.element('column', {width: 'fill', padding: 6,
                          children: UI.element('input', {id: 'notes-text', width: 'fill', height: 20, value: 'Draft'})
                        })
                      })
                    }), host, {id: 'native-mode-capture-fence', viewport: {width: 240, height: 200}});
                    host.bindRoot(root);
                    """);
            assertTrue(fixture.adapter.dispatchAt(24, 48, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchRelease(24, 48, 0));
            assertTrue(fixture.adapter.dispatchAt(101, 111, "click", 0, 0));
            assertTrue(fixture.context.eval("js", "windowMode = 'maximized'; root.refresh()").asBoolean());
            assertEquals("maximized", fixture.adapter.roots().getFirst().children.getFirst().props.get("mode"));
            assertTrue(fixture.context.eval("js", "windowMode = 'normal'; root.refresh()").asBoolean());
            assertEquals("normal", fixture.adapter.roots().getFirst().children.getFirst().props.get("mode"));

            boolean dragged = fixture.adapter.dispatchDrag(121, 126, 0);
            RecordingGraphics frame = graphics();
            fixture.screen.extractRenderState(frame, -1, -1, 0);
            assertAll("publication must fence window capture without clearing focused child input",
                    () -> assertFalse(dragged, "a maximized publication must cancel the previous normal resize capture"),
                    () -> assertEquals(0, fixture.context.eval("js", "resizeEvents.length").asInt()),
                    () -> assertFrame(frame, 18, 42, 78, 20, 0xFFFFFFFF),
                    () -> assertFalse(fixture.adapter.dispatchRelease(121, 126, 0), "fenced capture must not own the release"));
        }
    }

    @Test
    void fractionalPositionAndSizeUseSharedNearestPixelBoundaries() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.root = UI.createRoot(() => UI.element('screen', {padding: 12.75,
                      children: UI.element('input', {id: 'fractional-frame', width: 60.5, height: 20.5, value: 'Draft'})
                    }), host, {id: 'fractional-boundaries', viewport: {width: 100, height: 100}});
                    host.bindRoot(root);
                    """);
            RecordingGraphics graphics = graphics();
            fixture.screen.extractRenderState(graphics, -1, -1, 0);
            // Native pixels use nearest rectangle edges, not independently rounded origin and size.
            // Logical [12.75, 73.25) x [12.75, 33.25) becomes pixel [13, 73) x [13, 33).
            assertFrame(graphics, 13, 13, 60, 20, 0xFF707780);
        }
    }

    @Test
    void controlledWorkspaceDragKeepsInputFramesVisibleAcrossPublishedFractionalPositions() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.adapter.resize(240, 200);
            fixture.context.eval("js", """
                    globalThis.__nekoNodeBuiltins = {'nekojs/jsx-runtime': {UI}};
                    globalThis.__nekoNodeDefine = (ids, exports) => {
                      for (const id of ids) globalThis.__nekoNodeBuiltins[id] = exports;
                    };
                    """);
            try (var input = getClass().getResourceAsStream("/nekojs/node/modules/jsx-workspace.ts")) {
                assertNotNull(input, "Canonical workspace runtime must be present");
                String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                fixture.context.eval("js", com.tkisor.nekojs.core.compiler.NekoTypeScriptCompiler.eraseTypescript(
                        Path.of("jsx-workspace.ts"), source));
            }
            fixture.context.eval("js", """
                    globalThis.workspace = __nekoNodeBuiltins['nekojs/jsx-workspace'].createWorkspace(host,
                      {id: 'drag-frame', viewport: {width: 240, height: 200}});
                    if (workspace.add('notes', () => UI.element('scroll', {width: 'fill', height: 'fill',
                      children: UI.element('column', {width: 'fill', padding: 6, gap: 6, children: [
                        UI.element('input', {id: 'notes-text', width: 60, height: 20, value: 'Draft'}),
                        UI.element('button', {id: 'blur', width: 60, height: 20, text: 'Blur'})
                      ]})
                    }), {title: 'Notes', rect: {x: 12, y: 12, width: 90, height: 100}}) !== true) {
                      throw new Error('Canonical workspace window must publish before dragging');
                    }
                    """);
            List<Executable> frames = new ArrayList<>();
            RecordingGraphics unfocused = graphics();
            fixture.screen.extractRenderState(unfocused, -1, -1, 0);
            frames.add(() -> assertFrame(unfocused, 18, 38, 60, 20, 0xFF707780));
            assertTrue(fixture.adapter.dispatchAt(24, 44, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchRelease(24, 44, 0));
            RecordingGraphics focused = graphics();
            fixture.screen.extractRenderState(focused, -1, -1, 0);
            frames.add(() -> assertFrame(focused, 18, 38, 60, 20, 0xFFFFFFFF));

            assertTrue(fixture.adapter.dispatchAt(32, 17, "click", 0, 0), "the native titlebar must capture the drag");
            record DragFrame(double mouseX, double mouseY, double requestedX, double requestedY, int inputX, int inputY) { }
            // Worked positions: titlebar grab offset (20, 5), content inset 20, column padding 6.
            List<DragFrame> movements = List.of(
                    new DragFrame(44, 25, 24, 20, 30, 46),
                    new DragFrame(44.25, 25.5, 24.25, 20.5, 30, 47),
                    new DragFrame(49.75, 29.25, 29.75, 24.25, 36, 50),
                    new DragFrame(55.5, 37.75, 35.5, 32.75, 42, 59),
                    new DragFrame(64, 45, 44, 40, 50, 66));
            for (DragFrame movement : movements) {
                assertTrue(fixture.adapter.dispatchDrag(movement.mouseX(), movement.mouseY(), 0));
                assertEquals(movement.requestedX(), fixture.context.eval("js", "workspace.snapshot().windows[0].rect.x").asDouble());
                assertEquals(movement.requestedY(), fixture.context.eval("js", "workspace.snapshot().windows[0].rect.y").asDouble());
                for (int repaint = 0; repaint < 2; repaint++) {
                    RecordingGraphics frame = graphics();
                    fixture.screen.extractRenderState(frame, -1, -1, 0);
                    frames.add(() -> assertFrame(frame, movement.inputX(), movement.inputY(), 60, 20, 0xFFFFFFFF));
                }
            }
            assertTrue(fixture.adapter.dispatchRelease(64, 45, 0));
            assertFalse(fixture.adapter.dispatchDrag(70, 50, 0), "release must end the native titlebar capture");
            assertTrue(fixture.adapter.dispatchAt(56, 98, "click", 0, 0));
            assertTrue(fixture.adapter.dispatchRelease(56, 98, 0));
            RecordingGraphics blurred = graphics();
            fixture.screen.extractRenderState(blurred, -1, -1, 0);
            frames.add(() -> assertFrame(blurred, 50, 66, 60, 20, 0xFF707780));
            assertAll("input borders must survive every native drag frame and repaint", frames);
        }
    }

    private static void installControl(NativeUiScreenFixture fixture, String type, int padding) throws Exception {
        fixture.installRuntime();
        fixture.context.getBindings("js").putMember("controlType", type);
        fixture.context.getBindings("js").putMember("controlPadding", padding);
        fixture.context.eval("js", """
                globalThis.root = UI.createRoot(() => UI.element('column', {
                  width: 100, height: 100, padding: controlPadding,
                  children: UI.element(controlType, {
                    id: 'frame', width: 60, height: 20,
                    ...(controlType === 'input' ? {value: 'Draft'} : {}),
                    ...(controlType === 'slider' ? {min: 0, max: 1, value: 0.5} : {})
                  })
                }), host, {id: 'native-frame', viewport: {width: 100, height: 100}});
                host.bindRoot(root);
                """);
    }

    private static void assertFrame(RecordingGraphics graphics, int x, int y, int width, int height, int color) {
        assertTrue(graphics.fillCount > 0, "the actual native paint path must submit fills");
        assertAll("every frame side must survive actual extractor scissoring",
                () -> assertHorizontal(graphics, x, y, width, color, "top"),
                () -> assertHorizontal(graphics, x, y + height - 1, width, color, "bottom"),
                () -> assertVertical(graphics, x, y, height, color, "left"),
                () -> assertVertical(graphics, x + width - 1, y, height, color, "right"));
    }

    private static void assertHorizontal(RecordingGraphics graphics, int x, int y, int width, int color, String side) {
        for (int offset = 0; offset < width; offset++) {
            assertEquals(Integer.valueOf(color), graphics.pixels.get(pixel(x + offset, y)),
                    side + " border must be visible at " + (x + offset) + "," + y);
        }
    }

    private static void assertVertical(RecordingGraphics graphics, int x, int y, int height, int color, String side) {
        for (int offset = 0; offset < height; offset++) {
            assertEquals(Integer.valueOf(color), graphics.pixels.get(pixel(x, y + offset)),
                    side + " border must be visible at " + x + "," + (y + offset));
        }
    }

    private static long pixel(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    private static RecordingGraphics graphics() throws Exception {
        // Bypass atlas/GPU construction only; outline, pose and nested scissor behavior remain Minecraft's own.
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        RecordingGraphics graphics = (RecordingGraphics) ((Unsafe) unsafeField.get(null))
                .allocateInstance(RecordingGraphics.class);
        graphics.pixels = new HashMap<>();
        setField(GuiGraphicsExtractor.class, graphics, "pose", new Matrix3x2fStack(16));
        Field scissorField = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
        Constructor<?> constructor;
        Object[] arguments;
        try {
            // 26.2 seeds the real stack with the caller GUI extent; 26.1 has an empty-stack constructor.
            constructor = scissorField.getType().getDeclaredConstructor(ScreenRectangle.class);
            arguments = new Object[] { new ScreenRectangle(0, 0, 256, 256) };
        } catch (NoSuchMethodException olderExtractor) {
            constructor = scissorField.getType().getDeclaredConstructor();
            arguments = new Object[0];
        }
        constructor.setAccessible(true);
        scissorField.setAccessible(true);
        scissorField.set(graphics, constructor.newInstance(arguments));
        assertEquals(GuiGraphicsExtractor.class, RecordingGraphics.class
                .getMethod("outline", int.class, int.class, int.class, int.class, int.class).getDeclaringClass());
        return graphics;
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private Map<Long, Integer> pixels;
        private int fillCount;

        private RecordingGraphics() { super(null, null, 0, 0); }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int color) {
            fillCount++;
            ScreenRectangle scissor = currentFillScissor();
            // These fixtures use integer translations. Clip at command submission, preserving paint order.
            Vector2f start = pose().transformPosition(x0, y0, new Vector2f());
            Vector2f end = pose().transformPosition(x1, y1, new Vector2f());
            int left = Math.max(0, (int) Math.min(start.x, end.x));
            int right = Math.min(256, (int) Math.max(start.x, end.x));
            int top = Math.max(0, (int) Math.min(start.y, end.y));
            int bottom = Math.min(256, (int) Math.max(start.y, end.y));
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    if (scissor == null || scissor.containsPoint(x, y)) pixels.put(pixel(x, y), color);
                }
            }
        }

        private ScreenRectangle currentFillScissor() {
            // Real innerFill attaches the innermost ScissorStack.peek() intersection.
            // Public containsPointInScissor checks the outermost entry, not the fill command clip.
            try {
                Field field = GuiGraphicsExtractor.class.getDeclaredField("scissorStack");
                field.setAccessible(true);
                Object stack = field.get(this);
                var peek = stack.getClass().getDeclaredMethod("peek");
                peek.setAccessible(true);
                return (ScreenRectangle) peek.invoke(stack);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Could not read the native fill command scissor", failure);
            }
        }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) { }

        @Override
        public void text(Font font, Component text, int x, int y, int color, boolean shadow) { }
    }
}
//?}
