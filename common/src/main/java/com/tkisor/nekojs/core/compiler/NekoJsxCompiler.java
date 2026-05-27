package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.ScriptCompileResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class NekoJsxCompiler {
    private NekoJsxCompiler() {}

    public static ScriptCompileResult compileJsx(Path file, String source) {
        JsxTransformResult result = new Transpiler(file, source == null ? "" : source).transpileDetailed();
        return new ScriptCompileResult(result.code(), result.sourceMap());
    }

    public static ScriptCompileResult compileTsx(Path file, String source) {
        JsxTransformResult lowered = new Transpiler(file, source == null ? "" : source).transpileDetailed();
        NekoTypeScriptCompiler.TypeScriptTransformResult erased = NekoTypeScriptCompiler.erasePreservingSourceMap(file, lowered.code(), lowered.sourceMap());
        return new ScriptCompileResult(erased.code(), erased.sourceMap());
    }

    private static final class Transpiler {
        private final Path file;
        private final String source;
        private final int length;

        private Transpiler(Path file, String source) {
            this.file = file;
            this.source = source == null ? "" : source;
            this.length = this.source.length();
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
            return new JsxTransformResult(output.code(), output.sourceMap());
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

            GeneratedPart typeExpression = new GeneratedPart(jsxTagExpression(tagName), List.of(new NekoSourceMapBuilder.MappingPoint(0, nameStart)));
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
            return new Transpiler(file, innerSource).transpile();
        }

        private List<NekoSourceMapBuilder.MappingPoint> expressionMappings(String transformed, int generatedStart, int originalStart) {
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
            return normalized.isEmpty() ? null : normalized;
        }

        private String jsxTagExpression(String tagName) {
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
                if (Character.isWhitespace(c) || c == '/' || c == '>' || c == '{' || c == '=') {
                    break;
                }
                i++;
            }
            return i;
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
                    i = skipString(i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipTemplate(i);
                    continue;
                }
                if (c == '/') {
                    int skipped = skipSlash(i);
                    if (skipped != i) {
                        i = skipped;
                        continue;
                    }
                }
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
                i++;
            }
            throw jsxError("Unterminated JSX expression", openBrace);
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
            int i = start;
            boolean inClass = false;
            while (i < length) {
                char c = source.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '[') {
                    inClass = true;
                } else if (c == ']') {
                    inClass = false;
                } else if (c == '/' && !inClass) {
                    i++;
                    while (i < length && isIdentifierPart(source.charAt(i))) {
                        i++;
                    }
                    return i;
                }
                i++;
            }
            return length;
        }

        private boolean looksLikeRegexStart(int slash) {
            int previous = previousNonWhitespace(slash - 1);
            if (previous < 0) {
                return true;
            }
            char c = source.charAt(previous);
            return "=(:,[!&|?;{}\n\r".indexOf(c) >= 0;
        }

        private int previousNonWhitespace(int index) {
            int i = Math.min(index, length - 1);
            while (i >= 0 && Character.isWhitespace(source.charAt(i))) {
                i--;
            }
            return i;
        }

        private boolean isIdentifierStart(char c) {
            return NekoSourceLexerBase.isIdentifierStart(c);
        }

        private boolean isIdentifierPart(char c) {
            return NekoSourceLexerBase.isIdentifierPart(c);
        }

        private ParseResult factoryCall(int originalStart, GeneratedPart typeExpression, GeneratedPart propsExpression, List<GeneratedPart> children, int nextIndex) {
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
            int line = 1;
            int column = 1;
            for (int i = 0; i < index && i < length; i++) {
                if (source.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return line + ":" + column;
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
