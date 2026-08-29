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
            // automatic runtime：key 不进 props，单独作为 jsx()/jsxs() 的第三实参（React 约定）
            GeneratedPart keyExpression = null;

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
                if (automatic && "key".equals(attribute.name()) && attribute.valuePart() != null) {
                    keyExpression = attribute.valuePart();
                } else {
                    props.add(attribute.part());
                }
                index = attribute.nextIndex();
            }

            GeneratedPart typeExpression = new GeneratedPart(jsxTagExpression(tagExpr), List.of(new NekoSourceMapBuilder.MappingPoint(0, nameStart)));
            GeneratedPart propsExpression = props.isEmpty() ? GeneratedPart.unmapped("null") : objectPart(props);
            if (selfClosing) {
                return factoryCall(start, typeExpression, propsExpression, List.of(), keyExpression, index);
            }

            List<GeneratedPart> children = new ArrayList<>();
            index = parseChildren(index, tagName, children);
            return factoryCall(start, typeExpression, propsExpression, children, keyExpression, index);
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
            JsxTransformResult lowered = transformDetailed(inner);
            return new ExpressionResult(new GeneratedPart("(" + lowered.code() + ")", rebasedMappings(lowered, inner, 1, start + 1)), end + 1);
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
                return new AttributeResult(new GeneratedPart(attributeKey(name) + ": true", List.of(new NekoSourceMapBuilder.MappingPoint(0, start))), nameEnd, name, null);
            }

            index = skipWhitespace(index + 1);
            if (index >= length) {
                throw jsxError("Missing JSX attribute value", start);
            }
            char valueStart = source.charAt(index);
            if (valueStart == '\'' || valueStart == '"') {
                int valueEnd = skipString(index, valueStart);
                // 与 React/TS 的 JSX 语义一致：字符串属性值解码 HTML 实体（&amp; → &），
                // 并经 stringLiteral 转义（多行属性值原样拷贝会产出非法 JS 字符串字面量）
                String decoded = decodeHtmlEntities(source.substring(index + 1, valueEnd - 1));
                GeneratedPart valuePart = GeneratedPart.unmapped(stringLiteral(decoded));
                String text = attributeKey(name) + ": " + valuePart.text();
                return new AttributeResult(new GeneratedPart(text, List.of(
                        new NekoSourceMapBuilder.MappingPoint(0, start),
                        new NekoSourceMapBuilder.MappingPoint(text.indexOf(source.charAt(index)), index)
                )), valueEnd, name, valuePart);
            }
            if (valueStart == '{') {
                int valueEnd = findMatchingBrace(index);
                String inner = source.substring(index + 1, valueEnd);
                JsxTransformResult lowered = transformDetailed(inner);
                String transformed = lowered.code();
                if (transformed.isBlank()) {
                    throw jsxError("Missing JSX attribute expression", start);
                }
                String text = attributeKey(name) + ": (" + transformed + ")";
                List<NekoSourceMapBuilder.MappingPoint> mappings = new ArrayList<>();
                mappings.add(new NekoSourceMapBuilder.MappingPoint(0, start));
                // generatedStart 相对 text：紧随 "name: (" 之后的 transformed 起点 = '(' 后一位
                mappings.addAll(rebasedMappings(lowered, inner, text.indexOf('(') + 1, index + 1));
                GeneratedPart valuePart = new GeneratedPart("(" + transformed + ")",
                        rebasedMappings(lowered, inner, 1, index + 1));
                return new AttributeResult(new GeneratedPart(text, mappings), valueEnd + 1, name, valuePart);
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
            return transformDetailed(innerSource).code();
        }

        /**
         * 递归 lowering 子表达式，返回转换后代码与内部 source map。
         *
         * <p>内部 source map 把 transformed 的行列映射回 innerSource 的行列。
         * {@link #rebasedMappings} 据此重建到外层源文件的精确偏移，替代此前按 1:1
         * 线性合成的近似偏移（嵌套 JSX / 实体解码会改变 transformed 长度，旧逻辑会漂移）。
         */
        private JsxTransformResult transformDetailed(String innerSource) {
            if (innerSource == null || innerSource.isBlank()) {
                return new JsxTransformResult(innerSource == null ? "" : innerSource, null);
            }
            // 子表达式递归 lowering：复用本 Transpiler 的 automatic 标志（同一文件 runtime 模式一致）；
            // 内部不再 prepend import（由最外层统一注入）。
            Transpiler inner = new Transpiler(file, innerSource, automatic);
            inner.suppressRuntimeImport = true;
            inner.usedJsx = this.usedJsx;
            inner.usedFragment = this.usedFragment;
            inner.usedJsxs = this.usedJsxs;
            JsxTransformResult result = inner.transpileDetailed();
            if (inner.usedJsx) this.usedJsx = true;
            if (inner.usedFragment) this.usedFragment = true;
            if (inner.usedJsxs) this.usedJsxs = true;
            return result;
        }

        /**
         * 把内部 transpiler 的 source map 重基（rebase）到外层生成文本/源文件的偏移。
         *
         * <p>内部 source map 的每个 segment 是 (genLine, genCol)→(srcLine, srcCol)，
         * 行列均相对 transformed / innerSource。重基：
         * <ul>
         *   <li>generated 偏移 = generatedStart + transformed 内偏移</li>
         *   <li>original 偏移  = originalStart  + innerSource 内偏移</li>
         * </ul>
         * 这样嵌套表达式内部的每一行都能精确指回外层源文件，而非按 1:1 长度近似。
         * 解析失败（空 map / 非 v3 / 无 mappings）时回退到单锚点，保证不丢映射起点。
         */
        private List<NekoSourceMapBuilder.MappingPoint> rebasedMappings(
                JsxTransformResult inner, String innerSource,
                int generatedStart, int originalStart) {
            List<NekoSourceMapBuilder.MappingPoint> mappings = new ArrayList<>();
            mappings.add(new NekoSourceMapBuilder.MappingPoint(generatedStart, originalStart));
            if (inner == null || inner.sourceMap() == null || inner.sourceMap().isEmpty()
                    || inner.code() == null || inner.code().isEmpty()) {
                return mappings;
            }
            int[][] segments = decodeSourceMapMappings(inner.sourceMap());
            if (segments.length == 0) {
                return mappings;
            }
            String transformed = inner.code();
            int[] generatedLineStarts = lineStartOffsets(transformed);
            int[] sourceLineStarts = lineStartOffsets(innerSource);
            for (int[] seg : segments) {
                int genLine = seg[0];
                int genCol = seg[1];
                int srcLine = seg[2];
                int srcCol = seg[3];
                int genOffset = offsetAt(generatedLineStarts, genLine, genCol);
                int srcOffset = offsetAt(sourceLineStarts, srcLine, srcCol);
                if (genOffset < 0 || srcOffset < 0) {
                    continue;
                }
                mappings.add(new NekoSourceMapBuilder.MappingPoint(
                        generatedStart + genOffset, originalStart + srcOffset));
            }
            // 保持按 generated 偏移升序（appendMapped 要求升序输入）
            mappings.sort(java.util.Comparator.comparingInt(NekoSourceMapBuilder.MappingPoint::generatedOffset));
            return mappings;
        }

        /** 行首偏移表：lineStarts[i] = 第 i 行起始字符在文本中的偏移（第 0 行恒为 0）。 */
        private static int[] lineStartOffsets(String text) {
            java.util.List<Integer> starts = new ArrayList<>();
            starts.add(0);
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    starts.add(i + 1);
                }
            }
            int[] arr = new int[starts.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = starts.get(i);
            }
            return arr;
        }

        private static int offsetAt(int[] lineStarts, int line, int col) {
            if (line < 0 || line >= lineStarts.length) {
                return -1;
            }
            return lineStarts[line] + col;
        }

        /**
         * 解码 v3 source map 的 mappings 字段为 segment 数组。
         * 每个 segment = {genLine, genCol, srcLine, srcCol}（忽略 source index，恒为 0）。
         * 行列均按累计增量还原为绝对值。
         */
        private static int[][] decodeSourceMapMappings(String sourceMapJson) {
            com.google.gson.JsonObject root;
            try {
                root = com.google.gson.JsonParser.parseString(sourceMapJson).getAsJsonObject();
            } catch (Exception ignored) {
                return new int[0][];
            }
            com.google.gson.JsonElement mappingsEl = root.get("mappings");
            if (mappingsEl == null) {
                return new int[0][];
            }
            String mappings = mappingsEl.getAsString();
            java.util.List<int[]> out = new ArrayList<>();
            int genLine = 0;
            int genCol = 0;
            int srcLine = 0;
            int srcCol = 0;
            int i = 0;
            int n = mappings.length();
            while (i < n) {
                char c = mappings.charAt(i);
                if (c == ';') {
                    genLine++;
                    genCol = 0;
                    i++;
                    continue;
                }
                if (c == ',') {
                    i++;
                    continue;
                }
                // 解一个 segment：[genColDelta, srcIndexDelta, srcLineDelta, srcColDelta, (nameDelta)]
                int[] fieldDeltas = new int[5];
                int fields = 0;
                while (i < n) {
                    char ch = mappings.charAt(i);
                    if (ch == ';' || ch == ',') {
                        break;
                    }
                    int[] vlq = decodeVlq(mappings, i);
                    fieldDeltas[fields++] = vlq[0];
                    i = vlq[1];
                }
                if (fields < 4) {
                    // 缺字段（如纯 generated 映射）——对 JSX 表达式映射无意义，跳过
                    continue;
                }
                genCol += fieldDeltas[0];
                // fieldDeltas[1] 是 source index delta，恒为 0（单源），忽略
                srcLine += fieldDeltas[2];
                srcCol += fieldDeltas[3];
                out.add(new int[]{genLine, genCol, srcLine, srcCol});
            }
            return out.toArray(new int[0][]);
        }

        /** 解一个 Base64 VLQ，返回 {value, nextIndex}。 */
        private static final String VLQ_CHARS =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

        private static int[] decodeVlq(String s, int start) {
            int shift = 0;
            int value = 0;
            int i = start;
            boolean continuation;
            do {
                int digit = VLQ_CHARS.indexOf(s.charAt(i));
                i++;
                continuation = (digit & 32) != 0;
                digit &= 31;
                value += digit << shift;
                shift += 5;
            } while (continuation && i < s.length());
            boolean negative = (value & 1) != 0;
            value >>>= 1;
            return new int[]{negative ? -value : value, i};
        }

        /**
         * JSX 文本白空白的 React/Babel 规则（换行驱动，而非一刀切 trim）：
         * 换行后的行首空白与换行前的行尾空白去除、纯空白行删除、保留行之间的换行折叠为
         * 单个空格；同一行内的空白（元素间空格 {@code <b>x</b> <i>y</i>}）原样保留——
         * 旧实现 {\s+→" "}+trim 把元素间有效空格也丢了。
         */
        private String normalizeText(String raw) {
            if (raw == null) {
                return null;
            }
            String[] lines = raw.split("\r\n|\n|\r", -1);
            int lastNonEmpty = 0;
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].replace(" ", "").replace("\t", "").isEmpty()) {
                    lastNonEmpty = i;
                }
            }
            StringBuilder sb = new StringBuilder(raw.length());
            for (int i = 0; i <= lastNonEmpty; i++) {
                String line = lines[i];
                boolean isFirst = i == 0;
                boolean isLast = i == lastNonEmpty;
                if (!isFirst) {
                    line = line.replaceAll("^[ \t]+", "");
                }
                if (!isLast) {
                    line = line.replaceAll("[ \t]+$", "");
                }
                if (!line.isEmpty()) {
                    sb.append(line);
                    if (!isLast) {
                        sb.append(' ');
                    }
                }
            }
            String normalized = sb.toString();
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
            return NekoSourceLexerBase.skipSlash(source, length, slash);
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

        private ParseResult factoryCall(int originalStart, GeneratedPart typeExpression, GeneratedPart propsExpression, List<GeneratedPart> children, GeneratedPart keyExpression, int nextIndex) {
            if (automatic) {
                // 标准 automatic runtime：
                // - 0/1 child → jsx(type, props, key?)
                // - 2+ children → jsxs(type, props, key?)
                // - children 始终放在 props.children（单值或数组）；key 是第三实参，不进 props
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
                if (keyExpression != null) {
                    call.append(", ");
                    call.append(keyExpression);
                }
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
    /** name 为 null 表示 spread 属性；valuePart 为属性值表达式（布尔简写属性为 null），供 key 提取复用。*/
    private record AttributeResult(GeneratedPart part, int nextIndex, String name, GeneratedPart valuePart) {
        AttributeResult(GeneratedPart part, int nextIndex) {
            this(part, nextIndex, null, null);
        }
    }
}
