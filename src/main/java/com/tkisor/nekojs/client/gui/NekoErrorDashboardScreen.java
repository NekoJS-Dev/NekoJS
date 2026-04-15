package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.components.*;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.*;
import com.tkisor.nekojs.network.dto.ErrorSummaryDTO;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class NekoErrorDashboardScreen extends Screen {
    private final List<ErrorSummaryDTO> errors;

    private ErrorListWidget listWidget;
    private EditBox searchBox;
    private StackTraceList stackList;

    private ErrorSummaryDTO selectedError = null;
    private FilterType currentFilter = FilterType.ALL;

    private boolean isMaximized = false;
    private boolean isEditing = false;

    private NekoTabbedEditor tabbedEditor = null;

    private int layoutRightX, layoutRightW, layoutContentY, layoutContentH;
    private long openTime;

    private final int[] filterCounts = new int[FilterType.VALUES.length];
    private final String[] filterLabels = new String[FilterType.VALUES.length];

    private final NekoToast toast = new NekoToast();
    private NekoModal modal;
    private NekoContextMenu activeContextMenu = null;

    private enum FilterType {
        ALL("nekojs.gui.filter.all", 0xFFFFFFFF),
        RUNTIME("nekojs.gui.filter.runtime", 0xFFE5534B),
        SYNTAX("nekojs.gui.filter.syntax", 0xFFF2A134),
        OTHER("nekojs.gui.filter.other", 0xFF748394);
        final String key; final int color;
        FilterType(String key, int color) { this.key = key; this.color = color; }
        public static final FilterType[] VALUES = values();
    }

    private record MenuCategory(String name, List<NekoContextMenu.MenuItem> items) {}
    private final List<MenuCategory> menuCategories = List.of(
            new MenuCategory(I18n.get("nekojs.gui.menu.file"), List.of(
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.file.save"), this::actionSaveActiveTab),
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.file.open_external"), this::actionLocate),
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.file.exit"), this::onClose)
            )),
            new MenuCategory(I18n.get("nekojs.gui.menu.sync"), List.of(
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.sync.push_current"), this::actionSyncUploadCurrent),
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.sync.pull_current"), this::actionSyncDownloadCurrent),
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.sync.push_all"), this::actionSyncUploadAll),
                    new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.menu.sync.pull_all"), this::actionSyncDownloadAll)
            ))
    );

    public NekoErrorDashboardScreen(List<ErrorSummaryDTO> errors) {
        super(Component.translatable("nekojs.gui.dashboard.title"));
        this.errors = errors;
        if (!errors.isEmpty()) this.selectedError = errors.get(0);

        for (ErrorSummaryDTO dto : errors) {
            filterCounts[FilterType.ALL.ordinal()]++;
            filterCounts[judgeErrorType(dto).ordinal()]++;
        }
        for (FilterType type : FilterType.VALUES) {
            filterLabels[type.ordinal()] = I18n.get(type.key) + " §8" + filterCounts[type.ordinal()];
        }
    }

    @Override
    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();

        if (this.modal == null) {
            this.modal = new NekoModal(this.font, () -> this.setFocused(null));
        }

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
            this.searchBox = new EditBox(this.font, margin, 18, leftW, 12, Component.translatable("nekojs.gui.search.placeholder"));
            this.searchBox.setHint(Component.translatable("nekojs.gui.search.hint"));
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
        } else {
            if (tabbedEditor == null) {
                tabbedEditor = new NekoTabbedEditor(this.font, layoutRightX, layoutContentY, layoutRightW, layoutContentH,
                        this::doSaveTab,
                        () -> { this.isEditing = false; buildDashboardLayout(); },
                        this::buildDashboardLayout);
            } else {
                tabbedEditor.setBounds(layoutRightX, layoutContentY, layoutRightW, layoutContentH);
            }

            if (tabbedEditor.getActiveTab() != null && tabbedEditor.getActiveTab().editor != null) {
                this.addRenderableWidget(tabbedEditor.getActiveTab().editor.getWidget());
                if (!modal.isOpen()) {
                    this.setFocused(tabbedEditor.getActiveTab().editor.getWidget());
                }
            }
        }

        this.modal.updateBounds(this.width, this.height);
        this.addRenderableWidget(this.modal.getWidget());
        if (this.modal.isInputMode()) this.setFocused(this.modal.getWidget());
    }

    private String getInitialTextForPath(String targetPath) {
        try {
            Path p = NekoJSPaths.ROOT.resolve(targetPath);
            if (Files.exists(p)) return Files.readString(p);
        } catch (Exception e) { return "// " + I18n.get("nekojs.gui.toast.error.read_fail", e.getMessage()); }
        return "";
    }

    public void openTab(String path) {
        this.isEditing = true;
        if (tabbedEditor == null) buildDashboardLayout();
        String text = getInitialTextForPath(path);
        tabbedEditor.openTab(path, text);
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
        this.listWidget.setScrollAmount(0);
    }

    private void refreshStackView() {
        if (this.stackList == null) return;
        this.stackList.clearEntries();
        if (selectedError != null) {
            int maxLineW = Math.max(100, this.stackList.getRowWidth() - 10);

            selectedError.fullDetails().replace("\t", "    ").lines().forEach(rawLine -> {
                if (rawLine.trim().isEmpty()) {
                    this.stackList.addEntry(new TextLineEntry("", 0xFFCCCCCC));
                    return;
                }
                int color = rawLine.contains("Error") ? 0xFFE5534B : (rawLine.trim().startsWith("at") ? 0xFF888888 : 0xFFCCCCCC);
                List<net.minecraft.network.chat.FormattedText> wrappedLines =
                        this.font.getSplitter().splitLines(rawLine, maxLineW, net.minecraft.network.chat.Style.EMPTY);
                for (net.minecraft.network.chat.FormattedText wrapped : wrappedLines) {
                    this.stackList.addEntry(new TextLineEntry(wrapped.getString(), color));
                }
            });
        }
    }

    private void selectError(ErrorSummaryDTO dto) {
        this.selectedError = dto;
        if (!this.isEditing) { this.refreshStackView(); }
    }

    private void actionLocate() {
        if (selectedError == null) return;
        Util.getPlatform().openFile(NekoJSPaths.ROOT.resolve(selectedError.path()).toFile());
        toast.show(I18n.get("nekojs.gui.toast.locate_success"));
    }

    private void actionLog() {
        Util.getPlatform().openFile(NekoJSPaths.GAME_DIR.resolve("logs/latest.log").toFile());
        toast.show(I18n.get("nekojs.gui.toast.log_success"));
    }

    private void actionCopy() {
        if (selectedError == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(selectedError.fullDetails());
        toast.show(I18n.get("nekojs.gui.toast.copy_success"));
    }

    private void actionSaveActiveTab() {
        if (tabbedEditor != null && tabbedEditor.getActiveTab() != null) {
            doSaveTab(tabbedEditor.getActiveTab());
        }
    }

    private void doSaveTab(NekoTabbedEditor.Tab tab) {
        if (tab == null || tab.editor == null) return;
        try {
            Path path = NekoJSPaths.ROOT.resolve(tab.path);
            Files.writeString(path, tab.editor.getValue());
            toast.show(I18n.get("nekojs.gui.toast.save_success", tab.path));
            tab.editor.markSaved();
        } catch (Exception e) { toast.show(I18n.get("nekojs.gui.toast.save_fail", e.getMessage())); }
    }

    public void loadServerScript(String content) {
        if (isEditing && tabbedEditor != null && tabbedEditor.getActiveTab() != null) {
            tabbedEditor.getActiveTab().editor.getWidget().setValue(content);
            tabbedEditor.getActiveTab().editor.markSaved();
            this.toast.show(I18n.get("nekojs.gui.toast.pull_success"));
        }
    }

    private void actionSyncUploadCurrent() {
        if (!this.isEditing || tabbedEditor == null || tabbedEditor.getActiveTab() == null) {
            this.toast.show(I18n.get("nekojs.gui.toast.error.not_editing"));
            return;
        }
        ClientPacketDistributor.sendToServer(new SaveScriptPacket(tabbedEditor.getActiveTab().path, tabbedEditor.getActiveTab().editor.getValue()));
        toast.show(I18n.get("nekojs.gui.toast.pushing_current"));
    }

    private void actionSyncDownloadCurrent() {
        if (!this.isEditing || tabbedEditor == null || tabbedEditor.getActiveTab() == null) {
            this.toast.show(I18n.get("nekojs.gui.toast.error.not_editing"));
            return;
        }
        ClientPacketDistributor.sendToServer(new FetchScriptRequestPacket(tabbedEditor.getActiveTab().path));
        toast.show(I18n.get("nekojs.gui.toast.pulling_current"));
    }

    private void actionSyncUploadAll() {
        toast.show(I18n.get("nekojs.gui.toast.pushing_all"));
        Map<String, String> localFiles = NekoJSNetwork.collectAllValidScripts(NekoJSPaths.ROOT);

        if (localFiles.isEmpty()) {
            toast.show(I18n.get("nekojs.gui.toast.error.empty_dir"));
            return;
        }
        ClientPacketDistributor.sendToServer(new UploadAllScriptsPacket(localFiles));
    }

    private void actionSyncDownloadAll() {
        toast.show(I18n.get("nekojs.gui.toast.pulling_all"));
        ClientPacketDistributor.sendToServer(new FetchAllScriptsRequestPacket());
    }

    public void onSyncFeedback(boolean success, String message) {
        String prefix = success ? "§a✔ " : "§c✖ ";
        this.toast.show(prefix + message);

        if (success && this.isEditing && tabbedEditor != null && tabbedEditor.getActiveTab() != null) {
            tabbedEditor.getActiveTab().editor.markSaved();
        }
    }

    private FilterType judgeErrorType(ErrorSummaryDTO dto) {
        String msg = dto.message().toLowerCase();
        if (msg.contains("syntax") || msg.contains("unexpected")) return FilterType.SYNTAX;
        if (msg.contains("null") || msg.contains("undefined") || msg.contains("error")) return FilterType.RUNTIME;
        return FilterType.OTHER;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF0A0A0B, 0xFF121214);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        float fade = Mth.clamp((System.currentTimeMillis() - openTime) / 300f, 0, 1);

        graphics.fill(0, 0, this.width, 14, 0xFF18181A);
        graphics.fill(0, 14, this.width, 50, ARGB.color((int)(fade * 180), 12, 12, 12));
        graphics.fill(0, 50, this.width, 51, 0x30FFFFFF);

        graphics.text(this.font, "§cNEKO§fJS", 15, 21, -1);

        int menuX = 5;
        for (MenuCategory cat : menuCategories) {
            int w = this.font.width(cat.name());
            boolean hov = mouseX >= menuX && mouseX <= menuX + w + 8 && mouseY >= 0 && mouseY <= 14;
            if (hov && this.activeContextMenu == null) {
                graphics.fill(menuX, 0, menuX + w + 8, 14, 0xFF3E3E42);
            }
            graphics.text(this.font, cat.name(), menuX + 4, 3, 0xFFCCCCCC);
            menuX += w + 8;
        }

        if (!isMaximized) {
            int tabX = 15, tabY = 38;
            for (FilterType type : FilterType.VALUES) {
                String lbl = filterLabels[type.ordinal()];
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

        if (isEditing && tabbedEditor != null) {
            tabbedEditor.renderUnderlay(graphics, mouseX, mouseY);
        } else {
            graphics.fill(layoutRightX, layoutContentY, layoutRightX + layoutRightW, layoutContentY + layoutContentH, 0xFF1E1E1E);
        }

        boolean blockEditorHover = modal.isOpen() || (activeContextMenu != null) || (tabbedEditor != null && tabbedEditor.isHoveringDropdown(mouseX, mouseY));
        super.extractRenderState(graphics, blockEditorHover ? -999 : mouseX, blockEditorHover ? -999 : mouseY, partialTick);

        renderHeaderButtons(graphics, mouseX, mouseY);

        boolean isDirty = isEditing && tabbedEditor != null && tabbedEditor.getActiveTab() != null && tabbedEditor.getActiveTab().editor.isDirty();
        int decorColor = isEditing ? (isDirty ? 0xFFFFDD00 : 0xFF44FF44) : (selectedError != null ? judgeErrorType(selectedError).color : 0xFF333333);
        graphics.fill(layoutRightX, layoutContentY, layoutRightX + 2, layoutContentY + layoutContentH, decorColor);
        graphics.outline(layoutRightX, layoutContentY, layoutRightW, layoutContentH, 0xFF333333);

        this.toast.render(graphics, this.font, this.width, this.height);
        if (this.activeContextMenu != null) this.activeContextMenu.render(graphics, mouseX, mouseY);
        this.modal.render(graphics, mouseX, mouseY, partialTick, this.width, this.height);
    }

    private boolean handleHeaderButton(GuiGraphicsExtractor g, Component text, int x, int mx, int my, boolean isClickAction) {
        int w = this.font.width(text);
        int targetX = x - w - 5;
        boolean hov = mx >= targetX && mx <= targetX + w && my >= 38 && my <= 48;
        if (!isClickAction && g != null) {
            g.text(this.font, text, targetX, 38, -1);
            if (hov) g.fill(targetX, 38 + this.font.lineHeight, targetX + w, 38 + this.font.lineHeight + 1, 0xFFFFFFFF);
        }
        return hov;
    }

    private void renderHeaderButtons(GuiGraphicsExtractor g, int mx, int my) {
        if (selectedError == null) return;
        boolean isDirty = isEditing && tabbedEditor != null && tabbedEditor.getActiveTab() != null && tabbedEditor.getActiveTab().editor.isDirty();

        if (!isEditing) {
            g.text(this.font, I18n.get("nekojs.gui.dashboard.target", selectedError.path()), layoutRightX + 5, 38, -1);
        }

        int closeX = this.width - 25;
        g.text(this.font, (mx >= closeX && mx <= closeX + 10 && my >= 16 && my <= 26) ? "§c✖" : "§7✖", closeX, 16, -1);
        int maxX = closeX - 25;
        g.text(this.font, (mx >= maxX && mx <= maxX + 10 && my >= 16 && my <= 26) ? "§f" + (isMaximized ? "🗗" : "⛶") : "§7" + (isMaximized ? "🗗" : "⛶"), maxX, 16, -1);

        int curX = layoutRightX + layoutRightW - 10;
        if (isEditing) {
            handleHeaderButton(g, Component.translatable("nekojs.gui.dashboard.btn.exit_edit"), curX, mx, my, false);
            curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.exit_edit")) + 5;
            handleHeaderButton(g, Component.translatable(isDirty ? "nekojs.gui.dashboard.btn.save_dirty" : "nekojs.gui.dashboard.btn.save"), curX, mx, my, false);
        } else {
            handleHeaderButton(g, Component.translatable("nekojs.gui.dashboard.btn.copy"), curX, mx, my, false);
            curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.copy")) + 5;
            handleHeaderButton(g, Component.translatable("nekojs.gui.dashboard.btn.log"), curX, mx, my, false);
            curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.log")) + 5;
            handleHeaderButton(g, Component.translatable("nekojs.gui.dashboard.btn.locate"), curX, mx, my, false);
            curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.locate")) + 5;
            handleHeaderButton(g, Component.translatable("nekojs.gui.dashboard.btn.edit"), curX, mx, my, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (modal.isOpen() || activeContextMenu != null) return true;
        if (this.isEditing && tabbedEditor != null) {
            if (tabbedEditor.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modal.isOpen()) {
            if (modal.keyPressed(event)) return true;
            return super.keyPressed(event);
        }
        if (this.activeContextMenu != null && event.isEscape()) {
            this.activeContextMenu = null; return true;
        }
        if (this.isEditing && tabbedEditor != null) {
            if (tabbedEditor.keyPressed(event)) return true;
            if (event.isEscape()) {
                this.isEditing = false;
                this.buildDashboardLayout();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (modal.isOpen()) return super.charTyped(event);
        if (this.activeContextMenu != null) return false;
        if (this.isEditing && tabbedEditor != null) {
            if (tabbedEditor.charTyped((char) event.codepoint())) return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (modal.isOpen()) {
            if (modal.mouseClicked(event, this.width, this.height)) return true;
            return true;
        }

        if (this.activeContextMenu != null) {
            this.activeContextMenu.mouseClicked(event.x(), event.y(), event.button());
            this.activeContextMenu = null;
            return true;
        }

        if (event.y() >= 0 && event.y() <= 14) {
            int menuX = 5;
            for (MenuCategory cat : menuCategories) {
                int w = this.font.width(cat.name()) + 8;
                if (event.x() >= menuX && event.x() <= menuX + w) {
                    this.activeContextMenu = new NekoContextMenu(this.font, menuX, 14, this.width, this.height, cat.items());
                    return true;
                }
                menuX += w;
            }
        }

        int closeX = this.width - 25;
        if (event.x() >= closeX && event.x() <= closeX + 10 && event.y() >= 16 && event.y() <= 26) { this.onClose(); return true; }
        int maxX = closeX - 25;
        if (event.x() >= maxX && event.x() <= maxX + 10 && event.y() >= 16 && event.y() <= 26) { this.isMaximized = !this.isMaximized; this.buildDashboardLayout(); return true; }

        if (isEditing && tabbedEditor != null) {
            if (tabbedEditor.mouseClicked(event.x(), event.y(), event.button())) return true;
        }

        if (selectedError != null && event.y() >= 38 && event.y() <= 48) {
            int curX = layoutRightX + layoutRightW - 10;
            int mx = (int) event.x();
            int my = (int) event.y();

            if (isEditing) {
                if (handleHeaderButton(null, Component.translatable("nekojs.gui.dashboard.btn.exit_edit"), curX, mx, my, true)) {
                    this.isEditing = false; buildDashboardLayout(); return true;
                }
                curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.exit_edit")) + 5;

                boolean isDirty = tabbedEditor != null && tabbedEditor.getActiveTab() != null && tabbedEditor.getActiveTab().editor.isDirty();
                if (handleHeaderButton(null, Component.translatable(isDirty ? "nekojs.gui.dashboard.btn.save_dirty" : "nekojs.gui.dashboard.btn.save"), curX, mx, my, true)) {
                    actionSaveActiveTab(); return true;
                }
            } else {
                if (handleHeaderButton(null, Component.translatable("nekojs.gui.dashboard.btn.copy"), curX, mx, my, true)) { actionCopy(); return true; }
                curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.copy")) + 5;

                if (handleHeaderButton(null, Component.translatable("nekojs.gui.dashboard.btn.log"), curX, mx, my, true)) { actionLog(); return true; }
                curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.log")) + 5;

                if (handleHeaderButton(null, Component.translatable("nekojs.gui.dashboard.btn.locate"), curX, mx, my, true)) { actionLocate(); return true; }
                curX -= this.font.width(I18n.get("nekojs.gui.dashboard.btn.locate")) + 5;

                if (handleHeaderButton(null, Component.translatable("nekojs.gui.dashboard.btn.edit"), curX, mx, my, true)) { openTab(selectedError.path()); return true; }
            }
        }

        if (!isMaximized) {
            int tabX = 15, tabY = 38;
            for (FilterType type : FilterType.VALUES) {
                int lw = this.font.width(filterLabels[type.ordinal()]);
                if (event.x() >= tabX && event.x() <= tabX + lw && event.y() >= tabY && event.y() <= tabY + 12) {
                    this.currentFilter = type;
                    this.refreshList();
                    return true;
                }
                tabX += lw + 12;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

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
                if (doubleClick) {
                    NekoErrorDashboardScreen.this.openTab(dto.path());
                }
                return true;
            } else if (event.button() == 1) {
                selectError(dto);
                NekoErrorDashboardScreen.this.activeContextMenu = new NekoContextMenu(NekoErrorDashboardScreen.this.font, (int)event.x(), (int)event.y(), NekoErrorDashboardScreen.this.width, NekoErrorDashboardScreen.this.height, List.of(
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.fullscreen"), () -> { isMaximized = true; buildDashboardLayout(); }),
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.open_tab"), () -> { NekoErrorDashboardScreen.this.openTab(dto.path()); }),
                        new NekoContextMenu.MenuItem(I18n.get("nekojs.gui.context.open_external"), () -> { Util.getPlatform().openFile(NekoJSPaths.ROOT.resolve(dto.path()).toFile()); toast.show(I18n.get("nekojs.gui.toast.cmd_sent")); })
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
        private final int color;

        public TextLineEntry(String line, int color) {
            this.line = line;
            this.color = color;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mx, int my, boolean hov, float pt) {
            g.text(NekoErrorDashboardScreen.this.font, "§7" + this.line, this.getX() + 5, this.getY() + 2, this.color);
        }
        @Override public Component getNarration() { return Component.empty(); }
    }
}