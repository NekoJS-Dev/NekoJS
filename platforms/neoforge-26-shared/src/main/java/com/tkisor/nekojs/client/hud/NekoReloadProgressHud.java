package com.tkisor.nekojs.client.hud;

import com.tkisor.nekojs.core.lifecycle.ReloadProgressTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Reload progress HUD (feature 8e): a small semi-transparent panel at the top-center of the
 * screen showing script (re)load progress — a green progress bar plus a
 * {@code "<type> — <message> (NN%)"} label. Subscribes itself to {@code NeoForge.EVENT_BUS}
 * from {@link #install()}; the subscription lives here (not in {@code ClientEvents}) so the
 * feature stays self-contained.
 *
 * <p>Reads an immutable {@link ReloadProgressTracker.Snapshot} per frame: active snapshots
 * always render, finished ones linger ~1.5s at 100% (see {@code ReloadProgressTracker}).
 * Rendering is gameplay-time only in practice — the vanilla loading overlay draws on top of
 * the HUD during initial load, and reloads triggered during play (F3+T, {@code /nekojs reload})
 * are exactly what this panel visualizes.
 */
public final class NekoReloadProgressHud {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 28;
    private static final int BAR_HEIGHT = 6;
    private static final int MARGIN_TOP = 8;
    private static final int PADDING = 8;

    private static final int COLOR_PANEL_BORDER = 0xAA000000;
    private static final int COLOR_PANEL_FILL = 0x66000000;
    private static final int COLOR_BAR_TRACK = 0x66333333;
    private static final int COLOR_BAR_FILL = 0xFF5DBB63;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private static boolean installed;

    private NekoReloadProgressHud() {
    }

    /** Registers the {@code RenderGuiEvent.Post} listener once; safe to call repeatedly. */
    public static void install() {
        if (installed) return;
        installed = true;
        NeoForge.EVENT_BUS.addListener(NekoReloadProgressHud::onRenderGuiPost);
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        render(event.getGuiGraphics(), Minecraft.getInstance());
    }

    /**
     * Core drawing, split out for readability: panel background, progress bar and label.
     * Package-visible geometry constants keep the Katton parity (220x28 top-center).
     */
    static void render(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        ReloadProgressTracker.Snapshot snapshot = ReloadProgressTracker.snapshot();
        if (!snapshot.visibleAt(System.currentTimeMillis())) return;

        int screenWidth = graphics.guiWidth();
        int left = (screenWidth - PANEL_WIDTH) / 2;
        int top = MARGIN_TOP;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        graphics.fill(left, top, right, bottom, COLOR_PANEL_BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, COLOR_PANEL_FILL);

        int barLeft = left + PADDING;
        int barRight = right - PADDING;
        int barTop = bottom - BAR_HEIGHT - 6;
        int barBottom = barTop + BAR_HEIGHT;
        graphics.fill(barLeft, barTop, barRight, barBottom, COLOR_BAR_TRACK);

        float progress = Math.max(0.0f, Math.min(1.0f, snapshot.progress()));
        int progressWidth = Math.round((barRight - barLeft) * progress);
        if (progressWidth > 0) {
            graphics.fill(barLeft, barTop, barLeft + progressWidth, barBottom, COLOR_BAR_FILL);
        }

        int percent = Math.round(progress * 100.0f);
        String label = snapshot.scriptType() + " — " + snapshot.message() + " (" + percent + "%)";
        graphics.text(minecraft.font, label, left + PADDING, top + 7, COLOR_TEXT, true);
    }
}
