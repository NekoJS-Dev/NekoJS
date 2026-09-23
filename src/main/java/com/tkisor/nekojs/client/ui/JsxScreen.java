//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Minimal retained JSX screen shell; host painting is added by the platform adapter. */
public final class JsxScreen extends Screen {
    private final boolean pausesGame;
    private final JsxHostAdapter adapter;
    private boolean disposed;
    private int paintCount;

    JsxScreen(String title, boolean pausesGame, JsxHostAdapter adapter) {
        super(Component.literal(title == null ? "" : title));
        this.pausesGame = pausesGame;
        this.adapter = adapter;
    }

    @Override
    protected void init() {
        super.init();
        adapter.resize(width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (disposed) return;
        paintCount++;
        if (adapter.roots().isEmpty()) return;
        for (JsxHostTree.Node node : adapter.roots()) adapter.paintNode(graphics, node, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return !disposed && adapter.dispatchAt(event.x(), event.y(), "click", event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return !disposed && adapter.dispatchAt(event.x(), event.y(), "release", event.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return !disposed && adapter.dispatchScroll(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (disposed) return false;
        if (adapter.key(event.key(), event.modifiers())) return true;
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return !disposed && adapter.textInput(event.codepoint());
    }

    @Override
    public boolean isPauseScreen() {
        return pausesGame;
    }

    @Override
    public void onClose() {
        adapter.close();
        super.onClose();
    }

    @Override
    public void removed() {
        if (disposed) return;
        adapter.close();
        disposed = true;
        super.removed();
    }

    boolean closeOnEscape() {
        return adapter.closeOnEscape();
    }

    public boolean isDisposed() {
        return disposed;
    }

    public int paintCount() {
        return paintCount;
    }
}
//?}
