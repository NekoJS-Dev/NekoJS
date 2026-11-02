//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeItemVisualTest {
    @Test
    void itemsResolveDuringStagingAndPaintCachedNaturalSizeCenteredWithNativeTooltip() throws Exception {
        try (Harness fixture = new Harness()) {
            assertTrue(fixture.base.adapter.supportsPrimitive("item"));
            assertTrue(fixture.base.adapter.supportsPrimitive("dialog"));
            assertEquals(List.of(new JsxItemVisual.Descriptor("minecraft:stone", 1, true)), fixture.resolved);
            fixture.paint(5, 5);
            fixture.paint(5, 5);
            assertEquals(1, fixture.resolved.size(), "Painting must not perform a registry lookup");
            assertEquals(List.of("minecraft:stone@8,4", "minecraft:stone@8,4"), fixture.paints);
            assertEquals(List.of("minecraft:stone", "minecraft:stone"), fixture.tooltips);
            assertEquals(0L, fixture.base.adapter.performanceCounters().get("diagnostics"));
        }
    }

    @Test
    void successfulUpdatesReplaceTheCachedCountAndTooltipPlanExactlyOnce() throws Exception {
        try (Harness fixture = new Harness()) {
            fixture.base.context.eval("js", "count=99; nativeTooltip=false; root.refresh()");
            assertEquals(2, fixture.resolved.size());
            assertEquals(new JsxItemVisual.Descriptor("minecraft:stone", 99, false), fixture.resolved.getLast());
            fixture.paint(5, 5);
            assertEquals(List.of("minecraft:stone@8,4"), fixture.paints);
            assertTrue(fixture.tooltips.isEmpty());
        }
    }

    @Test
    void aCustomStringTooltipOverridesTheVanillaTooltipEvenWhenEmpty() throws Exception {
        try (Harness fixture = new Harness()) {
            fixture.base.context.eval("js", "customTooltip='Custom item'; root.refresh()");
            assertEquals("Custom item", fixture.paint(5, 5).tooltip);
            assertTrue(fixture.tooltips.isEmpty());
            fixture.base.context.eval("js", "customTooltip=''; root.refresh()");
            assertEquals("", fixture.paint(5, 5).tooltip);
            assertTrue(fixture.tooltips.isEmpty());
        }
    }

    @Test
    void failedItemResolutionOrValidationCannotReplaceThePublishedNodeOrVisual() throws Exception {
        try (Harness fixture = new Harness()) {
            JsxHostTree.Node published = fixture.node();
            JsxItemVisual.Resolved visual = published.itemVisual;
            fixture.base.context.eval("js", "item='missing:item'; root.refresh()");
            assertSame(published, fixture.node());
            assertSame(visual, fixture.node().itemVisual);
            fixture.paint(5, 5);
            assertEquals(List.of("minecraft:stone@8,4"), fixture.paints);
            fixture.base.context.eval("js", "item='minecraft:stone'; count=100; root.refresh()");
            assertSame(published, fixture.node());
            assertSame(visual, fixture.node().itemVisual);
            assertEquals("minecraft:stone", fixture.node().props.get("item"));
        }
    }

    @Test
    void invisibleRemovedAndFullyClippedItemsHaveNoDrawOrTooltipCalls() throws Exception {
        try (Harness fixture = new Harness()) {
            fixture.node().props.put("visible", false);
            fixture.paint(5, 5);
            fixture.node().props.put("visible", true);
            fixture.node().hasClip = true;
            fixture.node().clipWidth = 0;
            fixture.node().clipHeight = 0;
            fixture.paint(5, 5);
            fixture.node().removed = true;
            fixture.paint(5, 5);
            assertTrue(fixture.paints.isEmpty());
            assertTrue(fixture.tooltips.isEmpty());
            assertEquals(1, fixture.resolved.size());
        }
    }

    private static final class Harness implements AutoCloseable {
        final NativeUiScreenFixture base = new NativeUiScreenFixture();
        final List<JsxItemVisual.Descriptor> resolved = new ArrayList<>();
        final List<String> paints = new ArrayList<>();
        final List<String> tooltips = new ArrayList<>();
        final JsxHostTree tree;

        Harness() throws Exception {
            base.adapter.itemResolver(descriptor -> {
                resolved.add(descriptor);
                if (descriptor.item().equals("missing:item")) {
                    throw new IllegalArgumentException("[NEKO-6012] Fixture rejected an unknown item");
                }
                return new JsxItemVisual.Resolved() {
                    @Override public void paint(GuiGraphicsExtractor graphics, Font font, int x, int y) {
                        paints.add(descriptor.item() + "@" + x + "," + y);
                    }
                    @Override public void tooltip(GuiGraphicsExtractor graphics, Font font, int x, int y) {
                        tooltips.add(descriptor.item());
                    }
                };
            });
            base.installRuntime();
            base.context.eval("js", """
                    globalThis.item='minecraft:stone'; globalThis.count=undefined;
                    globalThis.nativeTooltip=true; globalThis.customTooltip=undefined;
                    globalThis.root=UI.createRoot(() => UI.element('item',{
                      id:'item',item,itemTooltip:nativeTooltip,width:32,height:24,
                      ...(count === undefined ? {} : {count}),
                      ...(customTooltip === undefined ? {} : {tooltip:customTooltip})
                    }),host,{id:'native-item-test',viewport:{width:100,height:100}});
                    host.bindRoot(root);
                    """);
            Field field = JsxHostAdapter.class.getDeclaredField("tree");
            field.setAccessible(true);
            tree = (JsxHostTree) field.get(base.adapter);
        }

        JsxHostTree.Node node() { return tree.roots().getFirst(); }

        Painter paint(int x, int y) throws Exception {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            Painter painter = (Painter) ((Unsafe) field.get(null)).allocateInstance(Painter.class);
            base.screen.extractRenderState(painter, x, y, 0);
            return painter;
        }

        @Override public void close() throws Exception { base.close(); }
    }

    private static final class Painter extends GuiGraphicsExtractor {
        String tooltip;
        private Painter() { super(null, null, 0, 0); }
        @Override public void enableScissor(int x, int y, int right, int bottom) { }
        @Override public void disableScissor() { }
        @Override public void setTooltipForNextFrame(Font font, Component text, int x, int y) { tooltip = text.getString(); }
    }
}
//?}
