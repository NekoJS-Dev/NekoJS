//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.state.GlobalStateStores;
import com.tkisor.nekojs.script.ScriptManager;
import graal.graalvm.polyglot.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Ticket44NativeBoxPaintingTest {
    @Test
    void committedPanelPaintsRoundedThickBorderAndRespectsClipAndOpacity() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            fixture.commitPanel("#FF000001", "#FF000002", 4, 2, 0.5, false);
            List<Fill> fills = fixture.paint();
            assertEquals(0, colorAt(fills, 10, 20));
            assertEquals(0x80000002, colorAt(fills, 12, 20));
            assertEquals(0x80000002, colorAt(fills, 11, 23));
            assertEquals(0x80000001, colorAt(fills, 13, 23));
            fixture.commitPanel("#FF000001", "#FF000002", 4, 2, 0.5, true);
            fills = fixture.paint();
            assertEquals(0, colorAt(fills, 12, 20));
            assertEquals(0, colorAt(fills, 11, 23));
            assertEquals(0x80000001, colorAt(fills, 13, 23));
        }
    }

    @Test
    void transparentBorderShowsThePanelBackgroundRatherThanAHole() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            fixture.commitPanel("#FFFF0000", "#00000000", 0, 2, 1, false);
            assertEquals(0xFFFF0000, colorAt(fixture.paint(), 10, 20));
            assertEquals(0xFFFF0000, colorAt(fixture.paint(), 13, 23));
        }
    }

    @Test
    void translucentBorderComposesWithBackgroundBeforeElementOpacity() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            fixture.commitPanel("#FFFF0000", "#800000FF", 0, 2, 0.5, false);
            assertEquals(0x807F0080, colorAt(fixture.paint(), 10, 20));
            assertEquals(0x80FF0000, colorAt(fixture.paint(), 13, 23));
        }
    }

    @Test
    void roundedBorderMatchesLiteralPixelsWithoutOverlappingPaintCommands() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            fixture.commitPanel("#FF000001", "#FF000002", 4, 2, 1, false);
            List<Fill> fills = fixture.paint();
            StringBuilder pixels = new StringBuilder();
            for (int row = 20; row < 28; row++) {
                for (int column = 10; column < 18; column++) {
                    int painted = 0;
                    int color = 0;
                    for (Fill fill : fills) {
                        if (column >= fill.left() && column < fill.right() && row >= fill.top() && row < fill.bottom()) {
                            painted++;
                            color = fill.color();
                        }
                    }
                    assertEquals(color == 0 ? 0 : 1, painted);
                    pixels.append(color == 0 ? '.' : color == 0xFF000001 ? 'f' : 'b');
                }
                pixels.append('\n');
            }
            assertEquals("..bbbb..\n.bbbbbb.\nbbbffbbb\nbbffffbb\nbbffffbb\nbbbffbbb\n.bbbbbb.\n..bbbb..\n", pixels.toString());
        }
    }

    @Test
    void squareBorderKeepsItsThicknessInATranslatedClip() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            fixture.commitPanel("#FF000001", "#FF000002", 0, 2, 1,
                    Map.of("x", 10, "y", 20, "width", 6, "height", 6),
                    Map.of("x", 11, "y", 21, "width", 4, "height", 4));
            List<Fill> fills = fixture.paint();
            StringBuilder pixels = new StringBuilder();
            for (int row = 21; row < 25; row++) {
                for (int column = 11; column < 15; column++) pixels.append(colorAt(fills, column, row) == 0xFF000001 ? 'f' : 'b');
                pixels.append('\n');
            }
            assertEquals("bbbb\nbffb\nbffb\nbbbb\n", pixels.toString());
        }
    }

    @Test
    void zeroAndOversizedBordersHandleTinyRoundedPanels() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            Map<String, Object> rect = Map.of("x", 0, "y", 0, "width", 3, "height", 2);
            fixture.commitPanel("#FF000001", "#FF000002", 100, 0, 1, rect, rect);
            assertEquals(List.of(new Fill(0, 0, 3, 2, 0xFF000001)), fixture.paint());
            fixture.commitPanel("#FF000001", "#FF000002", 100, 100, 1, rect, rect);
            assertEquals(List.of(new Fill(0, 0, 3, 2, 0xFF000002)), fixture.paint());
        }
    }

    @Test
    void hugeOffscreenBoxPreparesOnlyItsVisibleRegionWithoutOverflow() throws Exception {
        try (PanelFixture fixture = new PanelFixture()) {
            fixture.commitPanel("#FF000001", "#FF000002", 0, 1, 1,
                    Map.of("x", -10, "y", -10, "width", Integer.MAX_VALUE, "height", Integer.MAX_VALUE),
                    Map.of("x", 0, "y", 0, "width", 3, "height", 3));
            assertEquals(List.of(new Fill(0, 0, 3, 3, 0xFF000001)), fixture.paint());
            fixture.commitPanel("#FF000001", "#FF000002", 2, 1, 1,
                    Map.of("x", 0, "y", 0, "width", 0, "height", 3),
                    Map.of("x", 0, "y", 0, "width", 3, "height", 3));
            assertEquals(List.of(), fixture.paint());
        }
    }

    private static int colorAt(List<Fill> fills, int x, int y) {
        int color = 0;
        for (Fill fill : fills) {
            if (x >= fill.left() && x < fill.right() && y >= fill.top() && y < fill.bottom()) color = fill.color();
        }
        return color;
    }

    private record Fill(int left, int top, int right, int bottom, int color) { }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<Fill> fills;

        private RecordingGraphics() { super(null, null, 0, 0); }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new Fill(left, top, right, bottom, color));
        }

        @Override
        public void enableScissor(int left, int top, int right, int bottom) { }

        @Override
        public void disableScissor() { }
    }

    private static final class PanelFixture implements AutoCloseable {
        private final Field clientField;
        private final Minecraft previous;
        private final Context context = Context.create("js");
        private final com.tkisor.nekojs.core.state.GenerationGlobals globals =
                new GlobalStateStores().newGeneration(ScriptType.CLIENT, false);
        private final JsxHostAdapter adapter;
        private final JsxScreen screen;
        private Object panel;

        private PanelFixture() throws Exception {
            clientField = Minecraft.class.getDeclaredField("instance");
            clientField.setAccessible(true);
            previous = (Minecraft) clientField.get(null);
            Minecraft minecraft = allocate(Minecraft.class);
            setField(minecraft, "gui", allocate(net.minecraft.client.gui.Gui.class));
            adapter = allocate(JsxHostAdapter.class);
            setField(adapter, "minecraft", minecraft);
            setField(adapter, "ownerThread", Thread.currentThread());
            setField(adapter, "tree", new JsxHostTree());
            setField(adapter, "manager", new ScriptManager(ScriptType.CLIENT, null, null, null, null,
                    null, null, null, List.of(), null));
            setField(adapter, "globals", globals);
            setField(adapter, "lifecycle", new UiRootLifecycle(false, 0, globals));
            setField(adapter, "viewportWidth", 100);
            setField(adapter, "viewportHeight", 100);
            setField(adapter, "resources", new MinecraftUiResourceResolver(null, new UiTextureBackend() {
                @Override
                public Size upload(net.minecraft.resources.Identifier slot,
                                   net.minecraft.resources.Identifier resource, byte[] bytes) {
                    throw new AssertionError("Panel fixture owns no images");
                }

                @Override
                public void release(net.minecraft.resources.Identifier slot) {
                    throw new AssertionError("Panel fixture owns no images");
                }
            }, List::of));
            screen = allocate(JsxScreen.class);
            setField(screen, "adapter", adapter);
            setField(adapter, "screen", screen);
            globals.registerUiRoot(adapter);
            clientField.set(null, minecraft);
        }

        private void commitPanel(String background, String borderColor, int radius, int borderWidth,
                                 double opacity, boolean clipped) {
            Map<String, Object> rect = Map.of("x", 10, "y", 20, "width", 8, "height", 8);
            Map<String, Object> clip = clipped ? Map.of("x", 12, "y", 22, "width", 4, "height", 4) : rect;
            commitPanel(background, borderColor, radius, borderWidth, opacity, rect, clip);
        }

        private void commitPanel(String background, String borderColor, int radius, int borderWidth,
                                 double opacity, Map<String, Object> rect, Map<String, Object> clip) {
            Map<String, Object> props = Map.of("id", "panel", "background", background, "borderColor", borderColor,
                    "radius", radius, "borderWidth", borderWidth, "opacity", opacity);
            Map<String, Object> node = Map.of("type", "panel", "id", "panel", "visible", true, "rect", rect,
                    "clip", clip, "overflow", Map.of("left", 0, "top", 0, "right", 0, "bottom", 0),
                    "style", props, "children", List.of(), "bindings", List.of());
            Map<String, Object> viewport = Map.of("width", 100, "height", 100, "contentWidth", 100,
                    "contentHeight", 100, "profile", 1, "designScale", 1,
                    "safeArea", Map.of("left", 0, "top", 0, "right", 0, "bottom", 0));
            String snapshot = new com.google.gson.Gson().toJson(Map.of("rootId", "panel-test", "viewport", viewport,
                    "nodes", List.of(node), "diagnostics", List.of()));
            adapter.layout(null, null, context.eval("js", "(" + snapshot + ")"));
            JsxHostAdapter.JsxHostTransaction transaction = adapter.begin();
            if (panel == null) panel = transaction.create("panel", "panel", props);
            else transaction.update(panel, "panel", "panel", props);
            transaction.order(null, List.of(panel));
            transaction.commit(List.of(panel));
        }

        private List<Fill> paint() throws Exception {
            RecordingGraphics graphics = allocate(RecordingGraphics.class);
            graphics.fills = new ArrayList<>();
            screen.extractRenderState(graphics, -1, -1, 0);
            return graphics.fills;
        }

        @Override
        public void close() throws IllegalAccessException {
            try {
                globals.close();
            } finally {
                try {
                    context.close();
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
