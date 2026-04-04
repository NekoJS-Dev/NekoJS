package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent; // 🌟 必须导入这个处理 ESC
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.io.File;
import java.util.List;

public class NekoErrorListScreen extends Screen {
    private final List<ErrorSummaryDTO> errors;
    private ErrorListWidget listWidget;
    private EditBox searchBox;
    private ContextMenu activeContextMenu = null;

    private FilterType currentFilter = FilterType.ALL;
    private enum FilterType {
        ALL("全部", 0xFFFFFFFF),
        RUNTIME("运行时", 0xFFE5534B),
        SYNTAX("语法", 0xFFF2A134),
        OTHER("其他", 0xFF748394);

        final String label;
        final int color;
        FilterType(String label, int color) { this.label = label; this.color = color; }
    }

    private String toastMessage = "";
    private long toastTime = 0;
    private long openTime;

    public NekoErrorListScreen(List<ErrorSummaryDTO> errors) {
        super(Component.literal("NekoJS Console"));
        this.errors = errors;
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();

        this.searchBox = new EditBox(this.font, this.width / 2 - 80, 8, 160, 12, Component.literal("搜索..."));
        this.searchBox.setHint(Component.literal("§8输入关键词过滤..."));
        this.searchBox.setResponder(s -> this.refreshList());
        this.addRenderableWidget(this.searchBox);

        this.listWidget = new ErrorListWidget(this.minecraft, this.width, this.height - 95, 55, 44);
        this.refreshList();
        this.addRenderableWidget(this.listWidget);

        this.addRenderableWidget(Button.builder(Component.literal("关闭"), btn -> this.onClose())
                .bounds(this.width / 2 - 40, this.height - 25, 80, 20).build());
    }

    private void refreshList() {
        String filterText = searchBox.getValue().toLowerCase();
        this.listWidget.clearEntries();
        for (ErrorSummaryDTO dto : errors) {
            if (currentFilter != FilterType.ALL && judgeErrorType(dto) != currentFilter) continue;
            if (filterText.isEmpty() || dto.path().toLowerCase().contains(filterText) || dto.message().toLowerCase().contains(filterText)) {
                this.listWidget.add(new ErrorEntry(dto));
            }
        }
        this.listWidget.setScrollAmount(0);
    }

    private FilterType judgeErrorType(ErrorSummaryDTO dto) {
        String msg = dto.message().toLowerCase();
        if (msg.contains("syntax") || msg.contains("unexpected")) return FilterType.SYNTAX;
        if (msg.contains("null") || msg.contains("undefined") || msg.contains("error")) return FilterType.RUNTIME;
        return FilterType.OTHER;
    }

    private int getCount(FilterType type) {
        if (type == FilterType.ALL) return errors.size();
        return (int) errors.stream().filter(e -> judgeErrorType(e) == type).count();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF0A0A0B, 0xFF121214);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float fade = Mth.clamp((System.currentTimeMillis() - openTime) / 300f, 0, 1);
        graphics.fill(0, 0, this.width, 50, ARGB.color((int)(fade * 180), 12, 12, 12));
        graphics.fill(0, 50, this.width, 51, 0x30FFFFFF);

        graphics.text(this.font, "§cNEKO§fJS", 15, 11, -1);

        int tabX = 15;
        int tabY = 32;
        for (FilterType type : FilterType.values()) {
            String fullLabel = type.label + " §8" + getCount(type);
            int labelW = this.font.width(fullLabel);
            boolean isCurrent = currentFilter == type;
            boolean hover = mouseX >= tabX && mouseX <= tabX + labelW && mouseY >= tabY && mouseY <= tabY + 12;

            if (isCurrent) graphics.fill(tabX, tabY + 12, tabX + labelW, tabY + 13, type.color);
            graphics.text(this.font, fullLabel, tabX, tabY, isCurrent ? -1 : (hover ? 0xFFAAAAAA : 0xFF666666));
            tabX += labelW + 15;
        }

