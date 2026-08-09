package com.tkisor.nekojs.core.compiler;


import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NekoJsxCompiler {
    private NekoJsxCompiler() {}

    public static ScriptCompileResult compileJsx(Path file, String source) {
        return compileJsx(file, source, false);
    }

    /**
     * 编译 .jsx。
     * @param automatic true 用标准自动 runtime（{@code jsx}/{@code jsxs}/{@code Fragment}，children 在 props 中），
     *                  false 用 classic runtime（{@code globalThis.__nekoJsxFactory}/{@code __nekoJsxFragment}）。
     */
    public static ScriptCompileResult compileJsx(Path file, String source, boolean automatic) {
        JsxTransformResult result = new Transpiler(file, source == null ? "" : source, automatic).transpileDetailed();
        return new ScriptCompileResult(result.code(), result.sourceMap());
    }

    public static ScriptCompileResult compileTsx(Path file, String source) {
        return compileTsx(file, source, false);
    }

    /** 同 {@link #compileTsx(Path, String)} 但可选自动 runtime。 */
    public static ScriptCompileResult compileTsx(Path file, String source, boolean automatic) {
        JsxTransformResult lowered = new Transpiler(file, source == null ? "" : source, automatic).transpileDetailed();
        NekoTypeScriptCompiler.TypeScriptTransformResult erased = NekoTypeScriptCompiler.erasePreservingSourceMap(file, lowered.code(), lowered.sourceMap());
        return new ScriptCompileResult(erased.code(), erased.sourceMap());
    }

    private static final class Transpiler {
        private final Path file;
        private final String source;
        private final int length;
        private final boolean automatic;
        private boolean suppressRuntimeImport;
        private boolean usedJsx;
        private boolean usedFragment;
        private boolean usedJsxs;

        private Transpiler(Path file, String source, boolean automatic) {
            this.file = file;
            this.source = source == null ? "" : source;
            this.length = this.source.length();
            this.automatic = automatic;
        }

        private String transpile() {
            return transpileDetailed().code();
        }

        private JsxTransformResult transpileDetailed() {
            NekoSourceMapBuilder.Emitter output = NekoSourceMapBuilder.emitter(file, source);
            int index = 0;
            int last = 0;
            while (index < length) {
                char c = source.charAt(index);
                if (c == '\'' || c == '"') {
                    index = skipString(index, c);
                    continue;
                }
                if (c == '`') {
                    index = skipTemplate(index);
                    continue;
                }
                if (c == '/') {
                    int skipped = skipSlash(index);
                    if (skipped != index) {
                        index = skipped;
                        continue;
                    }
                }
                if (c == '<' && looksLikeJsxStart(index)) {
                    output.appendOriginalRange(last, index);
                    ParseResult parsed = parseJsx(index);
                    output.appendMapped(parsed.text(), parsed.mappings());
                    index = parsed.nextIndex();
                    last = index;
                    continue;
                }
                index++;
            }
            output.appendOriginalRange(last, length);
            String code = output.code();
            String sourceMap = output.sourceMap();
            if (automatic && !suppressRuntimeImport) {
                String runtimeImport = jsxRuntimeImport();
                if (!runtimeImport.isEmpty()) {
                    code = runtimeImport + code;
                    sourceMap = NekoSourceMapBuilder.prependUnmappedGeneratedLines(sourceMap, 1);
                }
            }
            return new JsxTransformResult(code, sourceMap);
        }

        /**
         * 自动 runtime：在文件头注入标准 import。
         * 仅按实际使用注入 {@code jsx}/{@code jsxs}/{@code Fragment}。
         */
        private String jsxRuntimeImport() {
            if (!usedJsx && !usedJsxs && !usedFragment) {
                return "";
            }
            StringBuilder imp = new StringBuilder("import { ");
            boolean needsComma = false;
            if (usedJsx) {
                imp.append("jsx");
                needsComma = true;
            }
            if (usedJsxs) {
                if (needsComma) imp.append(", ");
                imp.append("jsxs");
                needsComma = true;
            }
            if (usedFragment) {
                if (needsComma) imp.append(", ");
                imp.append("Fragment");
            }
            imp.append(" } from 'nekojs/jsx-runtime';\n");
            return imp.toString();
        }

        private ParseResult parseJsx(int start) {
            if (peek(start + 1) == '>') {
                return parseFragment(start);
            }

            int nameStart = skipWhitespace(start + 1);
            int nameEnd = readJsxNameEnd(nameStart);
            if (nameEnd <= nameStart) {
                throw jsxError("Missing JSX element name", start);
            }

            String tagName = source.substring(nameStart, nameEnd);
            int index = nameEnd;
            // 泛型组件 <Foo<number>/>：JSX 层把 <number> 作为 tag 表达式的一部分透传，
            // TSX 模式下由后续 TS 擦除阶段处理；.jsx 模式下不应出现（用户责任）。
            int genericArgsEnd = skipGenericArgsIfPresent(index);
            String tagExpr;
            if (genericArgsEnd > index) {
                // 泛型参数仅用于 TS 类型检查，不能成为运行时组件表达式。
                // compileTsx 的擦除器会处理完整的 TypeScript 源；这里直接保留标签值，
                // 同时消费参数段，避免对象类型中的 {…} 被误作 JSX 属性。
                tagExpr = tagName;
                index = genericArgsEnd;
            } else {
                tagExpr = tagName;
            }
            List<GeneratedPart> props = new ArrayList<>();
            boolean selfClosing = false;

            while (index < length) {
                index = skipWhitespace(index);
                if (index >= length) {
                    throw jsxError("Unterminated JSX element", start);
                }
                if (source.startsWith("/>", index)) {
                    selfClosing = true;
                    index += 2;
                    break;
                }
                if (source.charAt(index) == '>') {
                    index++;
                    break;
                }
                AttributeResult attribute = parseAttribute(index);
                props.add(attribute.part());
                index = attribute.nextIndex();
            }

            GeneratedPart typeExpression = new GeneratedPart(jsxTagExpression(tagExpr), List.of(new NekoSourceMapBuilder.MappingPoint(0, nameStart)));
            GeneratedPart propsExpression = props.isEmpty() ? GeneratedPart.unmapped("null") : objectPart(props);
            if (selfClosing) {
                return factoryCall(start, typeExpression, propsExpression, List.of(), index);
            }

            List<GeneratedPart> children = new ArrayList<>();
            index = parseChildren(index, tagName, children);
            return factoryCall(start, typeExpression, propsExpression, children, index);
        }

        private ParseResult parseFragment(int start) {
            int index = start + 2;
            List<GeneratedPart> children = new ArrayList<>();
            index = parseChildren(index, "", children);
            return fragmentCall(start, children, index);
        }

        private int parseChildren(int index, String closingTagName, List<GeneratedPart> children) {
            int i = index;
            int textStart = i;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '{') {
                    flushText(textStart, i, children);
                    ExpressionResult expression = parseExpressionChild(i);
                    if (expression != null) {
                        children.add(expression.part());
                        i = expression.nextIndex();
                    } else {
                        i = findMatchingBrace(i) + 1;
                    }
                    textStart = i;
                    continue;
                }
                if (c == '<') {
                    if (peek(i + 1) == '/') {
                        flushText(textStart, i, children);
                        return parseClosingTag(i, closingTagName);
                    }
                    if (looksLikeJsxStart(i)) {
                        flushText(textStart, i, children);
                        ParseResult nested = parseJsx(i);
                        children.add(new GeneratedPart(nested.text(), nested.mappings()));
                        i = nested.nextIndex();
                        textStart = i;
                        continue;
                    }
                }
                i++;
            }
            throw jsxError("Unterminated JSX element", index);
        }

        private int parseClosingTag(int start, String closingTagName) {
            int index = skipWhitespace(start + 2);
            int nameStart = index;
            int nameEnd = readJsxNameEnd(nameStart);
            String actualClosingTag = source.substring(nameStart, nameEnd);
            index = skipWhitespace(nameEnd);
            if (index >= length || source.charAt(index) != '>') {
                throw jsxError("Malformed JSX closing tag", start);
            }
            index++;
            if (!closingTagName.equals(actualClosingTag)) {
                throw jsxError("Mismatched JSX closing tag '</" + actualClosingTag + ">' expected '</" + closingTagName + ">'", start);
            }
            return index;
        }

        private ExpressionResult parseExpressionChild(int start) {
            int end = findMatchingBrace(start);
            String inner = source.substring(start + 1, end);
            if (isIgnorableExpression(inner)) {
                return null;
            }
            String transformed = transform(inner);
            return new ExpressionResult(new GeneratedPart("(" + transformed + ")", expressionMappings(transformed, 1, start + 1)), end + 1);
        }

        private AttributeResult parseAttribute(int start) {
            if (source.charAt(start) == '{') {
                int end = findMatchingBrace(start);
                String inner = source.substring(start + 1, end).trim();
                if (!inner.startsWith("...")) {
                    throw jsxError("Only spread attributes are allowed inside JSX attribute braces", start);
                }
                String spreadExpression = transform(inner.substring(3).trim());
                if (spreadExpression.isBlank()) {
                    throw jsxError("Missing spread expression in JSX attribute", start);
                }
                return new AttributeResult(new GeneratedPart("..." + spreadExpression, List.of(new NekoSourceMapBuilder.MappingPoint(3, start + 1))), end + 1);
            }

            int nameEnd = readAttributeNameEnd(start);
            if (nameEnd <= start) {
                throw jsxError("Missing JSX attribute name", start);
            }
            String name = source.substring(start, nameEnd);
            int index = skipWhitespace(nameEnd);
            if (index >= length || source.charAt(index) != '=') {
                return new AttributeResult(new GeneratedPart(attributeKey(name) + ": true", List.of(new NekoSourceMapBuilder.MappingPoint(0, start))), nameEnd);
            }

            index = skipWhitespace(index + 1);
            if (index >= length) {
                throw jsxError("Missing JSX attribute value", start);
            }
            char valueStart = source.charAt(index);
            if (valueStart == '\'' || valueStart == '"') {
                int valueEnd = skipString(index, valueStart);
                String text = attributeKey(name) + ": " + source.substring(index, valueEnd);
                return new AttributeResult(new GeneratedPart(text, List.of(
                        new NekoSourceMapBuilder.MappingPoint(0, start),
                        new NekoSourceMapBuilder.MappingPoint(text.indexOf(source.charAt(index)), index)
                )), valueEnd);
            }
            if (valueStart == '{') {
                int valueEnd = findMatchingBrace(index);
                String transformed = transform(source.substring(index + 1, valueEnd));
                if (transformed.isBlank()) {
                    throw jsxError("Missing JSX attribute expression", start);
                }
                String text = attributeKey(name) + ": (" + transformed + ")";
                List<NekoSourceMapBuilder.MappingPoint> mappings = new ArrayList<>();
                mappings.add(new NekoSourceMapBuilder.MappingPoint(0, start));
                mappings.addAll(expressionMappings(transformed, text.indexOf('(') + 1, index + 1));
                return new AttributeResult(new GeneratedPart(text, mappings), valueEnd + 1);
            }
            throw jsxError("JSX attribute values must be string literals or expressions", index);
        }

        private void flushText(int start, int end, List<GeneratedPart> children) {
            if (end <= start) {
                return;
            }
            String normalized = normalizeText(source.substring(start, end));
            if (normalized != null) {
                children.add(new GeneratedPart(stringLiteral(normalized), List.of(new NekoSourceMapBuilder.MappingPoint(0, start))));
            }
        }

        private String transform(String innerSource) {
            if (innerSource == null || innerSource.isBlank()) {
                return innerSource == null ? "" : innerSource;
            }
            // 子表达式递归 lowering：复用本 Transpiler 的 automatic 标志（同一文件 runtime 模式一致）；
            // 内部不再 prepend import（由最外层统一注入）。
            Transpiler inner = new Transpiler(file, innerSource, automatic);
            inner.suppressRuntimeImport = true;
            inner.usedJsx = this.usedJsx;
            inner.usedFragment = this.usedFragment;
            inner.usedJsxs = this.usedJsxs;
            String lowered = inner.transpile();
            if (inner.usedJsx) this.usedJsx = true;
            if (inner.usedFragment) this.usedFragment = true;
            if (inner.usedJsxs) this.usedJsxs = true;
            return lowered;
        }

        private List<NekoSourceMapBuilder.MappingPoint> expressionMappings(String transformed, int generatedStart, int originalStart) {
            // TODO(audit): source map offsets for nested JSX expressions are approximate.
            // `transformed` comes from transform() → inner.transpile(), which can change lengths
            // (e.g. nested JSX → jsxs(...) calls, injected runtime identifiers, HTML-entity decode).
            // This helper assumes a 1:1 offset mapping between `transformed` and the original source,
            // so newline-synced points past the first can drift. The clean fix requires surfacing the
            // inner transpiler's source map and remapping its inner-original offsets back to the
            // outer source offsets; deferred as risky for a narrow nested-expression edge case.
            List<NekoSourceMapBuilder.MappingPoint> mappings = new ArrayList<>();
            mappings.add(new NekoSourceMapBuilder.MappingPoint(generatedStart, originalStart));
            for (int i = 0; i < transformed.length(); i++) {
                if (transformed.charAt(i) == '\n' && i + 1 < transformed.length()) {
                    mappings.add(new NekoSourceMapBuilder.MappingPoint(generatedStart + i + 1, originalStart + i + 1));
                }
            }
            return mappings;
        }

        private String normalizeText(String raw) {
            if (raw == null) {
                return null;
            }
            String normalized = raw.replace('\r', ' ');
            normalized = normalized.replaceAll("\\s+", " ").trim();
            if (normalized.isEmpty()) return null;
            return decodeHtmlEntities(normalized);
        }

        /**
         * 解码 JSX 文本里的 HTML 实体（与 React/JSX 规范一致）。
         * 支持命名实体（amp/lt/gt/quot/apos/nbsp）与数字实体（&#NN; / &#xHH;）。
         * 字符串属性值里的实体是字面量（由 parseAttribute 原样保留），本方法只作用于元素文本。
         */
        private String decodeHtmlEntities(String text) {
            if (text.indexOf('&') < 0) return text;
            StringBuilder sb = new StringBuilder(text.length());
            int i = 0, n = text.length();
            while (i < n) {
                char c = text.charAt(i);
                if (c != '&') { sb.append(c); i++; continue; }
                int semi = text.indexOf(';', i + 1);
                if (semi < 0 || semi - i > 10) { sb.append(c); i++; continue; } // 不是实体，原样
                String body = text.substring(i + 1, semi);
                String decoded = decodeEntityBody(body);
                if (decoded != null) {
                    sb.append(decoded);
                    i = semi + 1;
                } else {
                    sb.append(c); i++; // 无法识别，原样保留 &
                }
            }
            return sb.toString();
        }

        private String decodeEntityBody(String body) {
            switch (body) {
                case "amp": return "&";
                case "lt": return "<";
                case "gt": return ">";
                case "quot": return "\"";
                case "apos": return "'";
                case "nbsp": return "\u00a0";
                default:
                    // 数字实体 &#39; / &#x27;
                    if (body.startsWith("#")) {
                        try {
                            int code;
                            if (body.length() > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X')) {
                                code = Integer.parseInt(body.substring(2), 16);
                            } else {
                                code = Integer.parseInt(body.substring(1), 10);
                            }
                            if (code < 0 || code > 0x10FFFF) return null;
                            return new String(Character.toChars(code));
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                    return null;
            }
        }

        private String jsxTagExpression(String tagName) {
            // 命名空间标签不是合法的 JavaScript 表达式，须作为完整字符串传给 factory。
            if (tagName.indexOf(':') >= 0) {
                return stringLiteral(tagName);
            }
            // 成员表达式 / 索引 / 调用作为组件表达式透传。
            if (tagName.indexOf('.') >= 0 || tagName.indexOf('[') >= 0 || tagName.indexOf('(') >= 0) {
                return tagName;
            }
            if (!tagName.isEmpty()) {
                char first = tagName.charAt(0);
                if (Character.isUpperCase(first) || first == '_' || first == '$') {
                    return tagName;
                }
            }
            return stringLiteral(tagName);
        }

        private String attributeKey(String name) {
            if (!name.isEmpty() && isIdentifierStart(name.charAt(0))) {
                boolean valid = true;
                for (int i = 1; i < name.length(); i++) {
                    if (!isIdentifierPart(name.charAt(i))) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    return name;
                }
            }
            return stringLiteral(name);
        }

        private int readJsxNameEnd(int start) {
            int i = start;
            while (i < length) {
                char c = source.charAt(i);
                // < 用于泛型组件 <Foo<number>/> —— 在 < 处停，让上层识别并跳过泛型实参
                if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '{' || c == '=' || c == '<') {
                    break;
                }
                i++;
            }
            return i;
        }

        /**
         * 若 index 处起是泛型组件的 {@code <T, U>} 实参段（紧跟标签名后），返回其结束位置（{@code >} 之后）；
         * 否则返回 index。跳过字符串/嵌套尖括号。注意：TSX 泛型实参里不会出现 {@code /}（那是自闭合），
         * 遇到 {@code />} 当作无泛型（实际是 {@code <} 比较表达式）。
         */
        private int skipGenericArgsIfPresent(int index) {
            if (index >= length || source.charAt(index) != '<') return index;
            int depth = 0;
            int i = index;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c); continue; }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '<') depth++;
                else if (c == '>') { depth--; if (depth == 0) return i + 1; }
                else if (depth == 1 && c == '/') return index; // </Foo 自闭合的 < 不可能是泛型
                i++;
            }
            return index; // 不闭合，当作无泛型，交给后续报错
        }

        private int readAttributeNameEnd(int start) {
            int i = start;
            while (i < length) {
                char c = source.charAt(i);
                if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '=' || c == '{') {
                    break;
                }
                i++;
            }
            return i;
        }

        private boolean looksLikeJsxStart(int index) {
            int next = skipWhitespace(index + 1);
            if (next >= length) {
                return false;
            }
            char first = source.charAt(next);
            if (first == '/' || first == '>') {
                return true;
            }
            if (!isIdentifierStart(first) && first != '.' && first != '(' && first != '_') {
                return false;
            }
            // TSX 的 <T>(x: T) => x 与 JSX 开标签同形。先识别完整泛型箭头，
            // 交给后续 TS 擦除；否则会被 parseJsx 当成未闭合元素。
            if (looksLikeTsxGenericArrow(index)) {
                return false;
            }

            int previous = previousNonWhitespace(index - 1);
            if (previous < 0) {
                return true;
            }
            char previousChar = source.charAt(previous);
            if ("=(:,[!&|?;{}<>+-*/%".indexOf(previousChar) >= 0) {
                return true;
            }
            if (isIdentifierPart(previousChar)) {
                int wordStart = previous;
                while (wordStart >= 0 && isIdentifierPart(source.charAt(wordStart))) {
                    wordStart--;
                }
                String word = source.substring(wordStart + 1, previous + 1);
                return "return".equals(word)
                        || "throw".equals(word)
                        || "case".equals(word)
                        || "default".equals(word)
                        || "new".equals(word)
                        || "typeof".equals(word)
                        || "delete".equals(word)
                        || "void".equals(word)
                        || "await".equals(word);
            }
            return false;
        }

        /** 判断 <T>(...) => 或 <T,>(...) => 的 TSX 泛型箭头歧义形式。 */
        private boolean looksLikeTsxGenericArrow(int start) {
            int typeParametersEnd = skipGenericArgsIfPresent(start);
            if (typeParametersEnd == start) {
                return false;
            }
            int parameters = skipWhitespace(typeParametersEnd);
            if (peek(parameters) != '(') {
                return false;
            }
            int closeParameters = findMatchingParen(parameters);
            if (closeParameters < 0) {
                return false;
            }
            int afterParameters = skipWhitespace(closeParameters + 1);
            if (peek(afterParameters) == ':') {
                afterParameters = skipTsType(afterParameters + 1);
            }
            afterParameters = skipWhitespace(afterParameters);
            return peek(afterParameters) == '=' && peek(afterParameters + 1) == '>';
        }

        private int findMatchingParen(int openParen) {
            int depth = 0;
            for (int i = openParen; i < length; i++) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') { i = skipString(i, c) - 1; continue; }
                if (c == '`') { i = skipTemplate(i) - 1; continue; }
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) return i;
            }
            return -1;
        }

        private int skipTsType(int index) {
            int angleDepth = 0;
            int i = index;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '<') angleDepth++;
                else if (c == '>') angleDepth--;
                else if (angleDepth == 0 && (c == '=' || c == ',' || c == ')' || c == ';' || c == '\n' || c == '\r')) break;
                i++;
            }
            return i;
        }

        private boolean isIgnorableExpression(String inner) {
            int i = 0;
            while (i < inner.length()) {
                char c = inner.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c == '/' && i + 1 < inner.length()) {
                    char next = inner.charAt(i + 1);
                    if (next == '/') {
                        i = skipLineComment(inner, i + 2);
                        continue;
                    }
                    if (next == '*') {
                        i = skipBlockComment(inner, i + 2);
                        continue;
                    }
                }
                return false;
            }
            return true;
        }

        private int findMatchingBrace(int openBrace) {
            int depth = 0;
            int i = openBrace;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipString(i, c); continue;
                }
                if (c == '`') { i = skipTemplate(i); continue; }
                if (c == '/') {
                    if (i + 1 < length && source.charAt(i + 1) == '/') { i = skipLineComment(i + 2); continue; }
                    if (i + 1 < length && source.charAt(i + 1) == '*') { i = skipBlockComment(i + 2); continue; }
                    // 正则体可含 }（含字符类）。仅在 JS 正则可起始的位置跳过，
                    // 避免把 JSX closing tag 的 </ 中 / 错作正则。
                    int previous = previousNonWhitespace(i - 1);
                    if (previous >= 0 && source.charAt(previous) != '<'
                            && (looksLikeRegexStart(i) || followsControlCondition(i, previous))) {
                        i = skipRegex(i + 1);
                        continue;
                    }
                }
                if (c == '{') { depth++; }
                else if (c == '}') { depth--; if (depth == 0) return i; }
                i++;
            }
            throw jsxError("Unterminated JSX expression", openBrace);
        }

        /**
         * A regex literal may begin immediately after a control statement's closing parenthesis,
         * e.g. {@code if (condition) /pattern/.test(value)}. A bare {@code )} is otherwise a
         * value boundary, so it must not classify {@code 10 / 2} as a regex.
         */
        private boolean followsControlCondition(int slash, int closingParen) {
            if (source.charAt(closingParen) != ')') {
                return false;
            }
            int depth = 1;
            for (int i = closingParen - 1; i >= 0; i--) {
                char c = source.charAt(i);
                if (c == ')') {
                    depth++;
                } else if (c == '(' && --depth == 0) {
                    int wordEnd = previousNonWhitespace(i - 1);
                    if (wordEnd >= 1 && source.charAt(wordEnd) == '/' && source.charAt(wordEnd - 1) == '*') {
                        int commentStart = source.lastIndexOf("/*", wordEnd - 2);
                        if (commentStart < 0) {
                            return false;
                        }
                        wordEnd = previousNonWhitespace(commentStart - 1);
                    }
                    if (wordEnd < 0 || !isIdentifierPart(source.charAt(wordEnd))) {
                        return false;
                    }
                    int wordStart = wordEnd;
                    while (wordStart >= 0 && isIdentifierPart(source.charAt(wordStart))) {
                        wordStart--;
                    }
                    String keyword = source.substring(wordStart + 1, wordEnd + 1);
                    return "if".equals(keyword) || "while".equals(keyword)
                            || "for".equals(keyword) || "with".equals(keyword);
                }
            }
            return false;
        }

        private int skipWhitespace(int index) {
            int i = index;
            while (i < length && Character.isWhitespace(source.charAt(i))) {
                i++;
            }
            return i;
        }

        private char peek(int index) {
            return index >= 0 && index < length ? source.charAt(index) : '\0';
        }

        private int skipSlash(int slash) {
            if (slash + 1 >= length) {
                return slash;
            }
            char next = source.charAt(slash + 1);
            if (next == '/') {
                return skipLineComment(slash + 2);
            }
            if (next == '*') {
                return skipBlockComment(slash + 2);
            }
            if (looksLikeRegexStart(slash)) {
                return skipRegex(slash + 1);
            }
            return slash;
        }

        private int skipString(int start, char quote) {
            return NekoSourceLexerBase.skipString(source, length, start, quote);
        }

        private int skipTemplate(int start) {
            return NekoSourceLexerBase.skipTemplate(source, length, start);
        }

        private int skipLineComment(int start) {
            return NekoSourceLexerBase.skipLineComment(source, length, start);
        }

        private int skipLineComment(String text, int start) {
            return NekoSourceLexerBase.skipLineComment(text, text.length(), start);
        }

        private int skipBlockComment(int start) {
            return NekoSourceLexerBase.skipBlockComment(source, length, start);
        }

        private int skipBlockComment(String text, int start) {
            return NekoSourceLexerBase.skipBlockComment(text, text.length(), start);
        }

        private int skipRegex(int start) {
            return NekoSourceLexerBase.skipRegex(source, length, start);
        }

        private boolean looksLikeRegexStart(int slash) {
            return NekoSourceLexerBase.looksLikeRegexStart(source, length, slash);
        }

        private int previousNonWhitespace(int index) {
            return NekoSourceLexerBase.previousNonWhitespace(source, length, index);
        }

        private boolean isIdentifierStart(char c) {
            return NekoSourceLexerBase.isIdentifierStart(c);
        }

        private boolean isIdentifierPart(char c) {
            return NekoSourceLexerBase.isIdentifierPart(c);
        }

        private ParseResult factoryCall(int originalStart, GeneratedPart typeExpression, GeneratedPart propsExpression, List<GeneratedPart> children, int nextIndex) {
            if (automatic) {
                // 标准 automatic runtime：
                // - 0/1 child → jsx(type, props)
                // - 2+ children → jsxs(type, props)
                // - children 始终放在 props.children（单值或数组）
                boolean multi = children.size() > 1;
                if (multi) {
                    usedJsxs = true;
                } else {
                    usedJsx = true;
                }
                GeneratedAssembler call = new GeneratedAssembler(multi ? "jsxs(" : "jsx(", originalStart);
                call.append(typeExpression);
                call.append(", ");
                call.append(automaticProps(propsExpression, children));
                call.append(")");
                return new ParseResult(call.text(), call.mappings(), nextIndex);
            }
            GeneratedAssembler call = new GeneratedAssembler("globalThis.__nekoJsxFactory(", originalStart);
            call.append(typeExpression);
            call.append(", ");
            call.append(propsExpression);
            for (GeneratedPart child : children) {
                call.append(", ");
                call.append(child);
            }
            call.append(")");
            return new ParseResult(call.text(), call.mappings(), nextIndex);
        }

        private ParseResult fragmentCall(int originalStart, List<GeneratedPart> children, int nextIndex) {
            if (automatic) {
                // Fragment 是类型值，不是可调用函数：jsx(Fragment, props) / jsxs(Fragment, props)
                usedFragment = true;
                boolean multi = children.size() > 1;
                if (multi) {
                    usedJsxs = true;
                } else {
                    usedJsx = true;
                }
                GeneratedAssembler call = new GeneratedAssembler(multi ? "jsxs(" : "jsx(", originalStart);
                call.append("Fragment");
                call.append(", ");
                call.append(automaticProps(GeneratedPart.unmapped("null"), children));
                call.append(")");
                return new ParseResult(call.text(), call.mappings(), nextIndex);
            }
            GeneratedAssembler call = new GeneratedAssembler("globalThis.__nekoJsxFragment(", originalStart);
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) {
                    call.append(", ");
                }
                call.append(children.get(i));
            }
            call.append(")");
            return new ParseResult(call.text(), call.mappings(), nextIndex);
        }

        /**
         * 构造 automatic runtime 的 props 对象。
         * 无 children 时保留原 props（{@code null} 或对象字面量）；
         * 有 children 时合并为 {@code { ...props, children: child | [..] }}。
         */
        private GeneratedPart automaticProps(GeneratedPart propsExpression, List<GeneratedPart> children) {
            if (children == null || children.isEmpty()) {
                return propsExpression == null ? GeneratedPart.unmapped("null") : propsExpression;
            }
            GeneratedAssembler object = new GeneratedAssembler("{", -1);
            boolean wroteField = false;
            if (propsExpression != null) {
                String propsText = propsExpression.text();
                if (propsText != null && !"null".equals(propsText)) {
                    String inner = propsText;
                    if (inner.startsWith("{") && inner.endsWith("}")) {
                        inner = inner.substring(1, inner.length() - 1).trim();
                    }
                    if (!inner.isEmpty()) {
                        // 属性字段原样拷贝；mapping 在 props 层已不精确，保持文本正确优先
                        object.append(inner);
                        wroteField = true;
                    }
                }
            }
            if (wroteField) {
                object.append(", ");
            }
            object.append("children: ");
            if (children.size() == 1) {
                object.append(children.get(0));
            } else {
                object.append("[");
                for (int i = 0; i < children.size(); i++) {
                    if (i > 0) {
                        object.append(", ");
                    }
                    object.append(children.get(i));
                }
                object.append("]");
            }
            object.append("}");
            return new GeneratedPart(object.text(), object.mappings());
        }

        private GeneratedPart objectPart(List<GeneratedPart> props) {
            GeneratedAssembler object = new GeneratedAssembler("{", -1);
            for (int i = 0; i < props.size(); i++) {
                if (i > 0) {
                    object.append(", ");
                }
                object.append(props.get(i));
            }
            object.append("}");
            return new GeneratedPart(object.text(), object.mappings());
        }

        private String stringLiteral(String value) {
            if (value == null) {
                return "''";
            }
            return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
        }

        private IllegalArgumentException jsxError(String message, int index) {
            return new IllegalArgumentException(message + " in " + file + " at " + position(index));
        }

        private String position(int index) {
            return NekoSourceLexerBase.position(source, length, index);
        }
    }

    private static final class GeneratedAssembler {
        private final StringBuilder text = new StringBuilder();
        private final List<NekoSourceMapBuilder.MappingPoint> mappings = new ArrayList<>();

        private GeneratedAssembler(String prefix, int originalOffset) {
            if (originalOffset >= 0) {
                mappings.add(new NekoSourceMapBuilder.MappingPoint(0, originalOffset));
            }
            text.append(prefix);
        }

        private void append(String value) {
            text.append(value);
        }

        private void append(GeneratedPart part) {
            int offset = text.length();
            text.append(part.text());
            for (NekoSourceMapBuilder.MappingPoint mapping : part.mappings()) {
                mappings.add(new NekoSourceMapBuilder.MappingPoint(offset + mapping.generatedOffset(), mapping.originalOffset()));
            }
        }

        private String text() {
            return text.toString();
        }

        private List<NekoSourceMapBuilder.MappingPoint> mappings() {
            return mappings;
        }
    }

    private record JsxTransformResult(String code, String sourceMap) {}
    private record GeneratedPart(String text, List<NekoSourceMapBuilder.MappingPoint> mappings) {
        private static GeneratedPart unmapped(String text) {
            return new GeneratedPart(text, List.of());
        }
    }
    private record ParseResult(String text, List<NekoSourceMapBuilder.MappingPoint> mappings, int nextIndex) {}
    private record ExpressionResult(GeneratedPart part, int nextIndex) {}
    private record AttributeResult(GeneratedPart part, int nextIndex) {}
}
