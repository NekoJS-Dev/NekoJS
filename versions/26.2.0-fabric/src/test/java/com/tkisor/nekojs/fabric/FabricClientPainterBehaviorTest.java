package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricClientPainterBehaviorTest {
    @Test
    void firstInitializationAndReplacementEventEachPaintExactlyOnce() throws Exception {
        Method install = FabricClientEventBindings.class.getDeclaredMethod("installScreenPainter", Event.class);
        install.setAccessible(true);
        Field singleton = Minecraft.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        singleton.set(null, allocate(Minecraft.class));
        AtomicInteger frames = new AtomicInteger();
        var token = FabricClientEventBindings.SCREEN_RENDER.bus().listen(event -> {
            frames.incrementAndGet();
            event.getPainter().color(0xFF55AA33).rect(event.getMouseX(), event.getMouseY(), 8, 4)
                    .outline(event.getMouseX(), event.getMouseY(), 8, 4);
        });
        try {
            RecordingGraphics graphics = allocate(RecordingGraphics.class);
            Event<ScreenEvents.AfterExtract> first = extractionEvent();
            install.invoke(null, first);
            install.invoke(null, first);
            first.invoker().afterExtract(null, graphics, 3, 7, 0.5F);
            assertEquals(1, frames.get(), "first screen initialization must register one callback");
            assertEquals(0xFF55AA33, graphics.color);
            assertEquals(3, graphics.left);
            assertEquals(7, graphics.top);
            assertEquals(11, graphics.right);
            assertEquals(11, graphics.bottom);
            assertEquals(8, graphics.outlineWidth);
            assertEquals(4, graphics.outlineHeight);

            Event<ScreenEvents.AfterExtract> resized = extractionEvent();
            install.invoke(null, resized);
            install.invoke(null, resized);
            resized.invoker().afterExtract(null, graphics, 10, 12, 0.25F);
            assertEquals(2, frames.get(), "replacement native event must be reattached once");
            assertEquals(10, graphics.left);
            assertEquals(12, graphics.top);
        } finally {
            FabricClientEventBindings.SCREEN_RENDER.bus().unregister(token);
            singleton.set(null, previous);
        }
    }

    @Test
    void fullTextureDrawUsesEndpointGeometryAndNormalizedUv() throws Exception {
        Method install = FabricClientEventBindings.class.getDeclaredMethod("installScreenPainter", Event.class);
        install.setAccessible(true);
        Field singleton = Minecraft.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        singleton.set(null, allocate(Minecraft.class));
        var token = FabricClientEventBindings.SCREEN_RENDER.bus().listen(event ->
                event.getPainter().textureFull("minecraft:textures/block/stone.png", 10, 20, 8, 4));
        try {
            RecordingGraphics graphics = allocate(RecordingGraphics.class);
            Event<ScreenEvents.AfterExtract> frame = extractionEvent();
            install.invoke(null, frame);
            frame.invoker().afterExtract(null, graphics, 0, 0, 0.5F);
            assertEquals(10, graphics.left);
            assertEquals(20, graphics.top);
            assertEquals(18, graphics.right);
            assertEquals(24, graphics.bottom);
            assertEquals(0F, graphics.u0);
            assertEquals(1F, graphics.u1);
            assertEquals(0F, graphics.v0);
            assertEquals(1F, graphics.v1);
        } finally {
            FabricClientEventBindings.SCREEN_RENDER.bus().unregister(token);
            singleton.set(null, previous);
        }
    }

    private static Event<ScreenEvents.AfterExtract> extractionEvent() {
        return EventFactory.createArrayBacked(ScreenEvents.AfterExtract.class,
                callbacks -> (screen, graphics, mouseX, mouseY, partialTick) -> {
                    for (ScreenEvents.AfterExtract callback : callbacks) {
                        callback.afterExtract(screen, graphics, mouseX, mouseY, partialTick);
                    }
                });
    }

    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        int color;
        int left;
        int top;
        int right;
        int bottom;
        int outlineWidth;
        int outlineHeight;
        float u0;
        float u1;
        float v0;
        float v1;

        @Override
        public void blit(net.minecraft.resources.Identifier texture, int left, int top, int right, int bottom,
                float u0, float u1, float v0, float v1) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.u0 = u0;
            this.u1 = u1;
            this.v0 = v0;
            this.v1 = v1;
        }

        @Override
        public void outline(int left, int top, int width, int height, int color) {
            outlineWidth = width;
            outlineHeight = height;
        }

        private RecordingGraphics() {
            super(null, null, 0, 0);
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.color = color;
        }
    }

    @SuppressWarnings("removal")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
    }
}
