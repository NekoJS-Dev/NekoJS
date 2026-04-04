package com.tkisor.nekojs.client.gui.components;

import com.tkisor.nekojs.client.gui.JSHighlighter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class NekoCodeEditor {
    private final int x, y, width, height;
    private final Font font;
    private final MultiLineEditBox editorBox;

    private int selectedEditorLine = -1;
    private final List<Integer> visualToRealLineMap = new ArrayList<>();
    private final List<FormattedCharSequence> visualLinesCache = new ArrayList<>();

    private int bracketMatch1 = -1;
    private int bracketMatch2 = -1;

    private String originalScriptText = "";
    private boolean isDirty = false;
    private final int GUTTER_WIDTH = 32;

    public NekoCodeEditor(Font font, int x, int y, int width, int height, String initialText) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.originalScriptText = initialText != null ? initialText : "";

        // 🌟 初始化底层的透明输入框
        this.editorBox = MultiLineEditBox.builder()
                .setX(x + GUTTER_WIDTH + 2)
                .setY(y + 4)
                .setShowBackground(false)
                .setTextColor(0x00000000)
                .setCursorColor(0xFFFFFFFF)
                .build(font, width - GUTTER_WIDTH - 6, height - 8, Component.empty());

        this.editorBox.setValue(this.originalScriptText);
        this.updateLineMap();

        this.editorBox.setValueListener(text -> {
            this.isDirty = !text.equals(this.originalScriptText);
            this.updateLineMap();
        });

        // 强制光标回到顶部
        this.editorBox.setScrollAmount(0);
        this.editorBox.textField.seekCursorToPoint(0, 0);
    }

    public MultiLineEditBox getWidget() {
        return this.editorBox;
    }

    public String getValue() {
        return this.editorBox.getValue();
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void markClean() {
        this.originalScriptText = this.editorBox.getValue();
        this.isDirty = false;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= x + GUTTER_WIDTH && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            double relativeY = mouseY - (y + 8) + editorBox.scrollAmount();
            if (relativeY >= 0) {
                int visualLine = (int) (relativeY / 9);
                if (visualLine >= 0 && visualLine < visualToRealLineMap.size()) {
                    selectedEditorLine = visualToRealLineMap.get(visualLine) - 1;
                } else selectedEditorLine = -1;
            } else selectedEditorLine = -1;
        }
    }

    // 必须在 Screen 的 super.extractRenderState 之前调用！
    public void renderUnderlay(GuiGraphicsExtractor g) {
        // 画底层深色背景
        g.fill(x, y, x + width, y + height, 0xFF1E1E1E);

        findMatchingBrackets();

        int totalVisualLines = visualToRealLineMap.size();
        int innerX = x + 2;
        int innerY = y + 2;
        int innerH = height - 4;

        g.fill(innerX, innerY, innerX + GUTTER_WIDTH, innerY + innerH, 0xFF181818);
        g.fill(innerX + GUTTER_WIDTH - 1, innerY, innerX + GUTTER_WIDTH, innerY + innerH, 0xFF333333);

        int fontHeight = 9;
        double scroll = editorBox.scrollAmount();
        int startLine = (int) (scroll / fontHeight);
        int maxVisibleLines = innerH / fontHeight + 1;

        int textStartX = x + GUTTER_WIDTH + 6;
        int textStartY = y + 8;
        String rawText = editorBox.getValue();

        for (int i = 0; i <= maxVisibleLines; i++) {
            int visualIdx = startLine + i;
            if (visualIdx >= totalVisualLines) break;

            int realLineNum = visualToRealLineMap.get(visualIdx);
            int drawY = textStartY + (i * fontHeight) - (int) (scroll % fontHeight);

            if (drawY < innerY || drawY > innerY + innerH - fontHeight) continue;

            boolean isCurrentLine = (realLineNum - 1 == selectedEditorLine);

            if (isCurrentLine) {
                g.fill(innerX, drawY, x + width - 2, drawY + fontHeight, 0x1AFFFFFF);
                g.fill(innerX, drawY, innerX + 2, drawY + fontHeight, 0xFF44FF44);
            }

            // 括号匹配背景
            MultilineTextField.StringView lineView = editorBox.textField.getLineView(visualIdx);
            if (lineView != null) {
                int bBegin = lineView.beginIndex();
                int bEnd = lineView.endIndex();

                if (bracketMatch1 >= bBegin && bracketMatch1 < bEnd) {
                    int xOff = this.font.width(rawText.substring(bBegin, bracketMatch1));
                    int bw = this.font.width(String.valueOf(rawText.charAt(bracketMatch1)));
                    g.fill(textStartX + xOff - 1, drawY, textStartX + xOff + bw + 1, drawY + fontHeight, 0x44FFFFFF);
                }
                if (bracketMatch2 >= bBegin && bracketMatch2 < bEnd) {
                    int xOff = this.font.width(rawText.substring(bBegin, bracketMatch2));
                    int bw = this.font.width(String.valueOf(rawText.charAt(bracketMatch2)));
                    g.fill(textStartX + xOff - 1, drawY, textStartX + xOff + bw + 1, drawY + fontHeight, 0x44FFFFFF);
                }
            }

            if (visualIdx < visualLinesCache.size()) {
                g.text(this.font, visualLinesCache.get(visualIdx), textStartX, drawY, 0xFFFFFFFF);
            }

            if (visualIdx > 0 && visualToRealLineMap.get(visualIdx - 1) == realLineNum) continue;

            String numStr = String.valueOf(realLineNum);
            int numW = this.font.width(numStr);
            int numColor = isCurrentLine ? 0xFFDDDDDD : 0xFF555555;
            g.text(this.font, numStr, innerX + GUTTER_WIDTH - 6 - numW, drawY, numColor);
        }
    }

    private void updateLineMap() {
        visualToRealLineMap.clear();
        visualLinesCache.clear();

        int innerWidth = editorBox.getWidth() - 8;
        String[] realLines = editorBox.getValue().split("\n", -1);

        JSHighlighter.HighlightState state = new JSHighlighter.HighlightState();

        for (int i = 0; i < realLines.length; i++) {
            Component highlightedRealLine = JSHighlighter.highlight(realLines[i], state);
            List<FormattedText> wrapped = this.font.getSplitter().splitLines(highlightedRealLine, innerWidth, Style.EMPTY);

            if (wrapped.isEmpty()) {
                visualToRealLineMap.add(i + 1);
                visualLinesCache.add(FormattedCharSequence.EMPTY);
            } else {
                for (FormattedText wt : wrapped) {
                    visualToRealLineMap.add(i + 1);
                    visualLinesCache.add(net.minecraft.locale.Language.getInstance().getVisualOrder(wt));
                }
            }
        }
    }

    private void findMatchingBrackets() {
        bracketMatch1 = -1;
        bracketMatch2 = -1;

        String text = editorBox.getValue();
        int cursor = editorBox.textField.cursor();

        int checkIdx = -1;
        if (cursor < text.length() && isBracket(text.charAt(cursor))) {
            checkIdx = cursor;
        } else if (cursor - 1 >= 0 && cursor - 1 < text.length() && isBracket(text.charAt(cursor - 1))) {
            checkIdx = cursor - 1;
        }

        if (checkIdx != -1) {
            char c = text.charAt(checkIdx);
            int match = findMatch(text, checkIdx, c);
            if (match != -1) {
                bracketMatch1 = checkIdx;
                bracketMatch2 = match;
            }
        }
    }

    private boolean isBracket(char c) {
        return c == '{' || c == '}' || c == '[' || c == ']' || c == '(' || c == ')';
    }

    private int findMatch(String text, int start, char c) {
        char open, close;
        int dir;
        if (c == '{' || c == '[' || c == '(') {
            open = c; close = getClose(c); dir = 1;
        } else {
            close = c; open = getOpen(c); dir = -1;
        }

        int depth = 0;
        for (int i = start; i >= 0 && i < text.length(); i += dir) {
            char cur = text.charAt(i);
            if (dir == 1) {
                if (cur == open) depth++; else if (cur == close) depth--;
            } else {
                if (cur == close) depth++; else if (cur == open) depth--;
            }
            if (depth == 0) return i;
        }
        return -1;
    }

    private char getClose(char c) { return c == '{' ? '}' : (c == '[' ? ']' : ')'); }
    private char getOpen(char c) { return c == '}' ? '{' : (c == ']' ? '[' : '('); }
}