package com.tkisor.nekojs.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class NekoTabbedEditor {
    public static class Tab {
        public final String path;
        public NekoCodeEditor editor;
        public Tab(String path, NekoCodeEditor editor) {
            this.path = path;
            this.editor = editor;
        }
    }

    private int x, y, width, height;
    private final Font font;
    private final Consumer<Tab> onSave;
    private final Runnable onEmpty;
    private final Runnable onTabChanged;

    private final List<Tab> tabs = new ArrayList<>();
    private Tab activeTab = null;

    private static final int TAB_HEIGHT = 20;
    private static final int DROPDOWN_BTN_WIDTH = 20;

    private double scrollOffset = 0;
    private boolean isDropdownOpen = false;
    private double dropdownScrollOffset = 0;

    public NekoTabbedEditor(Font font, int x, int y, int width, int height, Consumer<Tab> onSave, Runnable onEmpty, Runnable onTabChanged) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onSave = onSave;
        this.onEmpty = onEmpty;
        this.onTabChanged = onTabChanged;
    }

    public void setBounds(int x, int y, int width, int height) {
        boolean changed = this.x != x || this.y != y || this.width != width || this.height != height;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        if (changed) {
            for (Tab tab : tabs) {
                String currentText = tab.editor != null ? tab.editor.getValue() : "";
                String origText = tab.editor != null ? tab.editor.getOriginalScriptText() : currentText;

                tab.editor = new NekoCodeEditor(font, x, y + TAB_HEIGHT, width, height - TAB_HEIGHT, currentText, () -> onSave.accept(tab));
                tab.editor.setOriginalScriptText(origText);
            }
            clampScroll();
        }
    }

    private String getTabLabel(Tab tab) {
        boolean isDirty = tab.editor != null && tab.editor.isDirty();
        return tab.path.substring(tab.path.lastIndexOf('/') + 1) + (isDirty ? "*" : "");
    }

    private int getTabWidth(Tab tab) {
        return font.width(getTabLabel(tab)) + 28;
    }

    private void clampScroll() {
        int totalW = 0;
        for (Tab t : tabs) totalW += getTabWidth(t);
        int visibleW = width - DROPDOWN_BTN_WIDTH;
        int maxScroll = Math.max(0, totalW - visibleW);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private void scrollToTab(Tab target) {
        int currentX = 0;
        for (Tab t : tabs) {
            int tw = getTabWidth(t);
            if (t == target) {
                int visibleW = width - DROPDOWN_BTN_WIDTH;
                if (currentX < scrollOffset) {
                    scrollOffset = currentX;
                } else if (currentX + tw > scrollOffset + visibleW) {
                    scrollOffset = currentX + tw - visibleW;
                }
                break;
            }
            currentX += tw;
        }
        clampScroll();
    }

    public void openTab(String path, String initialText) {
        for (Tab tab : tabs) {
            if (tab.path.equals(path)) {
                activeTab = tab;
                scrollToTab(activeTab);
                onTabChanged.run();
                return;
            }
        }
        NekoCodeEditor editor = new NekoCodeEditor(font, x, y + TAB_HEIGHT, width, height - TAB_HEIGHT, initialText, () -> {
            if (activeTab != null) onSave.accept(activeTab);
        });
        Tab newTab = new Tab(path, editor);
        tabs.add(newTab);
        activeTab = newTab;
        scrollToTab(activeTab);
        onTabChanged.run();
    }

    public void closeTab(Tab tab) {
        tabs.remove(tab);
        if (tabs.isEmpty()) {
            activeTab = null;
            isDropdownOpen = false;
            onEmpty.run();
        } else if (activeTab == tab) {
            activeTab = tabs.get(tabs.size() - 1);
            onTabChanged.run();
        } else {
            onTabChanged.run();
        }
        clampScroll();
    }

    public Tab getActiveTab() { return activeTab; }
    public boolean isEmpty() { return tabs.isEmpty(); }

    public boolean isHoveringDropdown(double mx, double my) {
        if (!isDropdownOpen) return false;
        int menuW = 200;
        int menuX = x + width - menuW;
        int menuY = y + TAB_HEIGHT + 2;
        int itemH = 14;
        int contentH = tabs.size() * itemH;
        int maxAvailableH = Math.max(30, this.height - TAB_HEIGHT - 6);
        int menuH = Math.min(contentH + 6, maxAvailableH);
        return mx >= menuX && mx <= menuX + menuW && my >= menuY && my <= menuY + menuH;
    }

    public void renderUnderlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (tabs.isEmpty()) return;

        g.fill(x, y, x + width, y + TAB_HEIGHT, 0xFF1E1E1E);

        int visibleW = width - DROPDOWN_BTN_WIDTH;
        g.enableScissor(x, y, x + visibleW, y + TAB_HEIGHT);

        int currentX = x - (int) scrollOffset;
        String hoveredTabPath = null;

        for (Tab tab : tabs) {
            int tabW = getTabWidth(tab);

            if (currentX + tabW > x && currentX < x + visibleW) {
                boolean isActive = (tab == activeTab);
                String label = getTabLabel(tab);

                boolean isHoveringTab = mouseX >= currentX && mouseX <= currentX + tabW && mouseY >= y && mouseY <= y + TAB_HEIGHT;
                if (isHoveringTab) hoveredTabPath = tab.path;

                g.fill(currentX, y, currentX + tabW, y + TAB_HEIGHT, isActive ? 0xFF1E1E1E : 0xFF2D2D30);

                if (isActive) {
                    g.fill(currentX, y, currentX + tabW, y + 1, 0xFF007ACC);
                } else {
                    g.fill(currentX + tabW - 1, y + 4, currentX + tabW, y + TAB_HEIGHT - 4, 0x40FFFFFF);
                }

                g.text(font, label, currentX + 8, y + 6, isActive ? 0xFFFFFFFF : 0xFFAAAAAA);

                int closeX = currentX + tabW - 14;
                boolean hoverClose = mouseX >= closeX - 3 && mouseX <= closeX + 9 && mouseY >= y + 3 && mouseY <= y + TAB_HEIGHT - 3;
                if (hoverClose) g.fill(closeX - 3, y + 3, closeX + 9, y + TAB_HEIGHT - 3, 0x33FFFFFF);
                g.text(font, "x", closeX, y + 6, hoverClose ? 0xFFFF7777 : 0xFFAAAAAA);
            }
            currentX += tabW;
        }
        g.disableScissor();

        int totalW = currentX - (x - (int) scrollOffset);
        int maxScroll = Math.max(0, totalW - visibleW);
        if (maxScroll > 0) {
            int handleW = Math.max(20, (int) ((visibleW / (float) totalW) * visibleW));
            int handleX = x + (int) ((scrollOffset / maxScroll) * (visibleW - handleW));
            g.fill(x, y + TAB_HEIGHT - 2, x + visibleW, y + TAB_HEIGHT, 0xFF222222);
            g.fill(handleX, y + TAB_HEIGHT - 2, handleX + handleW, y + TAB_HEIGHT, 0xFF555555);
        }

        int dropX = x + visibleW;
        boolean hoverDrop = mouseX >= dropX && mouseX <= dropX + DROPDOWN_BTN_WIDTH && mouseY >= y && mouseY <= y + TAB_HEIGHT;
        g.fill(dropX, y, dropX + DROPDOWN_BTN_WIDTH, y + TAB_HEIGHT, hoverDrop ? 0xFF3E3E42 : 0xFF252526);
        g.fill(dropX, y + 4, dropX + 1, y + TAB_HEIGHT - 4, 0xFF111111);
        g.text(font, "v", dropX + 7, y + 6, isDropdownOpen ? 0xFFFFFFFF : 0xFFAAAAAA);

        if (activeTab != null && activeTab.editor != null) {
            activeTab.editor.renderUnderlay(g);
        }

        if (isDropdownOpen) {
            int menuW = 200;
            int menuX = x + width - menuW;
            int menuY = y + TAB_HEIGHT + 2;
            int itemH = 14;
            int contentH = tabs.size() * itemH;

            int maxAvailableH = Math.max(30, this.height - TAB_HEIGHT - 6);
            int menuH = Math.min(contentH + 6, maxAvailableH);

            g.fill(menuX, menuY, menuX + menuW, menuY + menuH, 0xFF1E1E1E);
            g.outline(menuX, menuY, menuW, menuH, 0xFF454545);

            g.enableScissor(menuX, menuY + 1, menuX + menuW, menuY + menuH - 1);
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                int iy = menuY + 3 + i * itemH - (int)dropdownScrollOffset;

                if (iy + itemH < menuY || iy > menuY + menuH) continue;

                boolean hovItem = mouseX >= menuX + 1 && mouseX <= menuX + menuW - 1 &&
                        mouseY >= Math.max(menuY, iy) && mouseY < Math.min(menuY + menuH, iy + itemH);

                if (hovItem) g.fill(menuX + 2, iy, menuX + menuW - 2, iy + itemH, 0xFF094771);

                String l = getTabLabel(t);
                if (font.width(l) > menuW - 20) l = font.plainSubstrByWidth(l, menuW - 25) + "...";
                int color = (t == activeTab) ? 0xFF00A2FF : 0xFFCCCCCC;
                g.text(font, l, menuX + 8, iy + 3, color);
            }
            g.disableScissor();

            int maxDropScroll = Math.max(0, contentH - (menuH - 6));
            if (maxDropScroll > 0) {
                int scrollW = 4;
                int trackH = menuH - 2;
                int handleH = Math.max(10, (int) (((menuH - 6) / (float) contentH) * trackH));
                int handleY = menuY + 1 + (int) ((dropdownScrollOffset / maxDropScroll) * (trackH - handleH));

                g.fill(menuX + menuW - scrollW - 1, menuY + 1, menuX + menuW - 1, menuY + menuH - 1, 0xFF222222);
                g.fill(menuX + menuW - scrollW - 1, handleY, menuX + menuW - 1, handleY + handleH, 0xFF555555);
            }
        }

        if (hoveredTabPath != null && !isHoveringDropdown(mouseX, mouseY)) {
            int tooltipW = font.width(hoveredTabPath) + 8;
            int tooltipH = 14;
            int tooltipX = mouseX + 12;
            int tooltipY = mouseY + 12;

            if (tooltipX + tooltipW > this.x + this.width) {
                tooltipX = mouseX - tooltipW - 4;
            }

            g.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xF0151515);
            g.outline(tooltipX, tooltipY, tooltipW, tooltipH, 0xFF555555);
            g.text(font, hoveredTabPath, tooltipX + 4, tooltipY + 3, 0xFFCCCCCC);
        }
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (tabs.isEmpty()) return false;

        if (isDropdownOpen) {
            int menuW = 200;
            int menuX = x + width - menuW;
            int menuY = y + TAB_HEIGHT + 2;
            int itemH = 14;
            int contentH = tabs.size() * itemH;
            int maxAvailableH = Math.max(30, this.height - TAB_HEIGHT - 6);
            int menuH = Math.min(contentH + 6, maxAvailableH);

            if (mx >= menuX && mx <= menuX + menuW && my >= menuY && my <= menuY + menuH) {
                double delta = (scrollY != 0 ? scrollY : scrollX) * 20;
                dropdownScrollOffset -= delta;
                int maxScroll = Math.max(0, contentH - (menuH - 6));
                dropdownScrollOffset = Mth.clamp(dropdownScrollOffset, 0, maxScroll);
                return true;
            }
        }

        if (my >= y && my <= y + TAB_HEIGHT) {
            double delta = (scrollX != 0 ? scrollX : scrollY) * 40;
            scrollOffset -= delta;
            clampScroll();
            return true;
        }

        return false;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (tabs.isEmpty()) return false;

        int visibleW = width - DROPDOWN_BTN_WIDTH;
        int dropX = x + visibleW;

        if (my >= y && my <= y + TAB_HEIGHT && mx >= dropX && mx <= dropX + DROPDOWN_BTN_WIDTH) {
            isDropdownOpen = !isDropdownOpen;
            if (isDropdownOpen) dropdownScrollOffset = 0;
            return true;
        }

        if (isDropdownOpen) {
            int menuW = 200;
            int menuX = x + width - menuW;
            int menuY = y + TAB_HEIGHT + 2;
            int itemH = 14;
            int contentH = tabs.size() * itemH;
            int maxAvailableH = Math.max(30, this.height - TAB_HEIGHT - 6);
            int menuH = Math.min(contentH + 6, maxAvailableH);

            if (mx >= menuX && mx <= menuX + menuW && my >= menuY && my <= menuY + menuH) {
                int clickedYOffset = (int) (my - menuY - 3 + dropdownScrollOffset);
                int index = clickedYOffset / itemH;

                if (index >= 0 && index < tabs.size()) {
                    activeTab = tabs.get(index);
                    scrollToTab(activeTab);
                    onTabChanged.run();
                }
                isDropdownOpen = false;
                return true;
            }

            isDropdownOpen = false;
        }

        if (my >= y && my <= y + TAB_HEIGHT) {
            int currentX = x - (int) scrollOffset;
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                int tabW = getTabWidth(tab);

                if (currentX + tabW > x && currentX < x + visibleW) {
                    if (mx >= currentX && mx <= currentX + tabW) {
                        int closeX = currentX + tabW - 14;
                        if (mx >= closeX - 3 && mx <= closeX + 9) {
                            closeTab(tab);
                        } else {
                            if (activeTab != tab) {
                                activeTab = tab;
                                scrollToTab(activeTab);
                                onTabChanged.run();
                            }
                        }
                        return true;
                    }
                }
                currentX += tabW;
            }
        }

        if (activeTab != null && activeTab.editor != null) {
            activeTab.editor.mouseClicked(mx, my, button);
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (activeTab != null && activeTab.editor != null) {
            return activeTab.editor.keyPressed(event);
        }
        return false;
    }

    public boolean charTyped(char codepoint) {
        if (activeTab != null && activeTab.editor != null) {
            return activeTab.editor.charTyped(codepoint);
        }
        return false;
    }
}