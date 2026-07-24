package com.tkisor.nekojs.client.gui.components;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tkisor.nekojs.client.gui.JSHighlighter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NekoCodeEditor {
    private final int x, y, width, height;
    private final Font font;
    private final MultiLineEditBox editorBox;
    private final Runnable onSave;

    private int selectedEditorLine = -1;
    private final List<Integer> visualToRealLineMap = new ArrayList<>();
    private final List<FormattedCharSequence> visualLinesCache = new ArrayList<>();
    private final List<String> rawVisualLines = new ArrayList<>();

    private int bracketMatch1 = -1;
    private int bracketMatch2 = -1;

    private String originalScriptText = "";
    private boolean isDirty = false;
    private final int GUTTER_WIDTH = 32;

    private record HistoryState(String text, int cursor, double scroll) {}
    private final Deque<HistoryState> undoStack = new ArrayDeque<>();
    private final Deque<HistoryState> redoStack = new ArrayDeque<>();
    private boolean isRestoringHistory = false;

    private String lastText = "";
    private int lastCursor = 0;
    private double lastScroll = 0;
    private long lastEditTime = 0;

    private static final String[] KEYWORDS = {
            "Array", "Block", "Boolean", "Client", "Entity", "Event", "Item", "JSON", "Level", "Math",
            "Number", "Object", "Player", "Promise", "Server", "String", "break", "case", "catch", "class",
            "const", "continue", "console", "default", "delete", "do", "else", "export", "false", "finally", "for",
            "function", "if", "import", "instanceof", "let", "new", "null", "return", "setInterval", "setTimeout",
            "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "yield"
    };

    private final Set<String> documentWords = new HashSet<>();
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");

    private boolean showAutoComplete = false;
    private final List<String> suggestions = new ArrayList<>();
    private int suggestionIndex = 0;
    private int wordStartIdx = -1;
    private int acCursorDrawX = -1;
    private int acCursorDrawY = -1;

    public NekoCodeEditor(Font font, int x, int y, int width, int height, String initialText, Runnable onSave) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onSave = onSave;

        this.originalScriptText = initialText != null ? initialText : "";
        this.lastText = this.originalScriptText;

        this.editorBox = MultiLineEditBox.builder()
                .setX(x + GUTTER_WIDTH + 2)
                .setY(y + 4)
                .setShowBackground(false)
                .setTextColor(0x00000000)
                .setCursorColor(0x00000000)
                .build(font, width - GUTTER_WIDTH - 6, height - 8, Component.empty());

        this.editorBox.setValue(this.originalScriptText);
        this.updateLineMap();
        this.updateDocumentWords(this.originalScriptText);

        this.editorBox.textField.setCursorListener(() -> {
            if (!isRestoringHistory) {
                lastCursor = this.editorBox.textField.cursor();
                lastScroll = this.editorBox.scrollAmount();
            }
            findMatchingBrackets();
            if (!isRestoringHistory) updateAutoComplete();
            lastEditTime = System.currentTimeMillis();
        });

        this.editorBox.setValueListener(text -> {
            if (isRestoringHistory) return;
            long now = System.currentTimeMillis();
            if (Math.abs(text.length() - lastText.length()) > 1 || now - lastEditTime > 800) {
                pushPreviousState();
            }
            lastText = text;
            lastEditTime = now;
            this.isDirty = !text.equals(this.originalScriptText);
            this.updateLineMap();
            this.updateDocumentWords(text);
            findMatchingBrackets();
            updateAutoComplete();
        });

        this.editorBox.setScrollAmount(0);
        this.setCursorAbsolute(0);
    }

    private void updateDocumentWords(String text) {
        documentWords.clear();
        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            String word = m.group();
            if (word.length() > 2) {
                documentWords.add(word);
            }
        }
    }

    public MultiLineEditBox getWidget() { return this.editorBox; }
    public String getValue() { return this.editorBox.getValue(); }
    public boolean isDirty() { return this.isDirty; }

    public void markSaved() {
        this.originalScriptText = this.editorBox.getValue();
        this.isDirty = false;
    }

    public String getOriginalScriptText() { return this.originalScriptText; }

    public void setOriginalScriptText(String text) {
        this.originalScriptText = text;
        this.isDirty = !this.getValue().equals(text);
        this.updateDocumentWords(text);
    }

    public void setHighlightAndScroll(int start, int end) {
        this.setSelection(end, start);
        this.scrollToCursorCentered();
    }

    public void scrollToCursorCentered() {
        int cursorLine = editorBox.textField.getLineAtCursor();
        if (cursorLine == -1) return;

        int cursorY = cursorLine * 9;
        int visibleHeight = height - 8;
        double targetScroll = cursorY - (visibleHeight / 2.0) + 4.5;
        editorBox.setScrollAmount(Math.max(0, targetScroll));
    }

    private void setCursorAbsolute(int pos) { setSelection(pos, pos); }

    public void setSelection(int cursor, int selectCursor) {
        int len = this.editorBox.getValue().length();
        this.editorBox.textField.cursor = Math.max(0, Math.min(cursor, len));
        this.editorBox.textField.selectCursor = Math.max(0, Math.min(selectCursor, len));
    }

    private boolean isCtrlDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private boolean isShiftDown() {
        Window window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public boolean charTyped(char c) {
        String pair = getMatchingPair(c);
        if (pair != null) {
            pushCurrentState();
            String text = editorBox.getValue();
            int cursor = editorBox.textField.cursor();
            int selectCursor = editorBox.textField.selectCursor;

            if (cursor != selectCursor) {
                int min = Math.min(cursor, selectCursor);
                int max = Math.max(cursor, selectCursor);
                String newText = text.substring(0, min) + pair.charAt(0) + text.substring(min, max) + pair.charAt(1) + text.substring(max);

                isRestoringHistory = true;
                editorBox.setValue(newText);
                setSelection(cursor < selectCursor ? cursor + 1 : cursor + 2, selectCursor < cursor ? selectCursor + 1 : selectCursor + 2);
                isRestoringHistory = false;
            } else {
                String newText = text.substring(0, cursor) + pair + text.substring(cursor);
                isRestoringHistory = true;
                editorBox.setValue(newText);
                setCursorAbsolute(cursor + 1);
                isRestoringHistory = false;
            }

            this.isDirty = true;
            updateLineMap();
            return true;
        }

        if (isClosingBracket(c)) {
            String text = editorBox.getValue();
            int cursor = editorBox.textField.cursor();
            if (cursor < text.length() && text.charAt(cursor) == c && cursor == editorBox.textField.selectCursor) {
                setCursorAbsolute(cursor + 1);
                return true;
            }
        }
        return false;
    }

    private String getMatchingPair(char c) {
        if (c == '{') return "{}";
        if (c == '[') return "[]";
        if (c == '(') return "()";
        if (c == '"') return "\"\"";
        if (c == '\'') return "''";
        if (c == '`') return "``";
        return null;
    }

    private boolean isClosingBracket(char c) {
        return c == '}' || c == ']' || c == ')' || c == '"' || c == '\'' || c == '`';
    }

    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (showAutoComplete && !suggestions.isEmpty()) {
            if (key == GLFW.GLFW_KEY_DOWN) {
                suggestionIndex = (suggestionIndex + 1) % suggestions.size();
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                suggestionIndex = (suggestionIndex - 1 + suggestions.size()) % suggestions.size();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
                applySuggestion();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                showAutoComplete = false;
                return true;
            }
        }

        if (isCtrlDown() && key == GLFW.GLFW_KEY_S) {
            if (this.onSave != null) this.onSave.run();
            return true;
        }
        if (isCtrlDown() && key == GLFW.GLFW_KEY_Z) {
            if (isShiftDown()) redo(); else undo();
            return true;
        }
        if (isCtrlDown() && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }

        if (isCtrlDown() && key == GLFW.GLFW_KEY_SLASH) { return handleToggleComment(); }
        if (isCtrlDown() && key == GLFW.GLFW_KEY_LEFT_BRACKET) { return handleIndent(true); }
        if (isCtrlDown() && key == GLFW.GLFW_KEY_RIGHT_BRACKET) { return handleIndent(false); }

        if (key == GLFW.GLFW_KEY_TAB) {
            int c1 = editorBox.textField.cursor();
            int c2 = editorBox.textField.selectCursor;
            if (c1 != c2) {
                return handleIndent(false);
            } else {
                editorBox.textField.insertText("    ");
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            return handleSmartEnter();
        }

        return false;
    }

    private void updateAutoComplete() {
        int cursor = editorBox.textField.cursor();
        String text = editorBox.getValue();

        if (cursor <= 0 || text.isEmpty()) {
            showAutoComplete = false;
            return;
        }

        int start = cursor - 1;
        while (start >= 0) {
            char c = text.charAt(start);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                start--;
            } else {
                break;
            }
        }
        start++;
        wordStartIdx = start;

        int prefixLen = cursor - start;
        if (prefixLen <= 0) {
            showAutoComplete = false;
            return;
        }

        String prefix = text.substring(start, cursor);
        suggestions.clear();

        Set<String> addedWords = new HashSet<>();

        for (String kw : KEYWORDS) {
            if (kw.startsWith(prefix) && !kw.equals(prefix)) {
                suggestions.add(kw);
                addedWords.add(kw);
            }
        }

        List<String> docSuggestions = new ArrayList<>();
        for (String dw : documentWords) {
            if (dw.startsWith(prefix) && !dw.equals(prefix) && !addedWords.contains(dw)) {
                docSuggestions.add(dw);
                addedWords.add(dw);
            }
        }

        docSuggestions.sort(String::compareToIgnoreCase);
        suggestions.addAll(docSuggestions);

        if (suggestions.isEmpty()) {
            showAutoComplete = false;
        } else {
            showAutoComplete = true;
            if (suggestionIndex >= suggestions.size()) {
                suggestionIndex = 0;
            }
        }
    }

    private void applySuggestion() {
        if (!showAutoComplete || suggestions.isEmpty()) return;
        String chosen = suggestions.get(suggestionIndex);
        String text = editorBox.getValue();
        int cursor = editorBox.textField.cursor();

        String newText = text.substring(0, wordStartIdx) + chosen + text.substring(cursor);

        isRestoringHistory = true;
        pushCurrentState();
        editorBox.setValue(newText);

        int newCursorPos = wordStartIdx + chosen.length();
        setCursorAbsolute(newCursorPos);
        isRestoringHistory = false;

        showAutoComplete = false;
        this.isDirty = true;
        updateLineMap();
    }

    private void pushPreviousState() {
        if (isRestoringHistory) return;
        undoStack.push(new HistoryState(lastText, lastCursor, lastScroll));
        redoStack.clear();
    }

    private void pushCurrentState() {
        if (isRestoringHistory) return;
        undoStack.push(new HistoryState(editorBox.getValue(), editorBox.textField.cursor(), editorBox.scrollAmount()));
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        isRestoringHistory = true;

        redoStack.push(new HistoryState(editorBox.getValue(), editorBox.textField.cursor(), editorBox.scrollAmount()));
        HistoryState state = undoStack.pop();

        editorBox.setValue(state.text());
        setCursorAbsolute(state.cursor());
        editorBox.setScrollAmount(state.scroll());

        lastText = state.text();
        lastCursor = state.cursor();
        lastScroll = state.scroll();
        this.isDirty = !state.text().equals(this.originalScriptText);
        updateLineMap();
        isRestoringHistory = false;
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        isRestoringHistory = true;

        undoStack.push(new HistoryState(editorBox.getValue(), editorBox.textField.cursor(), editorBox.scrollAmount()));
        HistoryState state = redoStack.pop();

        editorBox.setValue(state.text());
        setCursorAbsolute(state.cursor());
        editorBox.setScrollAmount(state.scroll());

        lastText = state.text();
        lastCursor = state.cursor();
        lastScroll = state.scroll();
        this.isDirty = !state.text().equals(this.originalScriptText);
        updateLineMap();
        isRestoringHistory = false;
    }

    private boolean handleIndent(boolean unindent) {
        pushCurrentState();
        String text = editorBox.getValue();
        int c1 = editorBox.textField.cursor();
        int c2 = editorBox.textField.selectCursor;
        int minC = Math.min(c1, c2);
        int maxC = Math.max(c1, c2);

        if (maxC > minC && text.charAt(maxC - 1) == '\n') maxC--;

        int startPos = text.lastIndexOf('\n', minC - 1) + 1;
        int endPos = text.indexOf('\n', maxC);
        if (endPos == -1) endPos = text.length();

        String targetText = text.substring(startPos, endPos);
        String[] lines = targetText.split("\n", -1);

        StringBuilder newTarget = new StringBuilder();
        int charsAddedTotal = 0;
        int charsAddedBeforeMinC = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String newLine = line;
            int diff = 0;

            if (unindent) {
                int spacesToRemove = 0;
                while (spacesToRemove < 4 && spacesToRemove < line.length() && line.charAt(spacesToRemove) == ' ') spacesToRemove++;
                if (spacesToRemove > 0) {
                    newLine = line.substring(spacesToRemove);
                    diff = -spacesToRemove;
                }
            } else {
                newLine = "    " + line;
                diff = 4;
            }

            newTarget.append(newLine);
            if (i < lines.length - 1) newTarget.append("\n");

            charsAddedTotal += diff;
            if (i == 0) charsAddedBeforeMinC = diff;
        }

        String newText = text.substring(0, startPos) + newTarget.toString() + text.substring(endPos);
        isRestoringHistory = true;
        editorBox.setValue(newText);

        int newC1 = c1;
        int newC2 = c2;
        if (minC == maxC) {
            newC1 = Math.max(startPos, c1 + charsAddedBeforeMinC);
            newC2 = newC1;
        } else {
            if (c1 < c2) {
                newC1 = Math.max(startPos, c1 + charsAddedBeforeMinC);
                newC2 = c2 + charsAddedTotal;
            } else {
                newC2 = Math.max(startPos, c2 + charsAddedBeforeMinC);
                newC1 = c1 + charsAddedTotal;
            }
        }

        setSelection(newC1, newC2);
        isRestoringHistory = false;
        this.isDirty = !newText.equals(this.originalScriptText);
        updateLineMap();
        return true;
    }

    private boolean handleToggleComment() {
        pushCurrentState();
        String text = editorBox.getValue();
        int c1 = editorBox.textField.cursor();
        int c2 = editorBox.textField.selectCursor;
        int minC = Math.min(c1, c2);
        int maxC = Math.max(c1, c2);

        if (maxC > minC && text.charAt(maxC - 1) == '\n') maxC--;

        int startPos = text.lastIndexOf('\n', minC - 1) + 1;
        int endPos = text.indexOf('\n', maxC);
        if (endPos == -1) endPos = text.length();

        String targetText = text.substring(startPos, endPos);
        String[] lines = targetText.split("\n", -1);

        boolean allCommented = true;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (!line.trim().startsWith("//")) {
                allCommented = false; break;
            }
        }

        StringBuilder newTarget = new StringBuilder();
        int charsAddedTotal = 0;
        int charsAddedBeforeMinC = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String newLine = line;
            int diff = 0;

            if (allCommented) {
                int commentIdx = line.indexOf("//");
                if (commentIdx != -1) {
                    if (commentIdx + 2 < line.length() && line.charAt(commentIdx + 2) == ' ') {
                        newLine = line.substring(0, commentIdx) + line.substring(commentIdx + 3);
                        diff = -3;
                    } else {
                        newLine = line.substring(0, commentIdx) + line.substring(commentIdx + 2);
                        diff = -2;
                    }
                }
            } else {
                newLine = "// " + line;
                diff = 3;
            }

            newTarget.append(newLine);
            if (i < lines.length - 1) newTarget.append("\n");

            charsAddedTotal += diff;
            if (i == 0) charsAddedBeforeMinC = diff;
        }

        String newText = text.substring(0, startPos) + newTarget.toString() + text.substring(endPos);
        isRestoringHistory = true;
        editorBox.setValue(newText);

        int newC1 = c1;
        int newC2 = c2;
        if (minC == maxC) {
            newC1 = Math.max(startPos, c1 + charsAddedBeforeMinC);
            newC2 = newC1;
        } else {
            if (c1 < c2) {
                newC1 = Math.max(startPos, c1 + charsAddedBeforeMinC);
                newC2 = c2 + charsAddedTotal;
            } else {
                newC2 = Math.max(startPos, c2 + charsAddedBeforeMinC);
                newC1 = c1 + charsAddedTotal;
            }
        }

        setSelection(newC1, newC2);
        isRestoringHistory = false;
        this.isDirty = !newText.equals(this.originalScriptText);
        updateLineMap();
        return true;
    }

    private boolean handleSmartEnter() {
        String text = editorBox.getValue();
        int cursor = editorBox.textField.cursor();

        int lineStart = text.lastIndexOf('\n', cursor - 1) + 1;
        String currentLinePrefix = text.substring(lineStart, cursor);
        StringBuilder indent = new StringBuilder();
        for (char c : currentLinePrefix.toCharArray()) {
            if (c == ' ' || c == '\t') indent.append(c);
            else break;
        }

        boolean isAfterOpenBrace = cursor > 0 && text.charAt(cursor - 1) == '{';
        boolean isBeforeCloseBrace = cursor < text.length() && text.charAt(cursor) == '}';

        if (isAfterOpenBrace) indent.append("    ");

        String insert = "\n" + indent.toString();

        if (isAfterOpenBrace && isBeforeCloseBrace) {
            String indentBefore = indent.substring(0, Math.max(0, indent.length() - 4));
            pushCurrentState();

            String newText = text.substring(0, cursor) + insert + "\n" + indentBefore + text.substring(cursor);

            isRestoringHistory = true;
            double currentScroll = editorBox.scrollAmount();
            editorBox.setValue(newText);
            setCursorAbsolute(cursor + insert.length());
            editorBox.setScrollAmount(currentScroll);
            scrollToCursorCentered();
            isRestoringHistory = false;

            lastText = newText;
            lastCursor = editorBox.textField.cursor();
            lastScroll = editorBox.scrollAmount();

            this.isDirty = !newText.equals(this.originalScriptText);
            updateLineMap();
        } else {
            editorBox.textField.insertText(insert);
        }
        return true;
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        showAutoComplete = false;

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

    public void renderUnderlay(GuiGraphicsExtractor g) {
        g.fill(x, y, x + width, y + height, 0xFF1E1E1E);

        int totalVisualLines = visualToRealLineMap.size();
        int innerX = x + 2;
        int innerY = y + 2;
        int innerH = height - 4;

        g.fill(innerX, innerY, innerX + GUTTER_WIDTH, innerY + innerH, 0xFF181818);

        int fontHeight = 9;
        double scroll = editorBox.scrollAmount();
        int startLine = (int) (scroll / fontHeight);
        int maxVisibleLines = innerH / fontHeight + 1;

        int textStartX = x + GUTTER_WIDTH + 6;
        int textStartY = y + 8;
        String rawText = editorBox.getValue();

        acCursorDrawX = -1;
        acCursorDrawY = -1;

        for (int i = 0; i <= maxVisibleLines; i++) {
            int visualIdx = startLine + i;
            if (visualIdx >= totalVisualLines) break;

            int realLineNum = visualToRealLineMap.get(visualIdx);
            int drawY = textStartY + (i * fontHeight) - (int) (scroll % fontHeight);

            if (drawY < innerY || drawY > innerY + innerH - fontHeight) continue;

            boolean isCurrentLine = (realLineNum - 1 == selectedEditorLine);

            if (isCurrentLine) {
                g.fill(innerX, drawY, x + width - 2, drawY + fontHeight, 0xFF2C2C2C);
                g.fill(innerX, drawY, innerX + 2, drawY + fontHeight, 0xFF007ACC);
            }

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

                int actualCursorLine = editorBox.textField.getLineAtCursor();
                if (this.editorBox.isFocused() && visualIdx == actualCursorLine && editorBox.textField.cursor() == editorBox.textField.selectCursor) {
                    String textBeforeCursor = rawText.substring(bBegin, editorBox.textField.cursor());
                    int cursorPixelX = textStartX + this.font.width(textBeforeCursor);

                    acCursorDrawX = cursorPixelX;
                    acCursorDrawY = drawY + fontHeight + 2;

                    long timeSinceEdit = System.currentTimeMillis() - lastEditTime;
                    float alphaMult;
                    if (timeSinceEdit < 500) {
                        alphaMult = 1.0f;
                    } else {
                        float sin = (float) Math.sin((timeSinceEdit - 500) / 200.0);
                        alphaMult = sin * 0.5f + 0.5f;
                    }
                    int alpha = (int) (alphaMult * 255);
                    int cursorColor = ARGB.color(alpha, 220, 220, 220);

                    g.fill(cursorPixelX, drawY, cursorPixelX + 1, drawY + fontHeight, cursorColor);
                }
            }

            if (visualIdx < visualLinesCache.size()) {
                g.text(this.font, visualLinesCache.get(visualIdx), textStartX, drawY, 0xFFFFFFFF);
            }

            if (visualIdx > 0 && visualToRealLineMap.get(visualIdx - 1) == realLineNum) continue;

            String numStr = String.valueOf(realLineNum);
            int numW = this.font.width(numStr);
            int numColor = isCurrentLine ? 0xFFFFFFFF : 0xFF666666;
            g.text(this.font, numStr, innerX + GUTTER_WIDTH - 6 - numW, drawY, numColor);
        }

        g.fill(innerX + GUTTER_WIDTH - 1, innerY, innerX + GUTTER_WIDTH, innerY + innerH, 0xFF3A3A3A);

        if (showAutoComplete && !suggestions.isEmpty() && acCursorDrawX != -1 && acCursorDrawY != -1) {
            renderAutoComplete(g, acCursorDrawX, acCursorDrawY);
        }
    }

    private void renderAutoComplete(GuiGraphicsExtractor g, int ax, int ay) {
        int itemH = 12;
        int maxItems = 6;
        int visibleItems = Math.min(suggestions.size(), maxItems);
        int menuH = visibleItems * itemH + 4;

        int menuW = 100;
        for (String s : suggestions) {
            menuW = Math.max(menuW, font.width(s) + 16);
        }

        if (ay + menuH > this.y + this.height) {
            ay -= (menuH + 12);
        }

        g.fill(ax, ay, ax + menuW, ay + menuH, 0xF21E1E1E);
        g.outline(ax, ay, menuW, menuH, 0xFF454545);

        int startIdx = Math.max(0, Math.min(suggestionIndex - visibleItems / 2, suggestions.size() - visibleItems));

        for (int i = 0; i < visibleItems; i++) {
            int idx = startIdx + i;
            String s = suggestions.get(idx);
            int itemY = ay + 2 + i * itemH;

            if (idx == suggestionIndex) {
                g.fill(ax + 1, itemY, ax + menuW - 1, itemY + itemH, 0xFF094771);
            }

            int prefixLen = editorBox.textField.cursor() - wordStartIdx;
            if (prefixLen > 0 && prefixLen <= s.length()) {
                String prefix = s.substring(0, prefixLen);
                String rest = s.substring(prefixLen);
                int px = ax + 6;
                g.text(font, prefix, px, itemY + 2, 0xFF00A2FF);
                g.text(font, rest, px + font.width(prefix), itemY + 2, 0xFFCCCCCC);
            } else {
                g.text(font, s, ax + 6, itemY + 2, 0xFFCCCCCC);
            }
        }
    }

    private void updateLineMap() {
        visualToRealLineMap.clear();
        visualLinesCache.clear();
        rawVisualLines.clear();

        int innerWidth = editorBox.getWidth() - 8;
        String[] realLines = editorBox.getValue().split("\n", -1);
        JSHighlighter.HighlightState state = new JSHighlighter.HighlightState();

        for (int i = 0; i < realLines.length; i++) {
            Component highlightedRealLine = JSHighlighter.highlight(realLines[i], state);
            List<FormattedText> wrapped = this.font.getSplitter().splitLines(highlightedRealLine, innerWidth, Style.EMPTY);

            if (wrapped.isEmpty()) {
                visualToRealLineMap.add(i + 1);
                visualLinesCache.add(FormattedCharSequence.EMPTY);
                rawVisualLines.add("");
            } else {
                for (FormattedText wt : wrapped) {
                    visualToRealLineMap.add(i + 1);
                    visualLinesCache.add(net.minecraft.locale.Language.getInstance().getVisualOrder(wt));
                    rawVisualLines.add(wt.getString());
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