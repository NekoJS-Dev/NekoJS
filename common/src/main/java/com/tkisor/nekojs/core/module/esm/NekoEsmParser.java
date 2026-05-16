package com.tkisor.nekojs.core.module.esm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NekoEsmParser {
    private static final Pattern IMPORT_FROM_PATTERN = Pattern.compile("^import\\s+(.+?)\\s+from\\s+(['\\\"])(.+?)\\2\\s*;?\\s*$", Pattern.DOTALL);
    private static final Pattern IMPORT_SIDE_EFFECT_PATTERN = Pattern.compile("^import\\s+(['\\\"])(.+?)\\1\\s*;?\\s*$", Pattern.DOTALL);
    private static final Pattern EXPORT_FROM_PATTERN = Pattern.compile("^export\\s+(.+?)\\s+from\\s+(['\\\"])(.+?)\\2\\s*;?\\s*$", Pattern.DOTALL);
    private static final Pattern EXPORT_DECL_PATTERN = Pattern.compile("^export\\s+((?:async\\s+)?function\\*?|class|const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)", Pattern.DOTALL);
    private static final Pattern EXPORT_DEFAULT_NAMED_DECL_PATTERN = Pattern.compile("^export\\s+default\\s+((?:async\\s+)?function\\*?|class)\\s+([A-Za-z_$][A-Za-z0-9_$]*)", Pattern.DOTALL);
    private static final Pattern EXPORT_DEFAULT_ANON_DECL_PATTERN = Pattern.compile("^export\\s+default\\s+((?:async\\s+)?function\\*?|class)(\\s|\\(|\\{)", Pattern.DOTALL);

    private final Path file;
    private final String code;
    private final int length;
    private final List<NekoEsmStatement> statements = new ArrayList<>();
    private final List<NekoEsmRuntimeExpression> runtimeExpressions = new ArrayList<>();
    private final List<NekoEsmLocalBinding> localBindings = new ArrayList<>();
    private boolean module;
    private boolean topLevelAwait;
    private int braceDepth;
    private int parenDepth;
    private int bracketDepth;

    public NekoEsmParser(Path file, String code) {
        this.file = file;
        this.code = code == null ? "" : code;
        this.length = this.code.length();
    }

    public NekoEsmModuleAst parse() {
        int i = 0;
        while (i < length) {
            char c = code.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipString(i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(i);
                continue;
            }
            if (c == '/') {
                if (peek(i + 1) == '/') {
                    i = skipLineComment(i + 2);
                    continue;
                }
                if (peek(i + 1) == '*') {
                    i = skipBlockComment(i + 2);
                    continue;
                }
                if (looksLikeRegexStart(i)) {
                    i = skipRegex(i + 1);
                    continue;
                }
            }
            if (isIdentifierStart(c)) {
                int end = readIdentifierEnd(i + 1);
                String word = code.substring(i, end);
                if (topLevel()) {
                    if ("import".equals(word)) {
                        int next = nextNonWhitespace(end);
                        if (next < length && code.charAt(next) == '(') {
                            addDynamicImportExpression(i, end);
                        } else if (next < length && code.charAt(next) == '.') {
                            int metaEnd = maybeReadImportMeta(i, next + 1);
                            if (metaEnd > i) {
                                i = metaEnd;
                                continue;
                            }
                        } else {
                            i = readModuleStatement(i);
                            continue;
                        }
                    } else if ("export".equals(word)) {
                        i = readModuleStatement(i);
                        continue;
                    } else if ("await".equals(word)) {
                        topLevelAwait = true;
                    } else {
                        recordLocalDeclaration(i, word);
                    }
                } else if ("import".equals(word)) {
                    int next = nextNonWhitespace(end);
                    if (next < length && code.charAt(next) == '(') {
                        addDynamicImportExpression(i, end);
                    } else if (next < length && code.charAt(next) == '.') {
                        int metaEnd = maybeReadImportMeta(i, next + 1);
                        if (metaEnd > i) {
                            i = metaEnd;
                            continue;
                        }
                    }
                }
                i = end;
                continue;
            }
            switch (c) {
                case '{' -> braceDepth++;
                case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                case '(' -> parenDepth++;
                case ')' -> parenDepth = Math.max(0, parenDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                default -> {
                }
            }
            i++;
        }
        return new NekoEsmModuleAst(module, topLevelAwait, statements, runtimeExpressions, localBindings);
    }

    private void recordLocalDeclaration(int start, String word) {
        if (!"const".equals(word) && !"let".equals(word) && !"var".equals(word) && !"function".equals(word) && !"class".equals(word)) {
            return;
        }
        int declarationStart = nextNonWhitespace(start + word.length());
        if (declarationStart >= length) {
            return;
        }
        char first = code.charAt(declarationStart);
        if (isIdentifierStart(first)) {
            int nameEnd = readIdentifierEnd(declarationStart + 1);
            localBindings.add(new NekoEsmLocalBinding(code.substring(declarationStart, nameEnd), word, new NekoEsmSpan(start, nameEnd)));
            return;
        }
        if (("const".equals(word) || "let".equals(word) || "var".equals(word)) && (first == '{' || first == '[')) {
            int declarationEnd = findDeclarationPatternEnd(declarationStart);
            if (declarationEnd > declarationStart) {
                recordPatternBindings(code.substring(declarationStart, declarationEnd), word, new NekoEsmSpan(start, declarationEnd));
            }
        }
    }

    private int findDeclarationPatternEnd(int start) {
        int i = start;
        int depth = 0;
        while (i < length) {
            char c = code.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipString(i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(i);
                continue;
            }
            if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            else if (c == '=' && depth == 0) return i;
            else if ((c == ';' || c == '\n' || c == '\r') && depth == 0) return i;
            i++;
        }
        return length;
    }

    private void recordPatternBindings(String pattern, String kind, NekoEsmSpan span) {
        for (String name : extractPatternBindingNames(pattern)) {
            localBindings.add(new NekoEsmLocalBinding(name, kind, span));
        }
    }

    private List<String> extractPatternBindingNames(String pattern) {
        List<String> names = new ArrayList<>();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipPatternString(pattern, i, c);
                continue;
            }
            if (isIdentifierStart(c)) {
                int end = readPatternIdentifierEnd(pattern, i + 1);
                String word = pattern.substring(i, end);
                int next = nextPatternNonWhitespace(pattern, end);
                boolean propertyKey = next < pattern.length() && pattern.charAt(next) == ':';
                if (!propertyKey && !isPatternKeyword(word)) {
                    names.add(word);
                }
                i = end;
                continue;
            }
            i++;
        }
        return names;
    }

    private int skipPatternString(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) return i + 1;
            i++;
        }
        return text.length();
    }

    private int readPatternIdentifierEnd(String text, int start) {
        int i = start;
        while (i < text.length() && isIdentifierPart(text.charAt(i))) i++;
        return i;
    }

    private int nextPatternNonWhitespace(String text, int index) {
        int i = index;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        return i;
    }

    private boolean isPatternKeyword(String word) {
        return "const".equals(word) || "let".equals(word) || "var".equals(word);
    }

    private int readModuleStatement(int start) {
        int i = start;
        int localBrace = 0;
        int localParen = 0;
        int localBracket = 0;
        while (i < length) {
            char c = code.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipString(i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(i);
                continue;
            }
            if (c == '/') {
                if (peek(i + 1) == '/') {
                    i = skipLineComment(i + 2);
                    continue;
                }
                if (peek(i + 1) == '*') {
                    i = skipBlockComment(i + 2);
                    continue;
                }
                if (looksLikeRegexStart(i)) {
                    i = skipRegex(i + 1);
                    continue;
                }
            }
            switch (c) {
                case '{' -> localBrace++;
                case '}' -> {
                    if (localBrace > 0) localBrace--;
                    if (localBrace == 0 && localParen == 0 && localBracket == 0 && blockModuleStatement(start)) {
                        addStatement(start, i + 1);
                        return i + 1;
                    }
                }
                case '(' -> localParen++;
                case ')' -> {
                    if (localParen > 0) localParen--;
                }
                case '[' -> localBracket++;
                case ']' -> {
                    if (localBracket > 0) localBracket--;
                }
                case ';' -> {
                    if (localBrace == 0 && localParen == 0 && localBracket == 0) {
                        addStatement(start, i + 1);
                        return i + 1;
                    }
                }
                case '\n', '\r' -> {
                    if (localBrace == 0 && localParen == 0 && localBracket == 0 && newlineTerminatesModuleStatement(start)) {
                        addStatement(start, i);
                        return i;
                    }
                }
                default -> {
                }
            }
            i++;
        }
        addStatement(start, length);
        return length;
    }

    private void addStatement(int start, int end) {
        module = true;
        String raw = code.substring(start, end);
        String trimmed = raw.trim();
        NekoEsmSpan span = new NekoEsmSpan(start, end);
        if (trimmed.startsWith("import")) {
            NekoEsmImportDecl importDecl = parseImport(span, trimmed);
            recordImportBindings(span, importDecl);
            statements.add(importDecl);
            return;
        }
        if (trimmed.startsWith("export")) {
            statements.add(parseExport(span, trimmed));
            return;
        }
        throw error("Unsupported ESM statement: " + oneLine(trimmed));
    }

    private void recordImportBindings(NekoEsmSpan span, NekoEsmImportDecl importDecl) {
        if (importDecl.defaultName() != null) {
            localBindings.add(new NekoEsmLocalBinding(importDecl.defaultName(), "import", span));
        }
        if (importDecl.namespaceName() != null) {
            localBindings.add(new NekoEsmLocalBinding(importDecl.namespaceName(), "import", span));
        }
        for (NekoEsmBinding binding : importDecl.namedBindings()) {
            localBindings.add(new NekoEsmLocalBinding(binding.local(), "import", span));
        }
    }

    private NekoEsmImportDecl parseImport(NekoEsmSpan span, String statement) {
        Matcher sideEffect = IMPORT_SIDE_EFFECT_PATTERN.matcher(statement);
        if (sideEffect.matches()) {
            return new NekoEsmImportDecl(span, statement, sideEffect.group(2), null, null, List.of(), true);
        }
        Matcher from = IMPORT_FROM_PATTERN.matcher(statement);
        if (!from.matches()) {
            throw error("Unsupported import syntax: " + oneLine(statement));
        }
        ImportBindings bindings = parseImportBindings(from.group(1).trim());
        return new NekoEsmImportDecl(span, statement, from.group(3), bindings.defaultName, bindings.namespaceName, bindings.namedBindings, false);
    }

    private ImportBindings parseImportBindings(String raw) {
        String defaultName = null;
        String namespaceName = null;
        List<NekoEsmBinding> namedBindings = new ArrayList<>();
        int comma = topLevelComma(raw);
        if (comma >= 0) {
            String first = raw.substring(0, comma).trim();
            String rest = raw.substring(comma + 1).trim();
            if (!first.isEmpty()) {
                if (!isIdentifier(first)) throw error("Unsupported default import binding: " + oneLine(first));
                defaultName = first;
            }
            ImportBindings nested = parseImportBindings(rest);
            namespaceName = nested.namespaceName;
            namedBindings.addAll(nested.namedBindings);
            return new ImportBindings(defaultName, namespaceName, namedBindings);
        }
        if (raw.startsWith("*")) {
            Matcher matcher = Pattern.compile("^\\*\\s+as\\s+([A-Za-z_$][A-Za-z0-9_$]*)$").matcher(raw);
            if (!matcher.matches()) throw error("Unsupported namespace import syntax: " + oneLine(raw));
            return new ImportBindings(null, matcher.group(1), List.of());
        }
        if (raw.startsWith("{") && raw.endsWith("}")) {
            return new ImportBindings(null, null, parseNamedBindings(raw.substring(1, raw.length() - 1)));
        }
        if (isIdentifier(raw)) {
            return new ImportBindings(raw, null, List.of());
        }
        throw error("Unsupported import bindings: " + oneLine(raw));
    }

    private NekoEsmExportDecl parseExport(NekoEsmSpan span, String statement) {
        if (statement.startsWith("export default")) {
            return parseDefaultExport(span, statement);
        }
        Matcher from = EXPORT_FROM_PATTERN.matcher(statement);
        if (from.matches()) {
            String bindings = from.group(1).trim();
            String specifier = from.group(3);
            if (bindings.equals("*")) {
                return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.RE_EXPORT_ALL, specifier, null, null, null, null, List.of());
            }
            if (bindings.startsWith("*")) {
                Matcher matcher = Pattern.compile("^\\*\\s+as\\s+([A-Za-z_$][A-Za-z0-9_$]*)$").matcher(bindings);
                if (!matcher.matches()) throw error("Unsupported namespace re-export syntax: " + oneLine(statement));
                return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.RE_EXPORT_NAMESPACE, specifier, null, null, matcher.group(1), null, List.of());
            }
            if (bindings.startsWith("{") && bindings.endsWith("}")) {
                return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.RE_EXPORT_LIST, specifier, null, null, null, null, parseNamedBindings(bindings.substring(1, bindings.length() - 1)));
            }
            throw error("Unsupported re-export syntax: " + oneLine(statement));
        }
        if (statement.startsWith("export {")) {
            int open = statement.indexOf('{');
            int close = matchingBrace(statement, open);
            if (open < 0 || close < 0) throw error("Malformed export list: " + oneLine(statement));
            String tail = statement.substring(close + 1).trim();
            if (!tail.equals(";") && !tail.isEmpty()) throw error("Unsupported export list suffix: " + oneLine(statement));
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.LIST, null, null, null, null, null, parseNamedBindings(statement.substring(open + 1, close)));
        }
        Matcher declaration = EXPORT_DECL_PATTERN.matcher(statement);
        if (declaration.find()) {
            localBindings.add(new NekoEsmLocalBinding(declaration.group(2), declaration.group(1), span));
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DECLARATION, null, declaration.group(1), declaration.group(2), null, null, List.of());
        }
        throw error("Unsupported export syntax: " + oneLine(statement));
    }

    private NekoEsmExportDecl parseDefaultExport(NekoEsmSpan span, String statement) {
        Matcher named = EXPORT_DEFAULT_NAMED_DECL_PATTERN.matcher(statement);
        if (named.find()) {
            localBindings.add(new NekoEsmLocalBinding(named.group(2), named.group(1), span));
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DEFAULT_NAMED_DECLARATION, null, named.group(1), named.group(2), null, null, List.of());
        }
        Matcher anonymous = EXPORT_DEFAULT_ANON_DECL_PATTERN.matcher(statement);
        if (anonymous.find()) {
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DEFAULT_ANONYMOUS_DECLARATION, null, anonymous.group(1), null, null, null, List.of());
        }
        String expression = statement.replaceFirst("^export\\s+default\\s+", "");
        if (expression.endsWith(";")) expression = expression.substring(0, expression.length() - 1);
        return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DEFAULT_EXPRESSION, null, null, null, null, expression, List.of());
    }

    private void addDynamicImportExpression(int importStart, int importEnd) {
        int openParen = nextNonWhitespace(importEnd);
        int specifierStart = nextNonWhitespace(openParen + 1);
        if (specifierStart < length) {
            char quote = code.charAt(specifierStart);
            if (quote == '\'' || quote == '"') {
                int literalEnd = skipString(specifierStart, quote);
                int closeParen = nextNonWhitespace(literalEnd);
                if (closeParen < length && code.charAt(closeParen) == ')') {
                    String specifier = code.substring(specifierStart + 1, Math.max(specifierStart + 1, literalEnd - 1));
                    runtimeExpressions.add(new NekoEsmRuntimeExpression(
                            NekoEsmRuntimeExpressionKind.DYNAMIC_IMPORT,
                            new NekoEsmSpan(importStart, importEnd),
                            specifier,
                            new NekoEsmSpan(specifierStart, literalEnd)));
                    return;
                }
            }
        }
        runtimeExpressions.add(new NekoEsmRuntimeExpression(NekoEsmRuntimeExpressionKind.DYNAMIC_IMPORT, new NekoEsmSpan(importStart, importEnd)));
    }

    private int maybeReadImportMeta(int importStart, int metaStart) {
        int metaEnd = metaStart + "meta".length();
        if (!code.startsWith("meta", metaStart) || !wordBoundary(metaEnd)) return -1;
        int dot = nextNonWhitespace(metaEnd);
        if (dot >= length || code.charAt(dot) != '.') return -1;
        int propertyStart = nextNonWhitespace(dot + 1);
        int propertyEnd = readIdentifierEnd(propertyStart);
        String property = code.substring(propertyStart, propertyEnd);
        NekoEsmRuntimeExpressionKind kind = switch (property) {
            case "url" -> NekoEsmRuntimeExpressionKind.IMPORT_META_URL;
            case "filename" -> NekoEsmRuntimeExpressionKind.IMPORT_META_FILENAME;
            case "dirname" -> NekoEsmRuntimeExpressionKind.IMPORT_META_DIRNAME;
            case "resolve" -> NekoEsmRuntimeExpressionKind.IMPORT_META_RESOLVE;
            default -> null;
        };
        if (kind == null) return -1;
        runtimeExpressions.add(new NekoEsmRuntimeExpression(kind, new NekoEsmSpan(importStart, propertyEnd)));
        return propertyEnd;
    }

    private boolean newlineTerminatesModuleStatement(int start) {
        String prefix = code.substring(start, Math.min(length, start + 64)).trim();
        return prefix.startsWith("import")
                || prefix.startsWith("export {")
                || prefix.startsWith("export *")
                || prefix.startsWith("export const")
                || prefix.startsWith("export let")
                || prefix.startsWith("export var")
                || expressionDefaultExport(prefix);
    }

    private boolean blockModuleStatement(int start) {
        String prefix = code.substring(start, Math.min(length, start + 64)).trim();
        return prefix.startsWith("export function")
                || prefix.startsWith("export async function")
                || prefix.startsWith("export class")
                || prefix.startsWith("export default function")
                || prefix.startsWith("export default async function")
                || prefix.startsWith("export default class");
    }

    private boolean expressionDefaultExport(String prefix) {
        return prefix.startsWith("export default")
                && !prefix.startsWith("export default function")
                && !prefix.startsWith("export default async function")
                && !prefix.startsWith("export default class");
    }

    private boolean topLevel() {
        return braceDepth == 0 && parenDepth == 0 && bracketDepth == 0;
    }

    private int skipString(int start, char quote) {
        int i = start + 1;
        while (i < length) {
            char c = code.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) return i + 1;
            i++;
        }
        return length;
    }

    private int skipTemplate(int start) {
        int i = start + 1;
        while (i < length) {
            char c = code.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') return i + 1;
            i++;
        }
        return length;
    }

    private int skipLineComment(int start) {
        int i = start;
        while (i < length && code.charAt(i) != '\n' && code.charAt(i) != '\r') i++;
        return i;
    }

    private int skipBlockComment(int start) {
        int i = start;
        while (i + 1 < length) {
            if (code.charAt(i) == '*' && code.charAt(i + 1) == '/') return i + 2;
            i++;
        }
        return length;
    }

    private int skipRegex(int start) {
        int i = start;
        boolean inClass = false;
        while (i < length) {
            char c = code.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if (c == '/' && !inClass) {
                i++;
                while (i < length && Character.isLetter(code.charAt(i))) i++;
                return i;
            }
            i++;
        }
        return length;
    }

    private boolean looksLikeRegexStart(int slash) {
        int previous = previousNonWhitespace(slash - 1);
        if (previous < 0) return true;
        char c = code.charAt(previous);
        return "({[=,:;!&|?+-*~^<>".indexOf(c) >= 0;
    }

    private int previousNonWhitespace(int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(code.charAt(i))) i--;
        return i;
    }

    private int nextNonWhitespace(int index) {
        int i = index;
        while (i < length && Character.isWhitespace(code.charAt(i))) i++;
        return i;
    }

    private char peek(int index) {
        return index >= 0 && index < length ? code.charAt(index) : '\0';
    }

    private int readIdentifierEnd(int start) {
        int i = start;
        while (i < length && isIdentifierPart(code.charAt(i))) i++;
        return i;
    }

    private boolean wordBoundary(int index) {
        return index >= length || !isIdentifierPart(code.charAt(index));
    }

    private List<NekoEsmBinding> parseNamedBindings(String raw) {
        List<NekoEsmBinding> bindings = new ArrayList<>();
        for (String part : splitTopLevel(raw, ',')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] pieces = trimmed.split("\\s+as\\s+", 2);
            if (pieces.length == 2) {
                bindings.add(new NekoEsmBinding(pieces[0].trim(), pieces[1].trim()));
            } else {
                String[] words = trimmed.split("\\s+");
                if (words.length == 1) bindings.add(new NekoEsmBinding(words[0].trim(), words[0].trim()));
                else if (words.length == 2) bindings.add(new NekoEsmBinding(words[0].trim(), words[1].trim()));
                else throw error("Unsupported named binding: " + trimmed);
            }
        }
        return bindings;
    }

    private List<String> splitTopLevel(String raw, char delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            else if (c == delimiter && depth == 0) {
                parts.add(raw.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(raw.substring(start));
        return parts;
    }

    private int topLevelComma(String raw) {
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    private int matchingBrace(String raw, int open) {
        int depth = 0;
        for (int i = open; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isIdentifier(String value) {
        if (value == null || value.isBlank()) return false;
        if (!isIdentifierStart(value.charAt(0))) return false;
        for (int i = 1; i < value.length(); i++) {
            if (!isIdentifierPart(value.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || Character.isDigit(c);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Failed to parse NekoJS ESM module" + (file == null ? "" : " " + file) + ": " + message);
    }

    private static String oneLine(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private record ImportBindings(String defaultName, String namespaceName, List<NekoEsmBinding> namedBindings) {}
}