        String clearBtn = "§7[ 清空记录 ]";
        int clearX = this.width - this.font.width(clearBtn) - 15;
        boolean clearHover = mouseX >= clearX && mouseX <= clearX + this.font.width(clearBtn) && mouseY >= 11 && mouseY <= 20;
        graphics.text(this.font, clearHover ? "§f§n[ 清空记录 ]" : clearBtn, clearX, 11, -1);

        super.extractRenderState(graphics, activeContextMenu != null ? -999 : mouseX, activeContextMenu != null ? -999 : mouseY, partialTick);

        // 🌟 融入物理弹性的丝滑 Toast 动画
        long timeRemaining = toastTime - System.currentTimeMillis();
        if (timeRemaining > 0) {
            float alphaAnim = Mth.clamp(timeRemaining / 300f, 0, 1);
            float yAnim = Mth.clamp((2000f - timeRemaining) / 150f, 0, 1);
            int alphaBase = (int) (alphaAnim * 255);

            int tw = this.font.width(toastMessage) + 20;
            int ty = this.height - 40 - (int)(15 * yAnim);

            graphics.fill(this.width/2-tw/2, ty, this.width/2+tw/2, ty + 16, (int)(alphaAnim * 0xCC) << 24);
            graphics.outline(this.width/2-tw/2, ty, tw, 16, alphaBase << 24 | 0x44FF44);
            graphics.centeredText(this.font, toastMessage, this.width / 2, ty + 4, alphaBase << 24 | 0xFFFFFF);
        }

