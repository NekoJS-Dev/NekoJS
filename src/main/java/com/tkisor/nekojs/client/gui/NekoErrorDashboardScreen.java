package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.components.NekoContextMenu;
import com.tkisor.nekojs.client.gui.components.TextLinkWidget;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class NekoErrorDashboardScreen extends Screen {
    private final List<ErrorSummaryDTO> errors;

    private ErrorListWidget listWidget;
    private EditBox searchBox;
    private StackTraceList stackList;
    private MultiLineEditBox editorBox;
    private NekoContextMenu activeContextMenu = null;

    private ErrorSummaryDTO selectedError = null;
    private FilterType currentFilter = FilterType.ALL;

    private boolean isMaximized = false;
    private boolean isEditing = false;

    // 智能折行与高亮核心
    private int selectedEditorLine = -1;
    private final List<Integer> visualToRealLineMap = new ArrayList<>();
    private String lastMappedText = null;
    private final int GUTTER_WIDTH = 32;

    private int rightX, rightW, contentY, contentH;
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

        // 🌟 初始化持久化组件 (不再反复 clearWidgets)
        this.searchBox = new EditBox(this.font, 0, 0, 0, 12, Component.empty());
        this.searchBox.setHint(Component.literal("§8过滤路径..."));
        this.searchBox.setResponder(s -> this.refreshList());
        this.addRenderableWidget(this.searchBox);

        this.listWidget = new ErrorListWidget(this.minecraft, 0, 0, 0, 36);
        this.addRenderableWidget(this.listWidget);

        this.stackList = new StackTraceList(this.minecraft, 0, 0, 0, 12);
        this.addRenderableWidget(this.stackList);

        this.editorBox = MultiLineEditBox.builder().setShowBackground(false).build(this.font, 0, 0, Component.empty());
        this.addRenderableWidget(this.editorBox);

        this.refreshList();
        this.refreshStackView();

        this.updateLayout();
    }

    // 🌟 动态计算并更新所有组件的边界
    private void updateLayout() {
        int margin = 15;
        int leftW = isMaximized ? 0 : Math.max(160, (int) (this.width * 0.35));

        this.rightX = isMaximized ? margin : leftW + margin * 2;
        this.rightW = this.width - rightX - margin;
        this.contentY = 55;
        this.contentH = this.height - contentY - margin;

        this.searchBox.visible = !isMaximized;
        this.listWidget.visible = !isMaximized;
        this.stackList.visible = !isEditing;
        this.editorBox.visible = isEditing;

        if (!isMaximized) {
            this.searchBox.setX(margin); this.searchBox.setY(8); this.searchBox.setWidth(leftW);
            this.listWidget.setX(margin); this.listWidget.setY(contentY); this.listWidget.setWidth(leftW); this.listWidget.setHeight(contentH);
        }

        this.stackList.setX(rightX + 1); this.stackList.setY(contentY + 2); this.stackList.setWidth(rightW - 2); this.stackList.setHeight(contentH - 4);

        this.editorBox.setX(rightX + GUTTER_WIDTH + 2); this.editorBox.setY(contentY + 4);
        this.editorBox.setWidth(rightW - GUTTER_WIDTH - 6); this.editorBox.setHeight(contentH - 8);

        rebuildHeaderButtons();
    }

    private void rebuildHeaderButtons() {
        this.children().removeIf(w -> w instanceof TextLinkWidget);
        this.renderables.removeIf(w -> w instanceof TextLinkWidget);

        if (selectedError == null) return;

        int curX = rightX + rightW - 10;

        if (isEditing) {
            curX = addLinkBtn("§c[取消]", curX, () -> { this.isEditing = false; this.selectedEditorLine = -1; this.updateLayout(); });
            addLinkBtn("§a[保存]", curX, this::actionSave);
        } else {
            curX = addLinkBtn("§7[复制]", curX, () -> { NekoDashboardActions.copyToClipboard(selectedError.fullDetails()); showToast("§a✔ 已复制"); });
            curX = addLinkBtn("§b[日志]", curX, () -> { NekoDashboardActions.openLog(); showToast("§a✔ 打开日志"); });
            curX = addLinkBtn("§e[定位]", curX, () -> { NekoDashboardActions.locateScript(selectedError.path()); showToast("§a✔ 尝试定位"); });
            addLinkBtn("§a[编辑]", curX, () -> {
                this.isEditing = true;
                this.selectedEditorLine = -1;
                this.editorBox.setValue(NekoDashboardActions.readScript(selectedError.path()));
                this.lastMappedText = null;
                this.updateLayout();
                this.setFocused(this.editorBox);
            });
        }
    }

    private int addLinkBtn(String text, int rightAlignX, Runnable action) {
        int w = this.font.width(text);
        int targetX = rightAlignX - w - 5;
        this.addRenderableWidget(new TextLinkWidget(targetX, 32, Component.literal(text), this.font, 0xFFCCCCCC, 0xFFFFFFFF, action));
        return targetX;
    }

    private void actionSave() {
        if (NekoDashboardActions.saveScript(selectedError.path(), this.editorBox.getValue())) {
            showToast("§a✔ 已保存修改");
            this.isEditing = false;
            this.selectedEditorLine = -1;
            this.updateLayout();
        } else {
            showToast("§c✖ 保存失败");
        }
    }

    private void showToast(String msg) { this.toastMessage = msg; this.toastTime = System.currentTimeMillis() + 2000; }

    private void updateLineMap() {
        if (editorBox == null || !editorBox.visible) return;
        String text = editorBox.getValue();
        if (text.equals(lastMappedText)) return;
        lastMappedText = text;
        visualToRealLineMap.clear();

        int innerWidth = editorBox.getWidth() - 8;
        String[] realLines = text.split("\n", -1);

        for (int i = 0; i < realLines.length; i++) {
            int wrappedCount = this.font.getSplitter().splitLines(realLines[i], innerWidth, net.minecraft.network.chat.Style.EMPTY).size();
            if (wrappedCount == 0) wrappedCount = 1;
            for (int j = 0; j < wrappedCount; j++) visualToRealLineMap.add(i + 1);
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
        if (this.isEditing) {
            this.isEditing = false; this.selectedEditorLine = -1; this.updateLayout();
        } else {
            this.refreshStackView();
        }
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
                if (isCur) graphics.fill(tabX, tabY + 12, tabX + lw, tabY + 13, type.color);
                graphics.text(this.font, lbl, tabX, tabY, isCur ? -1 : 0xFF666666);
                tabX += lw + 12;
            }

            String clearBtn = "§7[ 清空记录 ]";
            int clearX = rightX - this.font.width(clearBtn) - 15;
            boolean clearHover = mouseX >= clearX && mouseX <= clearX + this.font.width(clearBtn) && mouseY >= 11 && mouseY <= 20;
            graphics.text(this.font, clearHover ? "§f§n[ 清空记录 ]" : clearBtn, clearX, 11, -1);
        }

        if (selectedError != null) {
            String title = isEditing ? "§8编辑: §a" + selectedError.path() : "§8目标: §e" + selectedError.path();
            graphics.text(this.font, title, rightX, 32, -1);
        }

        int closeX = this.width - 25;
        graphics.text(this.font, (mouseX >= closeX && mouseX <= closeX + 10 && mouseY >= 8 && mouseY <= 20) ? "§c✖" : "§7✖", closeX, 10, -1);
        int maxX = closeX - 25;
        graphics.text(this.font, (mouseX >= maxX && mouseX <= maxX + 10 && mouseY >= 8 && mouseY <= 20) ? "§f" + (isMaximized ? "🗗" : "⛶") : "§7" + (isMaximized ? "🗗" : "⛶"), maxX, 10, -1);

        graphics.fill(rightX, contentY, rightX + rightW, contentY + contentH, 0xFF1E1E1E);

        if (isEditing) renderEditorExtensions(graphics);

        super.extractRenderState(graphics, activeContextMenu != null ? -999 : mouseX, activeContextMenu != null ? -999 : mouseY, partialTick);

        int decorColor = isEditing ? 0xFF44FF44 : (selectedError != null ? judgeErrorType(selectedError).color : 0xFF333333);
        graphics.fill(rightX, contentY, rightX + 2, contentY + contentH, decorColor);
        graphics.outline(rightX, contentY, rightW, contentH, 0xFF333333);

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

    private void renderEditorExtensions(GuiGraphicsExtractor g) {
        updateLineMap();
        int totalVisualLines = visualToRealLineMap.size();

        int innerX = rightX + 2;
        int innerY = contentY + 2;
        int innerH = contentH - 4;

        g.fill(innerX, innerY, innerX + GUTTER_WIDTH, innerY + innerH, 0xFF181818);

        int fontHeight = 9;
        double scroll = editorBox.scrollAmount();
        int startLine = (int) (scroll / fontHeight);
        int maxVisibleLines = innerH / fontHeight + 1;
        int textStartY = contentY + 8;

        for (int i = 0; i <= maxVisibleLines; i++) {
            int visualIdx = startLine + i;
            if (visualIdx >= totalVisualLines) break;

            int realLineNum = visualToRealLineMap.get(visualIdx);
            int y = textStartY + (i * fontHeight) - (int) (scroll % fontHeight);

            if (y < innerY || y > innerY + innerH - fontHeight) continue;

            boolean isCurrentLine = (realLineNum - 1 == selectedEditorLine);

            if (isCurrentLine) {
                g.fill(innerX, y, rightX + rightW - 2, y + fontHeight, 0x1AFFFFFF);
                g.fill(innerX + GUTTER_WIDTH, y, rightX + rightW - 2, y + fontHeight, 0x15FFFFFF);
            }

            if (visualIdx > 0 && visualToRealLineMap.get(visualIdx - 1) == realLineNum) continue;

            String numStr = String.valueOf(realLineNum);
            int numW = this.font.width(numStr);
            int numColor = isCurrentLine ? 0xFFDDDDDD : 0xFF555555;
            g.text(this.font, numStr, innerX + GUTTER_WIDTH - 4 - numW, y, numColor);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.activeContextMenu != null && event.isEscape()) {
            this.activeContextMenu = null; return true;
        }
        if (this.isEditing && event.isEscape()) {
            this.isEditing = false; this.selectedEditorLine = -1; this.updateLayout(); return true;
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
        if (event.x() >= maxX && event.x() <= maxX + 10 && event.y() >= 8 && event.y() <= 20) { this.isMaximized = !this.isMaximized; this.updateLayout(); return true; }

        if (isEditing && editorBox != null) {
            if (event.x() >= rightX + GUTTER_WIDTH && event.x() <= rightX + rightW && event.y() >= contentY && event.y() <= contentY + contentH) {
                double relativeY = event.y() - (contentY + 8) + editorBox.scrollAmount();
                if (relativeY >= 0) {
                    int visualLine = (int) (relativeY / 9);
                    updateLineMap();
                    if (visualLine >= 0 && visualLine < visualToRealLineMap.size()) {
                        selectedEditorLine = visualToRealLineMap.get(visualLine) - 1;
                    } else selectedEditorLine = -1;
                } else selectedEditorLine = -1;
            }
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
            String clearBtn = "[ 清空记录 ]";
            int clearX = rightX - this.font.width(clearBtn) - 15;
            if (event.x() >= clearX && event.x() <= rightX - 15 && event.y() >= 11 && event.y() <= 20) {
                this.errors.clear(); this.selectedError = null; this.isEditing = false;
                this.refreshList(); this.updateLayout(); showToast("§a✔ 记录已清理"); return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    // ================= 内部类：左侧列表 =================
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
                selectError(dto);
                if (doubleClick) { isMaximized = true; updateLayout(); }
                return true;
            } else if (event.button() == 1) {
                NekoErrorDashboardScreen.this.activeContextMenu = new NekoContextMenu(
                        NekoErrorDashboardScreen.this.width, NekoErrorDashboardScreen.this.height,
                        (int)event.x(), (int)event.y(), NekoErrorDashboardScreen.this.font, List.of(
                        new NekoContextMenu.MenuItem("⛶ 全屏查看", () -> { selectError(dto); isMaximized = true; updateLayout(); }),
                        new NekoContextMenu.MenuItem("📂 打开脚本", () -> { NekoDashboardActions.locateScript(dto.path()); showToast("§a✔ 指令已发送"); })
                ));
                return true;
            }
            return false;
        }
        @Override public Component getNarration() { return Component.literal(dto.path()); }
    }

    // ================= 内部类：右侧堆栈列表 =================
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
}