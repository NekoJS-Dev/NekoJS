package com.tkisor.nekojs.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class JSHighlighter {

    // 🌟 GitHub Theme Dark 官方 HEX 色盘
    private static final Style STYLE_DEFAULT  = Style.EMPTY.withColor(TextColor.fromRgb(0xC9D1D9)); // 乱码/普通文本/符号: 浅灰白
    private static final Style STYLE_STRING   = Style.EMPTY.withColor(TextColor.fromRgb(0xA5D6FF)); // 字符串: 浅蓝
    private static final Style STYLE_FUNCTION = Style.EMPTY.withColor(TextColor.fromRgb(0xD2A8FF)); // 函数/方法: 淡紫
    private static final Style STYLE_KEYWORD  = Style.EMPTY.withColor(TextColor.fromRgb(0xFF7B72)); // 关键字 (async等): 珊瑚红
    private static final Style STYLE_NUMBER   = Style.EMPTY.withColor(TextColor.fromRgb(0x79C0FF)); // 数字/布尔: 亮蓝
    private static final Style STYLE_COMMENT  = Style.EMPTY.withColor(TextColor.fromRgb(0x8B949E)); // 注释: 灰绿
    private static final Style STYLE_BUILTIN  = Style.EMPTY.withColor(TextColor.fromRgb(0x79C0FF)); // 内置对象 (RegExp等): 亮蓝

    // 🌟 6层 括号层级彩虹配色定制版
    private static final Style[] BRACKET_STYLES = new Style[]{
            Style.EMPTY.withColor(TextColor.fromRgb(0x1E90FF)), // Level 1: 蓝色
            Style.EMPTY.withColor(TextColor.fromRgb(0x50C878)), // Level 2: 绿色
            Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700)), // Level 3: 黄色
            Style.EMPTY.withColor(TextColor.fromRgb(0xFF9E81)), // Level 4: 略微黄色的粉
            Style.EMPTY.withColor(TextColor.fromRgb(0xFF69B4)), // Level 5: 粉色
            Style.EMPTY.withColor(TextColor.fromRgb(0xB180D7))  // Level 6: 紫色
    };

    public static class HighlightState {
        public int bracketDepth = 0;
        public boolean inMultiLineComment = false;
        public boolean inTemplateString = false;
    }

    public static Component highlight(String code, HighlightState state) {
        MutableComponent root = Component.empty();
        StringBuilder buffer = new StringBuilder();
        int i = 0;
        int len = code.length();

        while (i < len) {
            char c = code.charAt(i);

            // 处理多行注释连贯状态
            if (state.inMultiLineComment) {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                int end = code.indexOf("*/", i);
                if (end == -1) {
                    root.append(Component.literal(code.substring(i)).withStyle(STYLE_COMMENT));
                    break;
                } else {
                    root.append(Component.literal(code.substring(i, end + 2)).withStyle(STYLE_COMMENT));
                    state.inMultiLineComment = false;
                    i = end + 2;
                    continue;
                }
            }

            // 处理多行模板字符串连贯状态
            if (state.inTemplateString) {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                int end = i;
                while (end < len) {
                    if (code.charAt(end) == '\\') end += 2;
                    else if (code.charAt(end) == '`') { end++; state.inTemplateString = false; break; }
                    else end++;
                }
                if (end > len) end = len;
                root.append(Component.literal(code.substring(i, end)).withStyle(STYLE_STRING));
                i = end;
                continue;
            }

            // 1. 匹配多行注释起点 (/*)
            if (c == '/' && i + 1 < len && code.charAt(i + 1) == '*') {
                state.inMultiLineComment = true;
                continue;
            }

            // 2. 匹配单行注释 (//)
            if (c == '/' && i + 1 < len && code.charAt(i + 1) == '/') {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                int end = code.indexOf('\n', i);
                if (end == -1) end = len;
                root.append(Component.literal(code.substring(i, end)).withStyle(STYLE_COMMENT));
                i = end;
                continue;
            }

            // 3. 匹配普通字符串 ("" '')
            if (c == '"' || c == '\'') {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                int end = i + 1;
                while (end < len) {
                    if (code.charAt(end) == '\\') end += 2;
                    else if (code.charAt(end) == c) { end++; break; }
                    else end++;
                }
                if (end > len) end = len;
                root.append(Component.literal(code.substring(i, end)).withStyle(STYLE_STRING));
                i = end;
                continue;
            }

            // 4. 匹配模板字符串起点 (`)
            if (c == '`') {
                state.inTemplateString = true;
                continue;
            }

            // 5. 完美数字解析 (包括 0x1F, 100n, 3.14)
            if (Character.isDigit(c)) {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                int end = i + 1;
                while (end < len && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '.')) end++;
                root.append(Component.literal(code.substring(i, end)).withStyle(STYLE_NUMBER));
                i = end;
                continue;
            }

            // 6. 匹配方法、关键字与变量
            if (Character.isJavaIdentifierStart(c)) {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                int end = i + 1;
                while (end < len && Character.isJavaIdentifierPart(code.charAt(end))) end++;
                String word = code.substring(i, end);

                boolean isFunction = false;
                int peek = end;
                while (peek < len && Character.isWhitespace(code.charAt(peek))) peek++;
                if (peek < len && code.charAt(peek) == '(') isFunction = true;

                Style style;
                if (isKeyword(word)) style = STYLE_KEYWORD;
                else if (isPrimitive(word)) style = STYLE_NUMBER;
                else if (isBuiltInObject(word)) style = STYLE_BUILTIN;
                else if (isFunction) style = STYLE_FUNCTION;
                else style = STYLE_DEFAULT;

                root.append(Component.literal(word).withStyle(style));
                i = end;
                continue;
            }

            // 7. 嵌套括号变色引擎 (6层循环)
            if (c == '{' || c == '[' || c == '(') {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                Style style = BRACKET_STYLES[state.bracketDepth % BRACKET_STYLES.length];
                state.bracketDepth++;
                root.append(Component.literal(String.valueOf(c)).withStyle(style));
                i++;
                continue;
            } else if (c == '}' || c == ']' || c == ')') {
                flushBuffer(root, buffer, STYLE_DEFAULT);
                state.bracketDepth--;
                if (state.bracketDepth < 0) state.bracketDepth = 0;
                Style style = BRACKET_STYLES[state.bracketDepth % BRACKET_STYLES.length];
                root.append(Component.literal(String.valueOf(c)).withStyle(style));
                i++;
                continue;
            }

            // 8. 其他所有符号和乱码
            buffer.append(c);
            i++;
        }

        flushBuffer(root, buffer, STYLE_DEFAULT);
        return root;
    }

    private static void flushBuffer(MutableComponent root, StringBuilder buffer, Style style) {
        if (!buffer.isEmpty()) {
            root.append(Component.literal(buffer.toString()).withStyle(style));
            buffer.setLength(0);
        }
    }

    private static boolean isKeyword(String w) {
        return w.equals("if") || w.equals("else") || w.equals("for") || w.equals("while") ||
                w.equals("return") || w.equals("break") || w.equals("continue") ||
                w.equals("try") || w.equals("catch") || w.equals("switch") || w.equals("case") ||
                w.equals("throw") || w.equals("finally") || w.equals("default") ||
                w.equals("const") || w.equals("let") || w.equals("var") ||
                w.equals("function") || w.equals("class") || w.equals("new") ||
                w.equals("import") || w.equals("export") || w.equals("this") ||
                w.equals("typeof") || w.equals("instanceof") || w.equals("yield") ||
                w.equals("await") || w.equals("async");
    }

    private static boolean isPrimitive(String w) {
        return w.equals("true") || w.equals("false") || w.equals("null") || w.equals("undefined") || w.equals("NaN");
    }

    private static boolean isBuiltInObject(String w) {
        return w.equals("RegExp") || w.equals("Math") || w.equals("Date") ||
                w.equals("console") || w.equals("Object") || w.equals("Array") ||
                w.equals("String") || w.equals("Number") || w.equals("Boolean") ||
                w.equals("Promise") || w.equals("Error") || w.equals("JSON") ||
                w.equals("Map") || w.equals("Set") || w.equals("window") || w.equals("document");
    }
}