        if (this.activeContextMenu != null) {
            this.activeContextMenu.render(graphics, mouseX, mouseY);
        }
    }

    // 🌟 ESC 防误触：如果有菜单先关菜单
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.activeContextMenu != null && event.isEscape()) {
            this.activeContextMenu = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.activeContextMenu != null) {
            this.activeContextMenu.mouseClicked(event.x(), event.y(), event.button());
            this.activeContextMenu = null;
            return true;
        }

        int tabX = 15;
        int tabY = 32;
        for (FilterType type : FilterType.values()) {
            int labelW = this.font.width(type.label + " §8" + getCount(type));
            if (event.x() >= tabX && event.x() <= tabX + labelW && event.y() >= tabY && event.y() <= tabY + 12) {
                this.currentFilter = type;
                this.refreshList();
                return true;
            }
            tabX += labelW + 15;
        }

        String clearBtn = "[ 清空记录 ]";
        int clearX = this.width - this.font.width(clearBtn) - 15;
        if (event.x() >= clearX && event.x() <= this.width - 15 && event.y() >= 11 && event.y() <= 20) {
            this.errors.clear();
            this.refreshList();
            showToast("§a✔ 记录已清理");
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void showToast(String msg) { this.toastMessage = msg; this.toastTime = System.currentTimeMillis() + 2000; }
    private void copyToClipboard(String text, String typeName) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        showToast("§a✔ 已复制" + typeName);
    }

    private class ErrorListWidget extends ObjectSelectionList<ErrorEntry> {
        public ErrorListWidget(Minecraft mc, int w, int h, int y, int ih) { super(mc, w, h, y, ih); }
        public void add(ErrorEntry entry) { this.addEntry(entry); }
        @Override public int getRowWidth() { return Math.min(420, NekoErrorListScreen.this.width - 40); }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}
    }

    private class ErrorEntry extends ObjectSelectionList.Entry<ErrorEntry> {
        private final ErrorSummaryDTO dto;
        public ErrorEntry(ErrorSummaryDTO dto) { this.dto = dto; }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean isHovered, float pt) {
            boolean hov = (NekoErrorListScreen.this.activeContextMenu == null) && isHovered;
            int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight() - 6;
            g.fill(x, y, x + w, y + h, hov ? 0x15FFFFFF : 0x08FFFFFF);
            g.fill(x + 4, y + 6, x + 6, y + h - 6, judgeErrorType(dto).color);

            int tx = x + 14;
            g.text(NekoErrorListScreen.this.font, "§f" + dto.path(), tx, y + 8, -1);
            String lineStr = "L" + dto.line();
            int badgeX = x + w - NekoErrorListScreen.this.font.width(lineStr) - 12;
            g.fill(badgeX - 4, y + 7, badgeX + NekoErrorListScreen.this.font.width(lineStr) + 4, y + 17, 0x25FFFFFF);
            g.text(NekoErrorListScreen.this.font, "§7" + lineStr, badgeX, y + 8, -1);

            String msg = NekoErrorListScreen.this.font.plainSubstrByWidth(dto.message(), w - 40);
            if (msg.length() < dto.message().length()) msg = msg.substring(0, Math.max(0, msg.length() - 3)) + "...";
            g.text(NekoErrorListScreen.this.font, "§8> §7" + msg, tx, y + 22, -1);
            if (dto.count() > 1) g.text(NekoErrorListScreen.this.font, "§6" + dto.count() + "x", badgeX - 30, y + 8, -1);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (NekoErrorListScreen.this.activeContextMenu != null) return false;
            if (event.button() == 1) {
                NekoErrorListScreen.this.activeContextMenu = new ContextMenu((int)event.x(), (int)event.y(), List.of(
                        new MenuItem("📂 打开脚本", () -> {
                            File file = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + dto.path());
                            Util.getPlatform().openFile(file);
                            showToast("§a✔ 指令已发送");
                        }),
                        new MenuItem("🚀 智能定位 (Code)", () -> {
                            try {
                                String absPath = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + dto.path()).getAbsolutePath();
                                new ProcessBuilder("code", "--goto", absPath + ":" + dto.line()).start();
                                showToast("§a✔ 尝试定位至 L" + dto.line());
                            } catch (Exception e) {
                                showToast("§c✖ 需要配置 VSCode 环境变量");
                            }
                        }),
                        new MenuItem("📋 复制路径", () -> copyToClipboard(dto.path(), "路径")),
                        new MenuItem("📜 复制堆栈", () -> copyToClipboard(dto.fullDetails(), "堆栈"))
                ));
                return true;
            } else if (event.button() == 0) {
                Minecraft.getInstance().setScreen(new NekoErrorScreen(NekoErrorListScreen.this, dto.id(), dto.fullDetails()));
                return true;
            }
            return false;
        }
        @Override public Component getNarration() { return Component.literal(dto.path()); }
    }

    private class ContextMenu {
        int x, y, width = 120, height; // 加宽一点以适应文字
        List<MenuItem> items;
        public ContextMenu(int sx, int sy, List<MenuItem> items) {
            this.items = items;
            this.height = items.size() * 18 + 6;
            this.x = Math.min(sx, NekoErrorListScreen.this.width - width - 5);
            this.y = Math.min(sy, NekoErrorListScreen.this.height - height - 5);
        }
        public void render(GuiGraphicsExtractor g, int mx, int my) {
            g.fill(x, y, x + width, y + height, 0xFF18181B);
            g.outline(x, y, width, height, 0xFF3F3F46);
            for (int i = 0; i < items.size(); i++) {
                int iy = y + 3 + i * 18;
                boolean h = mx >= x && mx <= x + width && my >= iy && my < iy + 18;
                if (h) g.fill(x + 2, iy, x + width - 2, iy + 18, 0xFF27272A);
                g.text(NekoErrorListScreen.this.font, items.get(i).label, x + 8, iy + 5, h ? -1 : 0xFFA1A1AA);
            }
        }
        public boolean mouseClicked(double mx, double my, int b) {
            if (b == 0 && mx >= x && mx <= x + width && my >= y && my <= y + height) {
                int index = ((int)my - y - 3) / 18;
                if (index >= 0 && index < items.size()) items.get(index).action.run();
                return true;
            }
            return false;
        }
    }
    private record MenuItem(String label, Runnable action) {}
}