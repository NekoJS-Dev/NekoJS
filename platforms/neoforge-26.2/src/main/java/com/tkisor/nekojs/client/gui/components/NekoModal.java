package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;
import java.util.function.Consumer;

public class NekoModal {
    private final Font font;
    private boolean isOpen = false;
    private int type = 0; // 0=输入, 1=确认
    private String title = "";
    private final EditBox input;

    private Consumer<String> inputCallback;
    private Runnable confirmCallback;
    private final Runnable onCloseFocusClear;

    public NekoModal(Font font, Runnable onCloseFocusClear) {
        this.font = font;
        this.onCloseFocusClear = onCloseFocusClear;
        this.input = new EditBox(font, 0, 0, 220, 18, Component.empty());
        this.input.setTextColor(0xFFFFFFFF);
        this.input.visible = false;
        this.input.active = false;
    }

    public EditBox getWidget() { return input; }
    public boolean isOpen() { return isOpen; }
    public boolean isInputMode() { return isOpen && type == 0; }

    public void updateBounds(int screenW, int screenH) {
        int mw = 240; int mh = 80;
        int mx = screenW / 2 - mw / 2;
        int my = screenH / 2 - mh / 2;
        this.input.setX(mx + 10);
        this.input.setY(my + 28);
    }

    public void openInput(String title, Consumer<String> callback) {
        this.title = title;
        this.inputCallback = callback;
        this.type = 0;
        this.input.setValue("");
        this.input.visible = true;
        this.input.active = true;
        this.isOpen = true;
    }

    public void openConfirm(String title, Runnable confirmCallback) {
        this.title = title;
        this.confirmCallback = confirmCallback;
        this.type = 1;
        this.input.visible = false;
        this.input.active = false;
        this.isOpen = true;
        this.onCloseFocusClear.run();
    }

    public void close() {
        this.isOpen = false;
        this.input.visible = false;
        this.input.active = false;
        this.onCloseFocusClear.run();
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, int screenW, int screenH) {
        if (!isOpen) return;

        int mw = 240; int mh = 80;
        int mx = screenW / 2 - mw / 2;
        int my = screenH / 2 - mh / 2;

        graphics.fill(0, 0, screenW, screenH, 0x88000000);
        graphics.fill(mx, my, mx + mw, my + mh, 0xFF1E1E1E);
        graphics.outline(mx, my, mw, mh, 0xFF454545);

        if (type == 0) {
            graphics.centeredText(this.font, title, screenW / 2, my + 8, 0xFFFFFFFF);
            this.input.extractRenderState(graphics, mouseX, mouseY, partialTick);
        } else if (type == 1) {
            graphics.centeredText(this.font, title, screenW / 2, my + 14, 0xFFFFFFFF);
            graphics.centeredText(this.font, Component.translatable("nekojs.gui.modal.warning.irreversible"), screenW / 2, my + 32, 0xFFFFFFFF);
        }

        int btnY = my + 55;
        boolean hovC = mouseX >= mx + mw - 110 && mouseX <= mx + mw - 60 && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(mx + mw - 110, btnY, mx + mw - 60, btnY + 16, hovC ? 0xFF007ACC : 0xFF094771);
        graphics.centeredText(this.font, Component.translatable("nekojs.gui.modal.button.confirm"), mx + mw - 85, btnY + 4, 0xFFFFFFFF);

        boolean hovX = mouseX >= mx + mw - 55 && mouseX <= mx + mw - 10 && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(mx + mw - 55, btnY, mx + mw - 10, btnY + 16, hovX ? 0xFF555555 : 0xFF333333);
        graphics.centeredText(this.font, Component.translatable("nekojs.gui.modal.button.cancel"), mx + mw - 32, btnY + 4, 0xFFFFFFFF);
    }

    public boolean mouseClicked(MouseButtonEvent event, int screenW, int screenH) {
        if (!isOpen) return false;

        int mw = 240; int mh = 80;
        int mx = screenW / 2 - mw / 2;
        int my = screenH / 2 - mh / 2;
        int btnY = my + 55;

        if (event.button() == 0 && event.y() >= btnY && event.y() <= btnY + 16) {
            if (event.x() >= mx + mw - 110 && event.x() <= mx + mw - 60) {
                if (type == 0 && inputCallback != null) inputCallback.accept(input.getValue());
                else if (type == 1 && confirmCallback != null) confirmCallback.run();
                close();
                return true;
            }
            if (event.x() >= mx + mw - 55 && event.x() <= mx + mw - 10) {
                close();
                return true;
            }
        }
        if (type == 0 && input.mouseClicked(event, false)) return true;
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!isOpen) return false;
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (type == 0 && inputCallback != null) inputCallback.accept(input.getValue());
            else if (type == 1 && confirmCallback != null) confirmCallback.run();
            close();
            return true;
        }
        return false;
    }
}