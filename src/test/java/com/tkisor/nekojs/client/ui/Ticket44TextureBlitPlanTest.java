//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.ResourceStatus;
import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import com.tkisor.nekojs.api.ui.VisualSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Ticket44TextureBlitPlanTest {
    private final UiDiagnostic.Location location = new UiDiagnostic.Location("root", "image", "hero", 7);

    @Test
    void containCentersImageAndPreservesOpacity() {
        var plan = UiTextureBlitPlan.prepare(texture(200, 100), null, VisualSpec.ImageFit.CONTAIN,
                0.5, 10, 20, 100, 100, location).plan();
        assertEquals(10, plan.x());
        assertEquals(45, plan.y());
        assertEquals(100, plan.width());
        assertEquals(50, plan.height());
        assertEquals(0x80FFFFFF, plan.argb());
        assertEquals(200, plan.sourceWidth());
        assertEquals(100, plan.sourceHeight());
    }

    @Test
    void coverCropsSourceInsteadOfDrawingBeyondDestination() {
        var plan = UiTextureBlitPlan.prepare(texture(200, 100), null, VisualSpec.ImageFit.COVER,
                1, 0, 0, 100, 100, location).plan();
        assertEquals(50.0f, plan.sourceX());
        assertEquals(100, plan.sourceWidth());
        assertEquals(100, plan.sourceHeight());
        assertEquals(100, plan.width());
        assertEquals(100, plan.height());
    }

    @Test
    void cropUsesTexturePixelsAndRejectsOverflowWithoutIntegerWrap() {
        var plan = UiTextureBlitPlan.prepare(texture(64, 32), new VisualSpec.CropRect(16, 8, 32, 16),
                VisualSpec.ImageFit.STRETCH, 1, 1, 2, 90, 30, location).plan();
        assertEquals(16.0f, plan.sourceX());
        assertEquals(8.0f, plan.sourceY());
        assertEquals(32, plan.sourceWidth());
        assertEquals(64, plan.textureWidth());
        var rejected = UiTextureBlitPlan.prepare(texture(64, 32),
                new VisualSpec.CropRect(Integer.MAX_VALUE, 0, 10, 10), VisualSpec.ImageFit.STRETCH,
                1, 0, 0, 10, 10, location);
        assertNull(rejected.plan());
        assertEquals(UiErrorCodes.INVALID_RESOURCE_SIZE, rejected.diagnostic().code());
        assertEquals("hero", rejected.diagnostic().nodeKey());
    }

    @Test
    void failureDoesNotProduceABlitAndEmptyDestinationDoesNotDivideByZero() {
        var failure = new MinecraftUiResourceResolver.Texture(new ResourceStatus(ResourceStatus.State.INVALID,
                "demo:broken", null, UiErrorCodes.RESOURCE_DECODE_FAILED, "decode failed"), null, 0, 0);
        assertEquals(UiErrorCodes.RESOURCE_DECODE_FAILED,
                UiTextureBlitPlan.prepare(failure, null, null, 1, 0, 0, 10, 10, location).diagnostic().code());
        assertNull(UiTextureBlitPlan.prepare(texture(16, 16), null, null, 1, 0, 0, 0, 10, location).plan());
    }

    private MinecraftUiResourceResolver.Texture texture(int width, int height) {
        return new MinecraftUiResourceResolver.Texture(ResourceStatus.resolved("demo:hero", "demo:textures/hero.png"),
                Identifier.parse("nekojs:ui/fixture"), width, height);
    }
}
//?}
