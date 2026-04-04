package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.components.NekoCodeEditor;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class NekoErrorDashboardScreen extends Screen {
    private final List<ErrorSummaryDTO> errors;

    private ErrorListWidget listWidget;
    private EditBox searchBox;
    private StackTraceList stackList;
    private ContextMenu activeContextMenu = null;

    private ErrorSummaryDTO selectedError = null;
    private FilterType currentFilter = FilterType.ALL;

    private boolean isMaximized = false;
    private boolean isEditing = false;

    private NekoCodeEditor codeEditor;

    private int layoutRightX, layoutRightW, layoutContentY, layoutContentH;
    private String toastMessage = "";
    private long toastTime = 0;
    private long openTime;

    private enum FilterType {
        ALL("全部", 0xFFFFFFFF), RUNTIME("运行", 0xFFE5534B),
        SYNTAX("语法", 0xFFF2A134), OTHER("其他", 0xFF748394);
        final String label; final int color;
        FilterType(String label, int color) { this.label = label; this.color = color; }
    }

    public NekoErrorDashboardScreen(List<ErrorSummaryDTO> errors) {
        super(Component.literal("NekoJS Dashboard"));
        this.errors = errors;
        if (!errors.isEmpty()) this.selectedError = errors.get(0);
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();
        this.buildDashboardLayout();
    }

    private void buildDashboardLayout() {
        String oldSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        double oldScroll = this.listWidget != null ? this.listWidget.scrollAmount() : 0;
        this.clearWidgets();

        int margin = 15;
        int leftW = isMaximized ? 0 : Math.max(160, (int)(this.width * 0.35));
        this.layoutRightX = isMaximized ? margin : leftW + margin * 2;
        this.layoutRightW = this.width - layoutRightX - margin;
        this.layoutContentY = 55;
        this.layoutContentH = this.height - layoutContentY - margin;

        if (!isMaximized) {
            this.searchBox = new EditBox(this.font, margin, 8, leftW, 12, Component.literal("搜索..."));
            this.searchBox.setHint(Component.literal("§8过滤路径..."));
            this.searchBox.setValue(oldSearch);
            this.searchBox.setResponder(s -> this.refreshList());
            this.addRenderableWidget(this.searchBox);

            this.listWidget = new ErrorListWidget(this.minecraft, leftW, layoutContentH, layoutContentY, 36);
            this.listWidget.setX(margin);
            this.refreshList();
            this.listWidget.setScrollAmount(oldScroll);
            this.addRenderableWidget(this.listWidget);
        }

        if (!isEditing) {
            this.stackList = new StackTraceList(this.minecraft, layoutRightW - 2, layoutContentH - 2, layoutContentY + 2, 12);
            this.stackList.setX(layoutRightX + 1);
            this.refreshStackView();
            this.addRenderableWidget(this.stackList);
            this.codeEditor = null;
        } else {
            String initialText = "";
            try {
                Path path = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + selectedError.path()).toPath();
                if (Files.exists(path)) initialText = Files.readString(path);
            } catch (Exception e) {
                initialText = "// 读取失败: " + e.getMessage();
            }

            this.codeEditor = new NekoCodeEditor(this.font, layoutRightX, layoutContentY, layoutRightW, layoutContentH, initialText);
            this.addRenderableWidget(this.codeEditor.getWidget());
            this.setFocused(this.codeEditor.getWidget());
        }
    }

    private void refreshList() {
        if (listWidget == null) return;
        String filter = searchBox.getValue().toLowerCase();
        this.listWidget.clearEntries();
        for (ErrorSummaryDTO dto : errors) {
            if (currentFilter != FilterType.ALL && judgeErrorType(dto) != currentFilter) continue;
            if (filter.isEmpty() || dto.path().toLowerCase().contains(filter) || dto.message().toLowerCase().contains(filter)) {
                this.listWidget.add(new ErrorEntry(dto));
            }
        }
    }

    private void refreshStackView() {
        if (this.stackList == null) return;
        this.stackList.clearEntries();
        if (selectedError != null) {
            selectedError.fullDetails().replace("\t", "    ").lines().forEach(line -> this.stackList.addEntry(new TextLineEntry(line)));
        }
    }

    private void selectError(ErrorSummaryDTO dto) {
        this.selectedError = dto;
        if (this.isEditing) { this.isEditing = false; this.buildDashboardLayout(); }
        else { this.refreshStackView(); }
    }

    private void actionLocate() {
        if (selectedError == null) return;
        Util.getPlatform().openFile(new File(Minecraft.getInstance().gameDirectory, "nekojs/" + selectedError.path()));
        showToast("§a✔ 尝试打开文件");
    }
    private void actionLog() {
        Util.getPlatform().openFile(new File(Minecraft.getInstance().gameDirectory, "logs/latest.log"));
        showToast("§a✔ 打开 latest.log");
    }
    private void actionCopy() {
        if (selectedError == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(selectedError.fullDetails());
        showToast("§a✔ 已存入剪贴板");
    }
    private void actionSave() {
        if (selectedError == null || codeEditor == null) return;
        try {
            Path path = new File(Minecraft.getInstance().gameDirectory, "nekojs/" + selectedError.path()).toPath();
            Files.writeString(path, this.codeEditor.getValue());
            showToast("§a✔ 已保存修改");
            this.isEditing = false; this.buildDashboardLayout();
        } catch (Exception e) { showToast("§c✖ 保存失败: " + e.getMessage()); }
    }

    private void showToast(String msg) {
        this.toastMessage = msg;
        this.toastTime = System.currentTimeMillis() + 2000;
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

        if (!isMaximized) {
            int tabX = 15, tabY = 32;
            for (FilterType type : FilterType.values()) {
                String lbl = type.label + " §8" + getCount(type);
                int lw = this.font.width(lbl);
                boolean isCur = currentFilter == type;
                if (isCur) {
                    graphics.fill(tabX - 4, tabY - 2, tabX + lw + 4, tabY + 12, 0x20FFFFFF);
                    graphics.fill(tabX - 4, tabY + 11, tabX + lw + 4, tabY + 12, type.color);
                }
                graphics.text(this.font, lbl, tabX, tabY, isCur ? -1 : 0xFF666666);
                tabX += lw + 12;
            }
        }

        if (isEditing && codeEditor != null) {
            codeEditor.renderUnderlay(graphics);
        } else {
            graphics.fill(layoutRightX, layoutContentY, layoutRightX + layoutRightW, layoutContentY + layoutContentH, 0xFF1E1E1E);
        }

        super.extractRenderState(graphics, activeContextMenu != null ? -999 : mouseX, activeContextMenu != null ? -999 : mouseY, partialTick);

        renderHeaderButtons(graphics, mouseX, mouseY);

        boolean isDirty = isEditing && codeEditor != null && codeEditor.isDirty();
        int decorColor = isEditing ? (isDirty ? 0xFFFFDD00 : 0xFF44FF44) : (selectedError != null ? judgeErrorType(selectedError).color : 0xFF333333);
        graphics.fill(layoutRightX, layoutContentY, layoutRightX + 2, layoutContentY + layoutContentH, decorColor);
        graphics.outline(layoutRightX, layoutContentY, layoutRightW, layoutContentH, 0xFF333333);

        if (System.currentTimeMillis() < toastTime) {
            float yAnim = Mth.clamp((2000f - (toastTime - System.currentTimeMillis())) / 150f, 0, 1);
            int tw = this.font.width(toastMessage) + 20;
            int ty = this.height - 45 - (int)(15 * yAnim);
            graphics.fill(this.width/2-tw/2, ty, this.width/2+tw/2, ty+16, 0xCC000000);
            graphics.outline(this.width/2-tw/2, ty, tw, 16, 0xFF44FF44);
            graphics.centeredText(this.font, toastMessage, this.width / 2, ty + 4, -1);
        }

        if (this.activeContextMenu != null) this.activeContextMenu.render(graphics, mouseX, mouseY);
    }

    private void renderHeaderButtons(GuiGraphicsExtractor g, int mx, int my) {
        if (selectedError == null) return;
        boolean isDirty = isEditing && codeEditor != null && codeEditor.isDirty();

        String title = isEditing ? "§8编辑: §a" + selectedError.path() : "§8目标: §e" + selectedError.path();
        if (isDirty) title += " §e(未保存)";

        g.text(this.font, title, layoutRightX + 5, 32, -1);

        int curX = layoutRightX + layoutRightW - 10;
        int closeX = this.width - 25;
        g.text(this.font, (mx >= closeX && mx <= closeX + 10 && my >= 10 && my <= 20) ? "§c✖" : "§7✖", closeX, 10, -1);
        int maxX = closeX - 25;
        g.text(this.font, (mx >= maxX && mx <= maxX + 10 && my >= 10 && my <= 20) ? "§f" + (isMaximized ? "🗗" : "⛶") : "§7" + (isMaximized ? "🗗" : "⛶"), maxX, 10, -1);

        if (isEditing) {
            curX = renderLink(g, "§c[取消]", curX, mx, my);
            curX = renderLink(g, isDirty ? "§e[保存*]" : "§a[保存]", curX, mx, my);
        } else {
            curX = renderLink(g, "§7[复制]", curX, mx, my);
            curX = renderLink(g, "§b[日志]", curX, mx, my);
            curX = renderLink(g, "§e[定位]", curX, mx, my);
            curX = renderLink(g, "§a[编辑]", curX, mx, my);
        }
    }

    private int renderLink(GuiGraphicsExtractor g, String text, int x, int mx, int my) {
        int w = this.font.width(text);
        int targetX = x - w - 5;
        boolean hov = mx >= targetX && mx <= targetX + w && my >= 32 && my <= 42;

        g.text(this.font, text, targetX, 32, -1);
        if (hov) g.fill(targetX, 32 + this.font.lineHeight, targetX + w, 32 + this.font.lineHeight + 1, 0xFFFFFFFF);

        return targetX;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.activeContextMenu != null && event.isEscape()) {
            this.activeContextMenu = null; return true;
        }
        if (this.isEditing && event.isEscape()) {
            this.isEditing = false; this.buildDashboardLayout(); return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.activeContextMenu != null) {
            this.activeContextMenu.mouseClicked(event.x(), event.y(), event.button());
            this.activeContextMenu = null; return true;
        }

        int closeX = this.width - 25;
        if (event.x() >= closeX && event.x() <= closeX + 10 && event.y() >= 8 && event.y() <= 20) { this.onClose(); return true; }
        int maxX = closeX - 25;
        if (event.x() >= maxX && event.x() <= maxX + 10 && event.y() >= 8 && event.y() <= 20) { this.isMaximized = !this.isMaximized; this.buildDashboardLayout(); return true; }

        if (selectedError != null && event.y() >= 32 && event.y() <= 42) {
            int curX = layoutRightX + layoutRightW - 10;

            if (isEditing) {
                String cancelBtn = "§c[取消]";
                int cw = this.font.width(cancelBtn);
                curX = curX - cw - 5;
                if (event.x() >= curX && event.x() <= curX + cw) { this.isEditing = false; buildDashboardLayout(); return true; }

                boolean isDirty = codeEditor != null && codeEditor.isDirty();
                String saveBtn = isDirty ? "§e[保存*]" : "§a[保存]";
                int sw = this.font.width(saveBtn);
                curX = curX - sw - 5;
                if (event.x() >= curX && event.x() <= curX + sw) { actionSave(); return true; }
            } else {
                String copyBtn = "§7[复制]";
                int cw1 = this.font.width(copyBtn);
                curX = curX - cw1 - 5;
                if (event.x() >= curX && event.x() <= curX + cw1) { actionCopy(); return true; }

                String logBtn = "§b[日志]";
                int cw2 = this.font.width(logBtn);
                curX = curX - cw2 - 5;
                if (event.x() >= curX && event.x() <= curX + cw2) { actionLog(); return true; }

                String locBtn = "§e[定位]";
                int cw3 = this.font.width(locBtn);
                curX = curX - cw3 - 5;
                if (event.x() >= curX && event.x() <= curX + cw3) { actionLocate(); return true; }

                String editBtn = "§a[编辑]";
                int cw4 = this.font.width(editBtn);
                curX = curX - cw4 - 5;
                if (event.x() >= curX && event.x() <= curX + cw4) { this.isEditing = true; buildDashboardLayout(); return true; }
            }
        }

        // 委派点击事件给编辑器（用于选择行）
        if (isEditing && codeEditor != null) {
            codeEditor.mouseClicked(event.x(), event.y(), event.button());
        }

        if (!isMaximized) {
            int tabX = 15, tabY = 32;
            for (FilterType type : FilterType.values()) {
                int lw = this.font.width(type.label + " §8" + getCount(type));
                if (event.x() >= tabX && event.x() <= tabX + lw && event.y() >= tabY && event.y() <= tabY + 12) {
                    this.currentFilter = type; this.refreshList(); return true;
                }
                tabX += lw + 12;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    // ================= 内部类 =================
    private class ErrorListWidget extends ObjectSelectionList<ErrorEntry> {
        public ErrorListWidget(Minecraft mc, int w, int h, int y, int ih) { super(mc, w, h, y, ih); }
        public void add(ErrorEntry entry) { this.addEntry(entry); }
        @Override public int getRowWidth() { return this.width - 15; }
        @Override protected int scrollBarX() { return this.getX() + this.width - 6; }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}
    }

    private class ErrorEntry extends ObjectSelectionList.Entry<ErrorEntry> {
        private final ErrorSummaryDTO dto;
        public ErrorEntry(ErrorSummaryDTO dto) { this.dto = dto; }
        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean isHovered, float pt) {
            boolean isSelected = selectedError != null && selectedError.id().equals(dto.id());
            boolean hov = (NekoErrorDashboardScreen.this.activeContextMenu == null) && isHovered;
            int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight() - 4;

            g.fill(x, y, x + w, y + h, isSelected ? 0x30FFFFFF : (hov ? 0x15FFFFFF : 0x08FFFFFF));
            g.fill(x + 2, y + 4, x + 4, y + h - 4, judgeErrorType(dto).color);

            g.text(NekoErrorDashboardScreen.this.font, (isSelected ? "§e" : "§f") + dto.path(), x + 8, y + 6, -1);
            g.text(NekoErrorDashboardScreen.this.font, "§7" + NekoErrorDashboardScreen.this.font.plainSubstrByWidth(dto.message(), w - 20), x + 8, y + 18, -1);
        }
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (NekoErrorDashboardScreen.this.activeContextMenu != null) return false;
            if (event.button() == 0) {
                selectError(dto); if (doubleClick) { isMaximized = true; buildDashboardLayout(); } return true;
            } else if (event.button() == 1) {
                NekoErrorDashboardScreen.this.activeContextMenu = new ContextMenu((int)event.x(), (int)event.y(), List.of(
                        new MenuItem("⛶ 全屏查看", () -> { selectError(dto); isMaximized = true; buildDashboardLayout(); }),
                        new MenuItem("📂 打开脚本", () -> { Util.getPlatform().openFile(new File(Minecraft.getInstance().gameDirectory, "nekojs/" + dto.path())); showToast("§a✔ 指令已发送"); })
                ));
                return true;
            }
            return false;
        }
        @Override public Component getNarration() { return Component.literal(dto.path()); }
    }

    private class StackTraceList extends ObjectSelectionList<TextLineEntry> {
        public StackTraceList(Minecraft mc, int w, int h, int y, int ih) { super(mc, w, h, y, ih); }
        @Override public int getRowWidth() { return this.width - 20; }
        @Override protected int scrollBarX() { return this.getX() + this.width - 8; }
        @Override protected void extractListBackground(GuiGraphicsExtractor g) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor g) {}
        public int addEntry(TextLineEntry entry) { return super.addEntry(entry); }
    }

    private class TextLineEntry extends ObjectSelectionList.Entry<TextLineEntry> {
        private final String line;
        public TextLineEntry(String line) { this.line = line; }
        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float pt) {
            int color = line.contains("Error") ? 0xFFE5534B : (line.trim().startsWith("at") ? 0xFF888888 : 0xFFCCCCCC);
            g.text(NekoErrorDashboardScreen.this.font, "§7" + line, this.getX() + 5, this.getY() + 2, color);
        }
        @Override public Component getNarration() { return Component.empty(); }
    }

    private class ContextMenu {
        int x, y, width = 120, height; List<MenuItem> items;
        public ContextMenu(int sx, int sy, List<MenuItem> items) {
            this.items = items; this.height = items.size() * 18 + 6;
            this.x = Math.min(sx, NekoErrorDashboardScreen.this.width - width - 5); this.y = Math.min(sy, NekoErrorDashboardScreen.this.height - height - 5);
        }
        public void render(GuiGraphicsExtractor g, int mx, int my) {
            g.fill(x, y, x+width, y+height, 0xFF18181B); g.outline(x, y, width, height, 0xFF3F3F46);
            for (int i = 0; i < items.size(); i++) {
                int iy = y + 3 + i * 18; boolean h = mx >= x && mx <= x+width && my >= iy && my < iy+18;
                if (h) g.fill(x+2, iy, x+width-2, iy+18, 0xFF27272A);
                g.text(NekoErrorDashboardScreen.this.font, items.get(i).label, x+8, iy+5, h ? -1 : 0xFFA1A1AA);
            }
        }
        public boolean mouseClicked(double mx, double my, int b) {
            if (b == 0 && mx >= x && mx <= x+width && my >= y && my <= y+height) {
                int index = ((int)my - y - 3) / 18; if (index >= 0 && index < items.size()) items.get(index).action.run(); return true;
            } return false;
        }
    }
    private record MenuItem(String label, Runnable action) {}
}