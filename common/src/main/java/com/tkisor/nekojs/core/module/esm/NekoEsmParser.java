package com.tkisor.nekojs.core.module.esm;

import com.tkisor.nekojs.core.module.jsast.NekoJsBinding;
import com.tkisor.nekojs.core.module.jsast.NekoJsBlockBody;
import com.tkisor.nekojs.core.module.jsast.NekoJsClassBody;
import com.tkisor.nekojs.core.module.jsast.NekoJsClassElement;
import com.tkisor.nekojs.core.module.jsast.NekoJsClassElementKind;
import com.tkisor.nekojs.core.module.jsast.NekoJsExportDeclaration;
import com.tkisor.nekojs.core.module.jsast.NekoJsExpression;
import com.tkisor.nekojs.core.module.jsast.NekoJsExpressionKind;
import com.tkisor.nekojs.core.module.jsast.NekoJsFunctionKind;
import com.tkisor.nekojs.core.module.jsast.NekoJsFunctionLike;
import com.tkisor.nekojs.core.module.jsast.NekoJsImportDeclaration;
import com.tkisor.nekojs.core.module.jsast.NekoJsProgram;
import com.tkisor.nekojs.core.module.jsast.NekoJsRuntimeExpressionStatement;
import com.tkisor.nekojs.core.module.jsast.NekoJsScope;
import com.tkisor.nekojs.core.module.jsast.NekoJsStatement;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NekoEsmParser {
    private final Path file;
    private final String code;
    private final int length;
    private final List<NekoEsmStatement> statements = new ArrayList<>();
    private final List<NekoEsmRuntimeExpression> runtimeExpressions = new ArrayList<>();
    private final List<NekoEsmLocalBinding> localBindings = new ArrayList<>();
    private final List<NekoEsmScope> scopes = new ArrayList<>();
    private final Deque<ScopeFrame> scopeStack = new ArrayDeque<>();
    private final Set<Integer> functionBodyStarts = new HashSet<>();
    private final List<PendingFunctionParameters> pendingFunctionParameters = new ArrayList<>();
    private final List<PendingBlockBinding> pendingBlockBindings = new ArrayList<>();
    private boolean module;
    private boolean topLevelAwait;
    private int braceDepth;
    private int parenDepth;
    private int bracketDepth;
    private int nextScopeId = 1;

    public NekoEsmParser(Path file, String code) {
        this.file = file;
        this.code = code == null ? "" : code;
        this.length = this.code.length();
    }

    public NekoEsmModuleAst parse() {
        scopes.add(new NekoEsmScope(0, -1, NekoEsmScopeKind.MODULE, new NekoEsmSpan(0, length)));
        scopeStack.push(new ScopeFrame(0, -1, NekoEsmScopeKind.MODULE, 0, false));
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
                if ("function".equals(word)) {
                    markFunctionBodyStart(end);
                } else if ("catch".equals(word)) {
                    recordCatchBinding(end);
                }
                if (topLevel() && "import".equals(word)) {
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
                } else if (topLevel() && "export".equals(word)) {
                    int exportEnd = readModuleStatement(i);
                    if (blockModuleStatement(i)) {
                        replayScopeSyntax(i, exportEnd);
                    }
                    i = exportEnd;
                    continue;
                } else if (topLevel() && "await".equals(word)) {
                    topLevelAwait = true;
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
                if (!"export".equals(word)) {
                    recordLocalDeclaration(i, word);
                }
                i = end;
                continue;
            }
            switch (c) {
                case '{' -> {
                    maybeMarkFunctionLikeBody(i);
                    openBraceScope(i);
                    braceDepth++;
                }
                case '}' -> {
                    closeBraceScope(i);
                    braceDepth = Math.max(0, braceDepth - 1);
                }
                case '(' -> parenDepth++;
                case ')' -> parenDepth = Math.max(0, parenDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                default -> {
                }
            }
            i++;
        }
        while (scopeStack.size() > 1) {
            closeScope(length);
        }
        return new NekoEsmModuleAst(module, topLevelAwait, statements, runtimeExpressions, localBindings, scopes);
    }

    private void recordLocalDeclaration(int start, String word) {
        if (!"const".equals(word) && !"let".equals(word) && !"var".equals(word) && !"function".equals(word) && !"class".equals(word)) {
            return;
        }
        int declarationStart = nextNonWhitespace(start + word.length());
        if (declarationStart >= length) {
            return;
        }
        if ("const".equals(word) || "let".equals(word) || "var".equals(word)) {
            recordVariableDeclarationBindings(declarationStart, word);
            return;
        }
        if (isIdentifierStart(code.charAt(declarationStart))) {
            int nameEnd = readIdentifierEnd(declarationStart + 1);
            localBindings.add(new NekoEsmLocalBinding(code.substring(declarationStart, nameEnd), word, NekoEsmBindingSource.DECLARATION, new NekoEsmSpan(declarationStart, nameEnd), declarationScopeId(word)));
        }
    }

    private void recordVariableDeclarationBindings(int declarationStart, String kind) {
        int i = declarationStart;
        int scopeId = declarationScopeId(kind);
        while (i < length) {
            i = nextNonWhitespace(i);
            if (i >= length || code.charAt(i) == ';' || code.charAt(i) == '\n' || code.charAt(i) == '\r') {
                return;
            }
            char first = code.charAt(i);
            if (isIdentifierStart(first)) {
                int nameEnd = readIdentifierEnd(i + 1);
                localBindings.add(new NekoEsmLocalBinding(code.substring(i, nameEnd), kind, NekoEsmBindingSource.DECLARATION, new NekoEsmSpan(i, nameEnd), scopeId));
                i = skipVariableInitializer(nameEnd);
            } else if (first == '{' || first == '[') {
                int declarationEnd = findDeclarationPatternEnd(i);
                if (declarationEnd > i) {
                    recordPatternBindings(code.substring(i, declarationEnd), kind, new NekoEsmSpan(i, declarationEnd), scopeId);
                }
                i = skipVariableInitializer(declarationEnd);
            } else {
                return;
            }
            i = nextNonWhitespace(i);
            if (i < length && code.charAt(i) == ',') {
                i++;
                continue;
            }
            return;
        }
    }

    private int skipVariableInitializer(int start) {
        int i = nextNonWhitespace(start);
        if (i >= length || code.charAt(i) != '=') {
            return start;
        }
        return skipVariableExpression(i + 1);
    }

    private int skipVariableExpression(int start) {
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
            if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') {
                if (depth == 0) return i;
                depth--;
            } else if ((c == ',' || c == ';' || c == '\n' || c == '\r') && depth == 0) {
                return i;
            }
            i++;
        }
        return length;
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

    private List<NekoEsmLocalBinding> recordPatternBindings(String pattern, String kind, NekoEsmSpan span, int scopeId) {
        return recordPatternBindings(pattern, kind, NekoEsmBindingSource.DECLARATION, span, scopeId);
    }

    private List<NekoEsmLocalBinding> recordPatternBindings(String pattern, String kind, NekoEsmBindingSource source, NekoEsmSpan span, int scopeId) {
        PatternBindingReader reader = new PatternBindingReader(pattern, kind, source, span.start(), scopeId);
        List<NekoEsmLocalBinding> bindings = reader.read();
        localBindings.addAll(bindings);
        return bindings;
    }

    private final class PatternBindingReader {
        private final String pattern;
        private final String kind;
        private final NekoEsmBindingSource source;
        private final int absoluteOffset;
        private final int scopeId;
        private final List<NekoEsmLocalBinding> bindings = new ArrayList<>();

        private PatternBindingReader(String pattern, String kind, NekoEsmBindingSource source, int absoluteOffset, int scopeId) {
            this.pattern = pattern == null ? "" : pattern;
            this.kind = kind;
            this.source = source;
            this.absoluteOffset = absoluteOffset;
            this.scopeId = scopeId;
        }

        private List<NekoEsmLocalBinding> read() {
            int start = nextPatternNonWhitespace(pattern, 0);
            if (start < pattern.length()) {
                readBindingPattern(start, pattern.length());
            }
            return List.copyOf(bindings);
        }

        private int readBindingPattern(int start, int end) {
            int i = nextPatternNonWhitespace(pattern, start);
            if (i >= end) return end;
            char c = pattern.charAt(i);
            if (c == '{') return readObjectPattern(i, end);
            if (c == '[') return readArrayPattern(i, end);
            if (isIdentifierStart(c)) {
                int nameEnd = readPatternIdentifierEnd(pattern, i + 1);
                addBinding(pattern.substring(i, nameEnd), i, nameEnd);
                return nameEnd;
            }
            return i + 1;
        }

        private int readObjectPattern(int start, int end) {
            int i = start + 1;
            while (i < end) {
                i = nextPatternNonWhitespace(pattern, i);
                if (i >= end || pattern.charAt(i) == '}') return i + 1;
                if (pattern.startsWith("...", i)) {
                    i = readBindingPattern(i + 3, end);
                    continue;
                }
                if (isPropertyNameStart(i)) {
                    int nameStart = i;
                    i = readPropertyName(i, end);
                    int afterName = nextPatternNonWhitespace(pattern, i);
                    if (afterName < end && pattern.charAt(afterName) == ':') {
                        i = readBindingPattern(afterName + 1, end);
                    } else if (isIdentifierStart(pattern.charAt(nameStart))) {
                        addBinding(pattern.substring(nameStart, i), nameStart, i);
                        i = skipDefaultValue(i, end);
                    }
                } else if (pattern.charAt(i) == '[') {
                    int computedEnd = skipComputedPropertyName(i, end);
                    int afterComputed = nextPatternNonWhitespace(pattern, computedEnd);
                    if (afterComputed < end && pattern.charAt(afterComputed) == ':') {
                        i = readBindingPattern(afterComputed + 1, end);
                    } else {
                        i = computedEnd;
                    }
                } else if (pattern.charAt(i) == '{') {
                    i = readBindingPattern(i, end);
                } else {
                    i++;
                }
                i = skipToNextElement(i, end);
            }
            return end;
        }

        private int skipComputedPropertyName(int start, int end) {
            int i = start + 1;
            int depth = 0;
            while (i < end) {
                char c = pattern.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipPatternString(pattern, i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipPatternTemplate(pattern, i);
                    continue;
                }
                if (c == '[' || c == '{' || c == '(') depth++;
                else if (c == ']' && depth == 0) return i + 1;
                else if (c == ']' || c == '}' || c == ')') depth = Math.max(0, depth - 1);
                i++;
            }
            return end;
        }

        private int readArrayPattern(int start, int end) {
            int i = start + 1;
            while (i < end) {
                i = nextPatternNonWhitespace(pattern, i);
                if (i >= end || pattern.charAt(i) == ']') return i + 1;
                if (pattern.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (pattern.startsWith("...", i)) {
                    i = readBindingPattern(i + 3, end);
                } else {
                    i = readBindingPattern(i, end);
                    i = skipDefaultValue(i, end);
                }
                i = skipToNextElement(i, end);
            }
            return end;
        }

        private boolean isPropertyNameStart(int index) {
            char c = pattern.charAt(index);
            return isIdentifierStart(c) || c == '\'' || c == '"';
        }

        private int readPropertyName(int start, int end) {
            char c = pattern.charAt(start);
            if (c == '\'' || c == '"') return skipPatternString(pattern, start, c);
            return readPatternIdentifierEnd(pattern, start + 1);
        }

        private int skipDefaultValue(int start, int end) {
            int i = nextPatternNonWhitespace(pattern, start);
            if (i >= end || pattern.charAt(i) != '=') return start;
            return skipExpression(i + 1, end);
        }

        private int skipExpression(int start, int end) {
            int i = start;
            int depth = 0;
            while (i < end) {
                char c = pattern.charAt(i);
                if (c == '\'' || c == '"') {
                    i = skipPatternString(pattern, i, c);
                    continue;
                }
                if (c == '`') {
                    i = skipPatternTemplate(pattern, i);
                    continue;
                }
                if (c == '{' || c == '[' || c == '(') depth++;
                else if (c == '}' || c == ']' || c == ')') {
                    if (depth == 0) return i;
                    depth--;
                } else if (c == ',' && depth == 0) {
                    return i;
                }
                i++;
            }
            return end;
        }

        private int skipToNextElement(int start, int end) {
            int i = skipExpression(start, end);
            if (i < end && pattern.charAt(i) == ',') return i + 1;
            return i;
        }

        private void addBinding(String name, int start, int end) {
            if (!isPatternKeyword(name)) {
                bindings.add(new NekoEsmLocalBinding(name, kind, source, new NekoEsmSpan(absoluteOffset + start, absoluteOffset + end), scopeId));
            }
        }
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

    private int declarationScopeId(String kind) {
        if (kind != null && kind.contains("function")) {
            return currentScopeId();
        }
        if ("var".equals(kind)) {
            return nearestVarScopeId();
        }
        if ("class".equals(kind)) {
            ScopeFrame frame = scopeStack.peek();
            if (frame != null && code.charAt(frame.start) == '{' && previousKeyword(frame.start, "class")) {
                return frame.parentId;
            }
        }
        return currentScopeId();
    }

    private boolean previousKeyword(int position, String keyword) {
        int i = previousNonWhitespace(position - 1);
        if (i < 0 || code.charAt(i) != ')') {
            int end = i + 1;
            while (i >= 0 && isIdentifierPart(code.charAt(i))) i--;
            return keyword.equals(code.substring(i + 1, end));
        }
        int open = matchingOpenParen(i);
        if (open < 0) return false;
        i = previousNonWhitespace(open - 1);
        int end = i + 1;
        while (i >= 0 && isIdentifierPart(code.charAt(i))) i--;
        return keyword.equals(code.substring(i + 1, end));
    }

    private int matchingOpenParen(int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private int currentScopeId() {
        ScopeFrame frame = scopeStack.peek();
        return frame == null ? 0 : frame.id;
    }

    private int nearestVarScopeId() {
        for (ScopeFrame frame : scopeStack) {
            if (frame.kind == NekoEsmScopeKind.FUNCTION || frame.kind == NekoEsmScopeKind.MODULE || staticBlockScope(frame)) {
                return frame.id;
            }
        }
        return 0;
    }

    private boolean staticBlockScope(ScopeFrame frame) {
        return frame != null && frame.kind == NekoEsmScopeKind.BLOCK && code.charAt(frame.start) == '{' && previousKeyword(frame.start, "static");
    }

    private void maybeMarkFunctionLikeBody(int bodyStart) {
        if (functionBodyStarts.contains(bodyStart)) {
            return;
        }
        int previous = previousNonWhitespace(bodyStart - 1);
        if (previous < 0) return;
        if (code.charAt(previous) == '>' && previousNonWhitespace(previous - 1) >= 0 && code.charAt(previousNonWhitespace(previous - 1)) == '=') {
            functionBodyStarts.add(bodyStart);
            markArrowFunctionParameters(previousNonWhitespace(previous - 1));
            return;
        }
        if (code.charAt(previous) == ')') {
            int open = matchingOpenParen(previous);
            if (open >= 0 && methodBodyAfterParameters(open)) {
                functionBodyStarts.add(bodyStart);
                pendingFunctionParameters.add(new PendingFunctionParameters(bodyStart, code.substring(open + 1, previous), open + 1));
            }
        }
    }

    private void markArrowFunctionParameters(int arrowEquals) {
        int previous = previousNonWhitespace(arrowEquals - 1);
        if (previous < 0) return;
        if (code.charAt(previous) == ')') {
            int open = matchingOpenParen(previous);
            if (open >= 0) {
                pendingFunctionParameters.add(new PendingFunctionParameters(nextNonWhitespace(arrowEquals + 2), code.substring(open + 1, previous), open + 1));
            }
            return;
        }
        if (isIdentifierPart(code.charAt(previous))) {
            int end = previous + 1;
            while (previous >= 0 && isIdentifierPart(code.charAt(previous))) previous--;
            int start = previous + 1;
            pendingFunctionParameters.add(new PendingFunctionParameters(nextNonWhitespace(arrowEquals + 2), code.substring(start, end), start));
        }
    }

    private boolean methodBodyAfterParameters(int parameterOpen) {
        int memberStart = methodMemberStart(parameterOpen);
        if (memberStart < 0 || !methodContainerAllowsMembers()) return false;
        int previous = previousNonWhitespace(memberStart - 1);
        return previous >= 0 && (code.charAt(previous) == '{' || code.charAt(previous) == ',' || code.charAt(previous) == ';' || code.charAt(previous) == '}');
    }

    private int methodMemberStart(int parameterOpen) {
        int previous = previousNonWhitespace(parameterOpen - 1);
        if (previous < 0) return -1;
        int start;
        if (isIdentifierPart(code.charAt(previous))) {
            while (previous >= 0 && isIdentifierPart(code.charAt(previous))) previous--;
            start = previous + 1;
            int hash = previousNonWhitespace(start - 1);
            if (hash >= 0 && code.charAt(hash) == '#') start = hash;
        } else if (code.charAt(previous) == ']') {
            start = matchingOpenBracket(previous);
            if (start < 0) return -1;
        } else {
            return -1;
        }
        return includeMethodModifiers(start);
    }

    private int includeMethodModifiers(int start) {
        int current = start;
        while (true) {
            int previous = previousNonWhitespace(current - 1);
            if (previous >= 0 && code.charAt(previous) == '*') {
                current = previous;
                continue;
            }
            if (previous < 0 || !isIdentifierPart(code.charAt(previous))) {
                return current;
            }
            int end = previous + 1;
            while (previous >= 0 && isIdentifierPart(code.charAt(previous))) previous--;
            String word = code.substring(previous + 1, end);
            if ("static".equals(word) || "async".equals(word) || "get".equals(word) || "set".equals(word) || "accessor".equals(word)) {
                current = previous + 1;
                continue;
            }
            return current;
        }
    }

    private boolean methodContainerAllowsMembers() {
        ScopeFrame frame = scopeStack.peek();
        if (frame == null || frame.start < 0 || frame.start >= length || code.charAt(frame.start) != '{') return false;
        if (frame.classBody) return true;
        int previous = previousNonWhitespace(frame.start - 1);
        return previous >= 0 && "=(:,[,{".indexOf(code.charAt(previous)) >= 0;
    }

    private boolean classBodyScope(int start) {
        int i = previousNonWhitespace(start - 1);
        while (i >= 0) {
            char c = code.charAt(i);
            if (c == '\'' || c == '"' || c == '`' || c == ';' || c == '}' || c == '{' || c == '=' || c == ',') return false;
            if (isIdentifierPart(c)) {
                int end = i + 1;
                while (i >= 0 && isIdentifierPart(code.charAt(i))) i--;
                if ("class".equals(code.substring(i + 1, end))) return true;
                continue;
            }
            i--;
        }
        return false;
    }

    private int matchingOpenBracket(int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char c = code.charAt(i);
            if (c == ']') depth++;
            else if (c == '[') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private void markFunctionBodyStart(int from) {
        int i = from;
        int parameterStart = -1;
        int parameterEnd = -1;
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
            }
            if (c == '(') {
                int close = matchingCloseParen(i);
                if (close < 0) return;
                parameterStart = i + 1;
                parameterEnd = close;
                i = close + 1;
                continue;
            }
            if (c == '{') {
                functionBodyStarts.add(i);
                if (parameterStart >= 0) {
                    pendingFunctionParameters.add(new PendingFunctionParameters(i, code.substring(parameterStart, parameterEnd), parameterStart));
                }
                return;
            }
            if (c == ';' || c == '\n' || c == '\r') {
                return;
            }
            i++;
        }
    }

    private int matchingCloseParen(int open) {
        int depth = 0;
        int i = open;
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
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    private void openBraceScope(int start) {
        boolean functionScope = functionBodyStarts.remove(start);
        NekoEsmScopeKind kind = functionScope ? NekoEsmScopeKind.FUNCTION : NekoEsmScopeKind.BLOCK;
        int id = nextScopeId++;
        scopeStack.push(new ScopeFrame(id, currentScopeId(), kind, start, !functionScope && classBodyScope(start)));
        if (functionScope) {
            recordPendingFunctionParameters(start, id);
        }
        recordPendingBlockBindings(start, id);
    }

    private void recordPendingFunctionParameters(int bodyStart, int scopeId) {
        for (int i = pendingFunctionParameters.size() - 1; i >= 0; i--) {
            PendingFunctionParameters parameters = pendingFunctionParameters.get(i);
            if (parameters.bodyStart() == bodyStart) {
                recordParameterBindings(parameters.raw(), parameters.absoluteOffset(), scopeId);
                pendingFunctionParameters.remove(i);
                return;
            }
        }
    }

    private void recordPendingBlockBindings(int bodyStart, int scopeId) {
        for (int i = pendingBlockBindings.size() - 1; i >= 0; i--) {
            PendingBlockBinding binding = pendingBlockBindings.get(i);
            if (binding.bodyStart() == bodyStart) {
                recordPatternBindings(binding.raw(), binding.kind(), NekoEsmBindingSource.DECLARATION, new NekoEsmSpan(binding.absoluteOffset(), binding.absoluteOffset() + binding.raw().length()), scopeId);
                pendingBlockBindings.remove(i);
                return;
            }
        }
    }

    private void recordParameterBindings(String raw, int absoluteOffset, int scopeId) {
        for (String parameter : splitPatternList(raw)) {
            String trimmed = parameter.trim();
            if (trimmed.isEmpty()) continue;
            int relativeStart = parameter.indexOf(trimmed);
            if (trimmed.startsWith("...")) {
                relativeStart += 3;
                trimmed = trimmed.substring(3).trim();
            }
            int defaultIndex = topLevelPatternEquals(trimmed);
            if (defaultIndex >= 0) {
                trimmed = trimmed.substring(0, defaultIndex).trim();
            }
            if (trimmed.isEmpty()) continue;
            int parameterAbsoluteStart = absoluteOffset + Math.max(0, relativeStart);
            recordPatternBindings(trimmed, "param", NekoEsmBindingSource.DECLARATION, new NekoEsmSpan(parameterAbsoluteStart, parameterAbsoluteStart + trimmed.length()), scopeId);
        }
    }

    private void recordCatchBinding(int from) {
        int open = nextNonWhitespace(from);
        if (open >= length || code.charAt(open) != '(') return;
        int close = matchingCloseParen(open);
        if (close < 0) return;
        String binding = code.substring(open + 1, close).trim();
        if (binding.isEmpty()) return;
        int relativeStart = code.substring(open + 1, close).indexOf(binding);
        int absoluteStart = open + 1 + Math.max(0, relativeStart);
        int bodyStart = nextNonWhitespace(close + 1);
        if (bodyStart < length && code.charAt(bodyStart) == '{') {
            pendingBlockBindings.add(new PendingBlockBinding(bodyStart, binding, "catch", absoluteStart));
        }
    }

    private List<String> splitPatternList(String raw) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipPatternString(raw, i, c) - 1;
            } else if (c == '`') {
                i = skipPatternTemplate(raw, i) - 1;
            } else if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(raw.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(raw.substring(start));
        return parts;
    }

    private int topLevelPatternEquals(String raw) {
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipPatternString(raw, i, c) - 1;
            } else if (c == '`') {
                i = skipPatternTemplate(raw, i) - 1;
            } else if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == '=' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private void closeBraceScope(int end) {
        if (scopeStack.size() <= 1) {
            return;
        }
        closeScope(end + 1);
    }

    private void closeScope(int end) {
        ScopeFrame frame = scopeStack.pop();
        scopes.add(new NekoEsmScope(frame.id, frame.parentId, frame.kind, new NekoEsmSpan(frame.start, end), frame.classBody));
    }

    private NekoJsProgram toJsProgram() {
        List<NekoJsStatement> jsStatements = new ArrayList<>();
        for (NekoEsmStatement statement : statements) {
            if (statement instanceof NekoEsmImportDecl importDecl) {
                jsStatements.add(new NekoJsImportDeclaration(importDecl.span(), importDecl.raw(), importDecl.specifier(), importDecl.specifierSpan(), importDecl.defaultName(), importDecl.namespaceName(), importDecl.namedBindings(), importDecl.sideEffectOnly()));
            } else if (statement instanceof NekoEsmExportDecl exportDecl) {
                jsStatements.add(new NekoJsExportDeclaration(exportDecl.span(), exportDecl.raw(), exportDecl.kind(), exportDecl.specifier(), exportDecl.specifierSpan(), exportDecl.declarationKind(), exportDecl.localName(), exportDecl.namespaceName(), exportDecl.expression(), exportDecl.bindings()));
            }
        }

        List<NekoJsExpression> jsExpressions = new ArrayList<>();
        for (NekoEsmRuntimeExpression expression : runtimeExpressions) {
            NekoJsExpression jsExpression = runtimeExpressionNode(expression);
            jsExpressions.add(jsExpression);
            jsStatements.add(new NekoJsRuntimeExpressionStatement(jsExpression.span(), jsExpression.raw(), expression.kind(), expression.specifier(), expression.specifierLiteralSpan()));
        }
        for (NekoEsmStatement statement : statements) {
            if (statement instanceof NekoEsmExportDecl exportDecl && exportDecl.expression() != null) {
                NekoJsExpression expression = defaultExportExpressionNode(exportDecl);
                if (expression != null) {
                    jsExpressions.add(expression);
                }
            }
        }

        List<NekoJsBinding> jsBindings = new ArrayList<>();
        for (NekoEsmLocalBinding binding : localBindings) {
            jsBindings.add(new NekoJsBinding(binding.name(), binding.kind(), binding.source(), binding.span(), binding.scopeId()));
        }

        List<NekoJsScope> jsScopes = new ArrayList<>();
        for (NekoEsmScope scope : scopes) {
            jsScopes.add(new NekoJsScope(scope.id(), scope.parentId(), scope.kind(), scope.span()));
        }

        List<NekoJsClassBody> jsClasses = new ArrayList<>();
        for (NekoEsmScope scope : scopes) {
            if (scope.classBody()) {
                jsClasses.add(classBody(scope, jsBindings, jsExpressions));
            }
        }

        List<NekoJsBlockBody> jsBlocks = new ArrayList<>();
        List<NekoJsFunctionLike> jsFunctions = new ArrayList<>();
        for (NekoEsmScope scope : scopes) {
            NekoJsBlockBody block = blockBody(scope, jsBindings, jsExpressions);
            jsBlocks.add(block);
            if (scope.kind() == NekoEsmScopeKind.FUNCTION) {
                jsFunctions.add(functionLike(scope, block));
            }
        }
        return new NekoJsProgram(new NekoEsmSpan(0, length), module, topLevelAwait, jsStatements, jsBindings, jsScopes, jsExpressions, jsFunctions, jsClasses, jsBlocks);
    }

    private NekoJsExpression runtimeExpressionNode(NekoEsmRuntimeExpression expression) {
        NekoEsmSpan span = expression.span();
        if (expression.kind() == NekoEsmRuntimeExpressionKind.DYNAMIC_IMPORT) {
            int open = nextNonWhitespace(span.end());
            if (open < length && code.charAt(open) == '(') {
                int close = matchingCloseParen(open);
                if (close >= open) {
                    span = new NekoEsmSpan(span.start(), close + 1);
                }
            }
        }
        List<NekoJsExpression> children = new ArrayList<>();
        if (expression.specifierLiteralSpan() != null) {
            children.add(expressionNode(expression.specifierLiteralSpan()));
        }
        return new NekoJsExpression(span, source(span), expressionKind(expression.kind()), children);
    }

    private NekoJsExpression defaultExportExpressionNode(NekoEsmExportDecl exportDecl) {
        String expression = exportDecl.expression();
        String raw = source(exportDecl.span());
        int relativeStart = raw.indexOf(expression);
        if (relativeStart < 0) {
            return null;
        }
        return expressionNode(new NekoEsmSpan(exportDecl.span().start() + relativeStart, exportDecl.span().start() + relativeStart + expression.length()));
    }

    private NekoJsExpression expressionNode(NekoEsmSpan span) {
        NekoEsmSpan trimmed = trimSpan(span.start(), span.end());
        String raw = source(trimmed);
        List<NekoJsExpression> children = new ArrayList<>();
        if (startsWithKeyword(trimmed.start(), "await")) {
            int childStart = nextNonWhitespace(trimmed.start() + "await".length());
            if (childStart < trimmed.end()) {
                children.add(expressionNode(new NekoEsmSpan(childStart, trimmed.end())));
            }
        }
        return new NekoJsExpression(trimmed, raw, expressionKind(raw), children);
    }

    private NekoJsExpressionKind expressionKind(NekoEsmRuntimeExpressionKind kind) {
        if (kind == NekoEsmRuntimeExpressionKind.DYNAMIC_IMPORT) return NekoJsExpressionKind.DYNAMIC_IMPORT;
        return NekoJsExpressionKind.IMPORT_META;
    }

    private NekoJsExpressionKind expressionKind(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return NekoJsExpressionKind.RAW;
        if (trimmed.startsWith("import(")) return NekoJsExpressionKind.DYNAMIC_IMPORT;
        if (trimmed.startsWith("import.meta")) return NekoJsExpressionKind.IMPORT_META;
        if (trimmed.startsWith("await") && (trimmed.length() == "await".length() || !isIdentifierPart(trimmed.charAt("await".length())))) return NekoJsExpressionKind.AWAIT;
        if (trimmed.startsWith("async function") || trimmed.startsWith("function")) return NekoJsExpressionKind.FUNCTION;
        if (trimmed.startsWith("class")) return NekoJsExpressionKind.CLASS;
        if (trimmed.contains("=>")) return NekoJsExpressionKind.ARROW_FUNCTION;
        if (trimmed.startsWith("[")) return NekoJsExpressionKind.ARRAY;
        if (trimmed.startsWith("{")) return NekoJsExpressionKind.OBJECT;
        if (literalExpression(trimmed)) return NekoJsExpressionKind.LITERAL;
        if (identifierExpression(trimmed)) return NekoJsExpressionKind.IDENTIFIER;
        if (callExpression(trimmed)) return NekoJsExpressionKind.CALL;
        if (trimmed.indexOf('.') >= 0) return NekoJsExpressionKind.MEMBER;
        return NekoJsExpressionKind.RAW;
    }

    private boolean literalExpression(String raw) {
        if (raw.length() >= 2 && ((raw.charAt(0) == '\'' && raw.charAt(raw.length() - 1) == '\'') || (raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') || (raw.charAt(0) == '`' && raw.charAt(raw.length() - 1) == '`'))) return true;
        if ("true".equals(raw) || "false".equals(raw) || "null".equals(raw) || "undefined".equals(raw)) return true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!Character.isDigit(c) && c != '.' && c != '_' && c != 'n') return false;
        }
        return true;
    }

    private boolean identifierExpression(String raw) {
        if (raw.isEmpty() || !isIdentifierStart(raw.charAt(0))) return false;
        for (int i = 1; i < raw.length(); i++) {
            if (!isIdentifierPart(raw.charAt(i))) return false;
        }
        return true;
    }

    private boolean callExpression(String raw) {
        int open = raw.indexOf('(');
        return open > 0 && raw.endsWith(")");
    }

    private NekoJsBlockBody blockBody(NekoEsmScope scope, List<NekoJsBinding> bindings, List<NekoJsExpression> expressions) {
        return new NekoJsBlockBody(scope.span(), scope.id(), List.of(), bindingsForScope(bindings, scope.id()), expressionsInside(expressions, scope.span()));
    }

    private NekoJsFunctionLike functionLike(NekoEsmScope scope, NekoJsBlockBody block) {
        FunctionDescriptor descriptor = describeFunction(scope);
        return new NekoJsFunctionLike(descriptor.span(), descriptor.raw(), descriptor.name(), descriptor.kind(), parameterBindings(block.bindings()), block);
    }

    private FunctionDescriptor describeFunction(NekoEsmScope scope) {
        int bodyStart = scope.span().start();
        int bodyEnd = scope.span().end();
        int start = bodyStart;
        String name = null;
        NekoJsFunctionKind kind = NekoJsFunctionKind.FUNCTION;
        int previous = previousNonWhitespace(bodyStart - 1);
        if (previous >= 0 && code.charAt(previous) == '>') {
            int arrowEquals = previousNonWhitespace(previous - 1);
            start = arrowParameterStart(arrowEquals);
            kind = NekoJsFunctionKind.ARROW_FUNCTION;
        } else if (previous >= 0 && code.charAt(previous) == ')') {
            int open = matchingOpenParen(previous);
            int memberStart = open >= 0 ? methodMemberStart(open) : -1;
            if (memberStart >= 0) {
                int functionKeyword = functionKeywordStart(memberStart, open);
                if (functionKeyword >= 0) {
                    int asyncStart = asyncKeywordStartBefore(functionKeyword);
                    start = asyncStart >= 0 ? asyncStart : functionKeyword;
                    kind = jsFunctionKind(asyncStart >= 0, generatorFunction(functionKeyword, open));
                    name = functionName(functionKeyword, open);
                } else {
                    ClassMemberHeader header = readClassMemberHeader(memberStart, open);
                    if (header != null) {
                        start = header.start();
                        name = header.name();
                        kind = header.functionKind();
                    }
                }
            }
        }
        NekoEsmSpan span = new NekoEsmSpan(Math.max(0, start), bodyEnd);
        return new FunctionDescriptor(span, source(span), name, kind);
    }

    private int arrowParameterStart(int arrowEquals) {
        int previous = previousNonWhitespace(arrowEquals - 1);
        if (previous >= 0 && code.charAt(previous) == ')') {
            int open = matchingOpenParen(previous);
            return open >= 0 ? open : arrowEquals;
        }
        if (previous >= 0 && isIdentifierPart(code.charAt(previous))) {
            while (previous >= 0 && isIdentifierPart(code.charAt(previous))) previous--;
            return previous + 1;
        }
        return Math.max(0, arrowEquals);
    }

    private int functionKeywordStart(int memberStart, int parameterOpen) {
        if (startsWithKeyword(memberStart, "function")) {
            return memberStart;
        }
        int search = parameterOpen;
        while (search > 0) {
            int found = code.lastIndexOf("function", search - 1);
            if (found < 0) return -1;
            if (found <= memberStart && startsWithKeyword(found, "function")) {
                return found;
            }
            search = found;
        }
        return -1;
    }

    private int asyncKeywordStartBefore(int functionKeyword) {
        int previous = previousNonWhitespace(functionKeyword - 1);
        if (previous < 0 || !isIdentifierPart(code.charAt(previous))) return -1;
        int end = previous + 1;
        while (previous >= 0 && isIdentifierPart(code.charAt(previous))) previous--;
        int start = previous + 1;
        return "async".equals(code.substring(start, end)) ? start : -1;
    }

    private boolean generatorFunction(int functionKeyword, int parameterOpen) {
        int i = nextNonWhitespace(functionKeyword + "function".length());
        return i < parameterOpen && code.charAt(i) == '*';
    }

    private String functionName(int functionKeyword, int parameterOpen) {
        int i = nextNonWhitespace(functionKeyword + "function".length());
        if (i < parameterOpen && code.charAt(i) == '*') {
            i = nextNonWhitespace(i + 1);
        }
        if (i < parameterOpen && isIdentifierStart(code.charAt(i))) {
            int end = readIdentifierEnd(i + 1);
            if (end <= parameterOpen) {
                return code.substring(i, end);
            }
        }
        return null;
    }

    private NekoJsFunctionKind jsFunctionKind(boolean async, boolean generator) {
        if (async && generator) return NekoJsFunctionKind.ASYNC_GENERATOR_FUNCTION;
        if (async) return NekoJsFunctionKind.ASYNC_FUNCTION;
        if (generator) return NekoJsFunctionKind.GENERATOR_FUNCTION;
        return NekoJsFunctionKind.FUNCTION;
    }

    private NekoJsClassBody classBody(NekoEsmScope scope, List<NekoJsBinding> bindings, List<NekoJsExpression> expressions) {
        return new NekoJsClassBody(scope.span(), scope.id(), className(scope), classElements(scope, bindings, expressions));
    }

    private String className(NekoEsmScope scope) {
        int classKeyword = lastKeywordBefore("class", scope.span().start());
        if (classKeyword < 0) return null;
        int nameStart = nextNonWhitespace(classKeyword + "class".length());
        if (nameStart < scope.span().start() && isIdentifierStart(code.charAt(nameStart))) {
            int nameEnd = readIdentifierEnd(nameStart + 1);
            String name = code.substring(nameStart, nameEnd);
            if (!"extends".equals(name)) {
                return name;
            }
        }
        return null;
    }

    private List<NekoJsClassElement> classElements(NekoEsmScope classScope, List<NekoJsBinding> bindings, List<NekoJsExpression> expressions) {
        List<NekoJsClassElement> elements = new ArrayList<>();
        int limit = Math.max(classScope.span().start(), classScope.span().end() - 1);
        int i = classScope.span().start() + 1;
        while (i < limit) {
            i = skipClassTrivia(i, limit);
            if (i >= limit) break;
            if (code.charAt(i) == ';') {
                i++;
                continue;
            }
            int staticBlockStart = staticBlockStart(i, limit);
            if (staticBlockStart >= 0) {
                int end = matchingCloseBrace(staticBlockStart);
                if (end < 0) break;
                NekoEsmSpan span = new NekoEsmSpan(i, end + 1);
                NekoEsmScope blockScope = scopeAt(staticBlockStart, NekoEsmScopeKind.BLOCK);
                NekoJsBlockBody block = blockScope == null ? new NekoJsBlockBody(new NekoEsmSpan(staticBlockStart, end + 1), -1, List.of(), List.of(), expressionsInside(expressions, new NekoEsmSpan(staticBlockStart, end + 1))) : blockBody(blockScope, bindings, expressions);
                NekoJsFunctionLike function = new NekoJsFunctionLike(span, source(span), null, NekoJsFunctionKind.CLASS_STATIC_BLOCK, List.of(), block);
                elements.add(new NekoJsClassElement(span, source(span), null, NekoJsClassElementKind.STATIC_BLOCK, true, false, false, function, null));
                i = end + 1;
                continue;
            }
            ClassMemberHeader header = readClassMemberHeader(i, limit);
            if (header == null) {
                i = skipClassElementFallback(i, limit);
                continue;
            }
            int afterName = nextNonWhitespace(header.end());
            if (afterName < limit && code.charAt(afterName) == '(') {
                int closeParen = matchingCloseParen(afterName);
                if (closeParen < 0) break;
                int bodyStart = nextNonWhitespace(closeParen + 1);
                if (bodyStart >= limit || code.charAt(bodyStart) != '{') {
                    i = skipClassElementFallback(afterName, limit);
                    continue;
                }
                int bodyEnd = matchingCloseBrace(bodyStart);
                if (bodyEnd < 0) break;
                NekoEsmSpan span = new NekoEsmSpan(header.start(), bodyEnd + 1);
                NekoEsmScope functionScope = scopeAt(bodyStart, NekoEsmScopeKind.FUNCTION);
                NekoJsBlockBody block = functionScope == null ? new NekoJsBlockBody(new NekoEsmSpan(bodyStart, bodyEnd + 1), -1, List.of(), List.of(), expressionsInside(expressions, new NekoEsmSpan(bodyStart, bodyEnd + 1))) : blockBody(functionScope, bindings, expressions);
                NekoJsFunctionLike function = new NekoJsFunctionLike(span, source(span), header.name(), header.functionKind(), parameterBindings(block.bindings()), block);
                elements.add(new NekoJsClassElement(span, source(span), header.name(), NekoJsClassElementKind.METHOD, header.isStatic(), header.isPrivate(), header.computed(), function, null));
                i = bodyEnd + 1;
                continue;
            }
            int elementEnd = classFieldEnd(afterName, limit);
            NekoJsExpression initializer = null;
            if (afterName < elementEnd && code.charAt(afterName) == '=') {
                initializer = expressionNode(new NekoEsmSpan(afterName + 1, trimClassFieldSemicolon(elementEnd)));
                expressions.add(initializer);
            }
            NekoEsmSpan span = new NekoEsmSpan(header.start(), elementEnd);
            NekoJsClassElementKind kind = header.accessor() ? NekoJsClassElementKind.ACCESSOR : NekoJsClassElementKind.FIELD;
            elements.add(new NekoJsClassElement(span, source(span), header.name(), kind, header.isStatic(), header.isPrivate(), header.computed(), null, initializer));
            i = elementEnd;
        }
        return List.copyOf(elements);
    }

    private int staticBlockStart(int start, int limit) {
        if (!startsWithKeyword(start, "static")) return -1;
        int blockStart = nextNonWhitespace(start + "static".length());
        return blockStart < limit && code.charAt(blockStart) == '{' ? blockStart : -1;
    }

    private ClassMemberHeader readClassMemberHeader(int start, int limit) {
        int i = skipClassTrivia(start, limit);
        boolean isStatic = false;
        boolean isPrivate = false;
        boolean computed = false;
        boolean accessor = false;
        boolean generator = false;
        boolean async = false;
        NekoJsFunctionKind functionKind = NekoJsFunctionKind.METHOD;
        while (i < limit) {
            if (startsWithKeyword(i, "static") && nextNonWhitespace(i + "static".length()) < limit && code.charAt(nextNonWhitespace(i + "static".length())) != '(') {
                isStatic = true;
                i = skipClassTrivia(i + "static".length(), limit);
            } else if (startsWithKeyword(i, "async") && nextNonWhitespace(i + "async".length()) < limit && code.charAt(nextNonWhitespace(i + "async".length())) != '(') {
                async = true;
                i = skipClassTrivia(i + "async".length(), limit);
            } else if (startsWithKeyword(i, "get")) {
                functionKind = NekoJsFunctionKind.GETTER;
                i = skipClassTrivia(i + "get".length(), limit);
            } else if (startsWithKeyword(i, "set")) {
                functionKind = NekoJsFunctionKind.SETTER;
                i = skipClassTrivia(i + "set".length(), limit);
            } else if (startsWithKeyword(i, "accessor")) {
                accessor = true;
                i = skipClassTrivia(i + "accessor".length(), limit);
            } else if (code.charAt(i) == '*') {
                generator = true;
                i = skipClassTrivia(i + 1, limit);
            } else {
                break;
            }
        }
        int nameStart = i;
        String name;
        if (i < limit && code.charAt(i) == '#') {
            isPrivate = true;
            int nameEnd = readIdentifierEnd(i + 1);
            if (nameEnd <= i + 1) return null;
            name = code.substring(i, nameEnd);
            i = nameEnd;
        } else if (i < limit && code.charAt(i) == '[') {
            computed = true;
            int nameEnd = matchingCloseBracket(i);
            if (nameEnd < 0 || nameEnd >= limit) return null;
            name = source(new NekoEsmSpan(i, nameEnd + 1));
            i = nameEnd + 1;
        } else if (i < limit && (code.charAt(i) == '\'' || code.charAt(i) == '"')) {
            int nameEnd = skipString(i, code.charAt(i));
            name = code.substring(i + 1, Math.max(i + 1, nameEnd - 1));
            i = nameEnd;
        } else if (i < limit && isIdentifierStart(code.charAt(i))) {
            int nameEnd = readIdentifierEnd(i + 1);
            name = code.substring(i, nameEnd);
            i = nameEnd;
        } else {
            return null;
        }
        if (functionKind == NekoJsFunctionKind.METHOD) {
            functionKind = jsMethodKind(async, generator);
        }
        return new ClassMemberHeader(nameStart, i, name, isStatic, isPrivate, computed, accessor, functionKind);
    }

    private NekoJsFunctionKind jsMethodKind(boolean async, boolean generator) {
        if (async && generator) return NekoJsFunctionKind.ASYNC_GENERATOR_FUNCTION;
        if (async) return NekoJsFunctionKind.ASYNC_FUNCTION;
        if (generator) return NekoJsFunctionKind.GENERATOR_FUNCTION;
        return NekoJsFunctionKind.METHOD;
    }

    private int classFieldEnd(int start, int limit) {
        int i = start;
        int depth = 0;
        while (i < limit) {
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
            if (c == '{' || c == '[' || c == '(') depth++;
            else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
            else if (c == ';' && depth == 0) return i + 1;
            i++;
        }
        return limit;
    }

    private int trimClassFieldSemicolon(int end) {
        int previous = previousNonWhitespace(end - 1);
        return previous >= 0 && code.charAt(previous) == ';' ? previous : end;
    }

    private int skipClassElementFallback(int start, int limit) {
        int end = classFieldEnd(start, limit);
        return end > start ? end : start + 1;
    }

    private int skipClassTrivia(int start, int limit) {
        int i = start;
        while (i < limit) {
            if (Character.isWhitespace(code.charAt(i))) {
                i++;
            } else if (code.charAt(i) == '/' && peek(i + 1) == '/') {
                i = skipLineComment(i + 2);
            } else if (code.charAt(i) == '/' && peek(i + 1) == '*') {
                i = skipBlockComment(i + 2);
            } else {
                return i;
            }
        }
        return i;
    }

    private NekoEsmScope scopeAt(int start, NekoEsmScopeKind kind) {
        for (NekoEsmScope scope : scopes) {
            if (scope.kind() == kind && scope.span() != null && scope.span().start() == start) {
                return scope;
            }
        }
        return null;
    }

    private List<NekoJsBinding> bindingsForScope(List<NekoJsBinding> bindings, int scopeId) {
        List<NekoJsBinding> result = new ArrayList<>();
        for (NekoJsBinding binding : bindings) {
            if (binding.scopeId() == scopeId) {
                result.add(binding);
            }
        }
        return List.copyOf(result);
    }

    private List<NekoJsBinding> parameterBindings(List<NekoJsBinding> bindings) {
        List<NekoJsBinding> result = new ArrayList<>();
        for (NekoJsBinding binding : bindings) {
            if ("param".equals(binding.kind())) {
                result.add(binding);
            }
        }
        return List.copyOf(result);
    }

    private List<NekoJsExpression> expressionsInside(List<NekoJsExpression> expressions, NekoEsmSpan span) {
        List<NekoJsExpression> result = new ArrayList<>();
        for (NekoJsExpression expression : expressions) {
            if (span != null && expression.span() != null && expression.span().start() >= span.start() && expression.span().end() <= span.end()) {
                result.add(expression);
            }
        }
        return List.copyOf(result);
    }

    private int matchingCloseBrace(int open) {
        int depth = 0;
        int i = open;
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
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    private int matchingCloseBracket(int open) {
        int depth = 0;
        int i = open;
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
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        return -1;
    }

    private int lastKeywordBefore(String keyword, int before) {
        int search = Math.min(before, length);
        while (search > 0) {
            int found = code.lastIndexOf(keyword, search - 1);
            if (found < 0) return -1;
            if (startsWithKeyword(found, keyword)) return found;
            search = found;
        }
        return -1;
    }

    private boolean startsWithKeyword(int start, String keyword) {
        if (start < 0 || start + keyword.length() > length || !code.startsWith(keyword, start)) return false;
        boolean before = start == 0 || !isIdentifierPart(code.charAt(start - 1));
        boolean after = start + keyword.length() >= length || !isIdentifierPart(code.charAt(start + keyword.length()));
        return before && after;
    }

    private NekoEsmSpan trimSpan(int start, int end) {
        int trimmedStart = Math.max(0, start);
        int trimmedEnd = Math.min(length, end);
        while (trimmedStart < trimmedEnd && Character.isWhitespace(code.charAt(trimmedStart))) trimmedStart++;
        while (trimmedEnd > trimmedStart && Character.isWhitespace(code.charAt(trimmedEnd - 1))) trimmedEnd--;
        return new NekoEsmSpan(trimmedStart, trimmedEnd);
    }

    private String source(NekoEsmSpan span) {
        if (span == null) return null;
        return code.substring(Math.max(0, span.start()), Math.min(length, span.end()));
    }

    private int skipPatternTemplate(String text, int start) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') return i + 1;
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
        markTopLevelAwaitInStatement(start, end);
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

    private void markTopLevelAwaitInStatement(int start, int end) {
        int i = start;
        int localBrace = 0;
        int localParen = 0;
        int localBracket = 0;
        while (i < end && i < length) {
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
                int wordEnd = readIdentifierEnd(i + 1);
                if (localBrace == 0 && localParen == 0 && localBracket == 0 && "await".equals(code.substring(i, wordEnd))) {
                    topLevelAwait = true;
                    return;
                }
                i = wordEnd;
                continue;
            }
            switch (c) {
                case '{' -> localBrace++;
                case '}' -> localBrace = Math.max(0, localBrace - 1);
                case '(' -> localParen++;
                case ')' -> localParen = Math.max(0, localParen - 1);
                case '[' -> localBracket++;
                case ']' -> localBracket = Math.max(0, localBracket - 1);
                default -> {
                }
            }
            i++;
        }
    }

    private void replayScopeSyntax(int start, int end) {
        int i = start;
        int depth = 0;
        while (i < end && i < length) {
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
                int wordEnd = readIdentifierEnd(i + 1);
                String word = code.substring(i, wordEnd);
                if ("function".equals(word)) {
                    markFunctionBodyStart(wordEnd);
                } else if ("catch".equals(word)) {
                    recordCatchBinding(wordEnd);
                }
                if (depth > 0) {
                    recordLocalDeclaration(i, word);
                }
                i = wordEnd;
                continue;
            }
            switch (c) {
                case '{' -> {
                    maybeMarkFunctionLikeBody(i);
                    openBraceScope(i);
                    depth++;
                }
                case '}' -> {
                    closeBraceScope(i);
                    depth = Math.max(0, depth - 1);
                }
                default -> {
                }
            }
            i++;
        }
    }

    private void recordImportBindings(NekoEsmSpan span, NekoEsmImportDecl importDecl) {
        if (importDecl.defaultName() != null) {
            localBindings.add(new NekoEsmLocalBinding(importDecl.defaultName(), "import", NekoEsmBindingSource.IMPORT, span, 0));
        }
        if (importDecl.namespaceName() != null) {
            localBindings.add(new NekoEsmLocalBinding(importDecl.namespaceName(), "import", NekoEsmBindingSource.IMPORT, span, 0));
        }
        for (NekoEsmBinding binding : importDecl.namedBindings()) {
            localBindings.add(new NekoEsmLocalBinding(binding.local(), "import", NekoEsmBindingSource.IMPORT, span, 0));
        }
    }

    private NekoEsmImportDecl parseImport(NekoEsmSpan span, String statement) {
        TokenCursor cursor = new TokenCursor(span, statement);
        cursor.expectIdentifier("import");
        if (cursor.peekKind(NekoEsmTokenKind.STRING)) {
            Specifier specifier = cursor.expectSpecifier();
            cursor.skipOptionalSemicolon();
            cursor.expectEnd("Unsupported import syntax: " + oneLine(statement));
            return new NekoEsmImportDecl(span, statement, specifier.value, specifier.span, null, null, List.of(), true);
        }

        String defaultName = null;
        String namespaceName = null;
        List<NekoEsmBinding> namedBindings = new ArrayList<>();
        if (cursor.peekKind(NekoEsmTokenKind.IDENTIFIER)) {
            defaultName = cursor.expectIdentifierName("Expected default import binding");
            if (cursor.consumeText(",")) {
                ImportBindings nested = parseSecondaryImportBindings(cursor, statement);
                namespaceName = nested.namespaceName;
                namedBindings.addAll(nested.namedBindings);
            }
        } else {
            ImportBindings bindings = parseSecondaryImportBindings(cursor, statement);
            namespaceName = bindings.namespaceName;
            namedBindings.addAll(bindings.namedBindings);
        }
        cursor.expectIdentifier("from");
        Specifier specifier = cursor.expectSpecifier();
        cursor.skipOptionalSemicolon();
        cursor.expectEnd("Unsupported import syntax: " + oneLine(statement));
        return new NekoEsmImportDecl(span, statement, specifier.value, specifier.span, defaultName, namespaceName, namedBindings, false);
    }

    private ImportBindings parseSecondaryImportBindings(TokenCursor cursor, String statement) {
        if (cursor.consumeText("*")) {
            cursor.expectIdentifier("as");
            return new ImportBindings(null, cursor.expectIdentifierName("Expected namespace import binding"), List.of());
        }
        if (cursor.peekText("{")) {
            return new ImportBindings(null, null, parseNamedBindings(cursor, true));
        }
        throw error("Unsupported import bindings: " + oneLine(statement));
    }

    private NekoEsmExportDecl parseExport(NekoEsmSpan span, String statement) {
        TokenCursor cursor = new TokenCursor(span, statement);
        cursor.expectIdentifier("export");
        if (cursor.consumeIdentifier("default")) {
            return parseDefaultExport(span, statement, cursor);
        }
        if (cursor.consumeText("*")) {
            if (cursor.consumeIdentifier("as")) {
                String namespaceName = cursor.expectIdentifierName("Expected namespace re-export binding");
                cursor.expectIdentifier("from");
                Specifier specifier = cursor.expectSpecifier();
                cursor.skipOptionalSemicolon();
                cursor.expectEnd("Unsupported namespace re-export syntax: " + oneLine(statement));
                return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.RE_EXPORT_NAMESPACE, specifier.value, specifier.span, null, null, namespaceName, null, List.of());
            }
            cursor.expectIdentifier("from");
            Specifier specifier = cursor.expectSpecifier();
            cursor.skipOptionalSemicolon();
            cursor.expectEnd("Unsupported star re-export syntax: " + oneLine(statement));
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.RE_EXPORT_ALL, specifier.value, specifier.span, null, null, null, null, List.of());
        }
        if (cursor.peekText("{")) {
            List<NekoEsmBinding> bindings = parseNamedBindings(cursor, false);
            if (cursor.consumeIdentifier("from")) {
                Specifier specifier = cursor.expectSpecifier();
                cursor.skipOptionalSemicolon();
                cursor.expectEnd("Unsupported re-export syntax: " + oneLine(statement));
                return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.RE_EXPORT_LIST, specifier.value, specifier.span, null, null, null, null, bindings);
            }
            cursor.skipOptionalSemicolon();
            cursor.expectEnd("Unsupported export list suffix: " + oneLine(statement));
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.LIST, null, null, null, null, null, null, bindings);
        }
        DeclarationHead declaration = parseDeclarationHead(cursor);
        if (declaration == null) {
            throw error("Unsupported export syntax: " + oneLine(statement));
        }
        if ("const".equals(declaration.kind) || "let".equals(declaration.kind) || "var".equals(declaration.kind)) {
            List<ParsedBinding> parsedBindings = parseVariableDeclarationBindings(cursor, declaration.kind, NekoEsmBindingSource.EXPORT_DECLARATION, 0);
            if (parsedBindings.isEmpty()) {
                throw error("Unsupported export declaration: " + oneLine(statement));
            }
            List<NekoEsmBinding> exportBindings = new ArrayList<>();
            for (ParsedBinding binding : parsedBindings) {
                exportBindings.add(new NekoEsmBinding(binding.name, binding.name));
            }
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DECLARATION, null, null, declaration.kind, parsedBindings.get(0).name, null, null, exportBindings);
        }
        NekoEsmToken name = cursor.expectIdentifierToken("Expected exported declaration name");
        String localName = name.text();
        NekoEsmSpan nameSpan = cursor.absoluteSpan(name);
        localBindings.add(new NekoEsmLocalBinding(localName, declaration.kind, NekoEsmBindingSource.EXPORT_DECLARATION, nameSpan, 0));
        if (declaration.kind.contains("function")) {
            markFunctionBodyStart(nameSpan.end());
        }
        return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DECLARATION, null, null, declaration.kind, localName, null, null, List.of(new NekoEsmBinding(localName, localName)));
    }

    private NekoEsmExportDecl parseDefaultExport(NekoEsmSpan span, String statement, TokenCursor cursor) {
        DeclarationHead declaration = parseDefaultDeclarationHead(cursor);
        if (declaration != null) {
            if (cursor.peekKind(NekoEsmTokenKind.IDENTIFIER) && !cursor.peekIdentifier("extends")) {
                NekoEsmToken name = cursor.consume();
                String localName = name.text();
                NekoEsmSpan nameSpan = cursor.absoluteSpan(name);
                localBindings.add(new NekoEsmLocalBinding(localName, declaration.kind, NekoEsmBindingSource.EXPORT_DEFAULT_DECLARATION, nameSpan, 0));
                if (declaration.kind.contains("function")) {
                    markFunctionBodyStart(nameSpan.end());
                }
                return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DEFAULT_NAMED_DECLARATION, null, null, declaration.kind, localName, null, null, List.of());
            }
            return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DEFAULT_ANONYMOUS_DECLARATION, null, null, declaration.kind, null, null, null, List.of());
        }
        String expression = defaultExportExpression(cursor);
        return new NekoEsmExportDecl(span, statement, NekoEsmExportKind.DEFAULT_EXPRESSION, null, null, null, null, null, expression, List.of());
    }

    private List<NekoEsmBinding> parseNamedBindings(TokenCursor cursor, boolean importBindings) {
        cursor.expectText("{");
        List<NekoEsmBinding> bindings = new ArrayList<>();
        while (!cursor.consumeText("}")) {
            String imported = cursor.expectIdentifierName("Expected named ESM binding");
            String local = imported;
            if (cursor.consumeIdentifier("as")) {
                local = cursor.expectIdentifierName("Expected named ESM binding alias");
            }
            bindings.add(new NekoEsmBinding(imported, local));
            if (cursor.consumeText(",")) {
                continue;
            }
            cursor.expectText("}");
            break;
        }
        return List.copyOf(bindings);
    }

    private DeclarationHead parseDeclarationHead(TokenCursor cursor) {
        if (cursor.consumeIdentifier("const")) return new DeclarationHead("const");
        if (cursor.consumeIdentifier("let")) return new DeclarationHead("let");
        if (cursor.consumeIdentifier("var")) return new DeclarationHead("var");
        int mark = cursor.mark();
        boolean async = cursor.consumeIdentifier("async");
        if (cursor.consumeIdentifier("function")) {
            boolean generator = cursor.consumeText("*");
            return new DeclarationHead(functionKind(async, generator));
        }
        cursor.reset(mark);
        if (cursor.consumeIdentifier("class")) return new DeclarationHead("class");
        return null;
    }

    private DeclarationHead parseDefaultDeclarationHead(TokenCursor cursor) {
        int mark = cursor.mark();
        boolean async = cursor.consumeIdentifier("async");
        if (cursor.consumeIdentifier("function")) {
            boolean generator = cursor.consumeText("*");
            return new DeclarationHead(functionKind(async, generator));
        }
        cursor.reset(mark);
        if (cursor.consumeIdentifier("class")) return new DeclarationHead("class");
        return null;
    }

    private String functionKind(boolean async, boolean generator) {
        if (async && generator) return "async function*";
        if (async) return "async function";
        return generator ? "function*" : "function";
    }

    private List<ParsedBinding> parseVariableDeclarationBindings(TokenCursor cursor, String kind, NekoEsmBindingSource source, int scopeId) {
        List<ParsedBinding> bindings = new ArrayList<>();
        while (!cursor.atEnd() && !cursor.peekText(";")) {
            if (cursor.consumeText(",")) {
                continue;
            }
            if (cursor.peekKind(NekoEsmTokenKind.IDENTIFIER)) {
                NekoEsmToken token = cursor.consume();
                NekoEsmSpan bindingSpan = cursor.absoluteSpan(token);
                localBindings.add(new NekoEsmLocalBinding(token.text(), kind, source, bindingSpan, scopeId));
                bindings.add(new ParsedBinding(token.text(), bindingSpan));
            } else if (cursor.peekText("{") || cursor.peekText("[")) {
                int startIndex = cursor.mark();
                int endIndex = cursor.balancedPatternEndIndex();
                if (endIndex <= startIndex) {
                    throw error("Unsupported variable export binding pattern");
                }
                NekoEsmToken start = cursor.token(startIndex);
                NekoEsmToken end = cursor.token(endIndex - 1);
                NekoEsmSpan patternSpan = new NekoEsmSpan(cursor.absoluteStart(start), cursor.absoluteEnd(end));
                String pattern = cursor.sourceBetween(start.span().start(), end.span().end());
                for (NekoEsmLocalBinding binding : recordPatternBindings(pattern, kind, source, patternSpan, scopeId)) {
                    bindings.add(new ParsedBinding(binding.name(), binding.span()));
                }
                cursor.reset(endIndex);
            } else {
                break;
            }
            if (!cursor.skipToNextVariableDeclarator()) {
                break;
            }
        }
        return List.copyOf(bindings);
    }

    private String defaultExportExpression(TokenCursor cursor) {
        String expression = cursor.remainingSource().trim();
        if (expression.endsWith(";")) {
            expression = expression.substring(0, expression.length() - 1).trim();
        }
        return expression;
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

    private final class TokenCursor {
        private final NekoEsmSpan statementSpan;
        private final String source;
        private final List<NekoEsmToken> tokens;
        private int index;

        private TokenCursor(NekoEsmSpan statementSpan, String source) {
            this.statementSpan = statementSpan;
            this.source = source == null ? "" : source;
            this.tokens = new NekoEsmLexer(this.source).tokenize();
        }

        private int mark() {
            return index;
        }

        private void reset(int mark) {
            index = mark;
        }

        private boolean atEnd() {
            return peek().kind() == NekoEsmTokenKind.EOF;
        }

        private NekoEsmToken token(int tokenIndex) {
            return tokens.get(tokenIndex);
        }

        private NekoEsmToken peek() {
            return tokens.get(Math.min(index, tokens.size() - 1));
        }

        private NekoEsmToken consume() {
            return tokens.get(index++);
        }

        private boolean peekKind(NekoEsmTokenKind kind) {
            return peek().kind() == kind;
        }

        private boolean peekText(String text) {
            return peek().text(text);
        }

        private boolean peekIdentifier(String text) {
            return peek().identifier(text);
        }

        private boolean consumeText(String text) {
            if (!peekText(text)) return false;
            index++;
            return true;
        }

        private boolean consumeIdentifier(String text) {
            if (!peekIdentifier(text)) return false;
            index++;
            return true;
        }

        private void expectText(String text) {
            if (!consumeText(text)) {
                throw error("Expected '" + text + "' in ESM statement");
            }
        }

        private void expectIdentifier(String text) {
            if (!consumeIdentifier(text)) {
                throw error("Expected '" + text + "' in ESM statement");
            }
        }

        private String expectIdentifierName(String message) {
            return expectIdentifierToken(message).text();
        }

        private NekoEsmToken expectIdentifierToken(String message) {
            if (!peekKind(NekoEsmTokenKind.IDENTIFIER)) {
                throw error(message);
            }
            return consume();
        }

        private Specifier expectSpecifier() {
            if (!peekKind(NekoEsmTokenKind.STRING)) {
                throw error("Expected ESM module specifier");
            }
            NekoEsmToken token = consume();
            return new Specifier(token.value(), absoluteSpan(token));
        }

        private void skipOptionalSemicolon() {
            consumeText(";");
        }

        private void expectEnd(String message) {
            if (!atEnd()) {
                throw error(message);
            }
        }

        private int balancedPatternEndIndex() {
            if (!peekText("{") && !peekText("[")) {
                return index;
            }
            Deque<String> closing = new ArrayDeque<>();
            int i = index;
            while (i < tokens.size()) {
                NekoEsmToken token = tokens.get(i);
                if (token.kind() == NekoEsmTokenKind.EOF) {
                    break;
                }
                String text = token.text();
                if ("{".equals(text)) closing.push("}");
                else if ("[".equals(text)) closing.push("]");
                else if ("(".equals(text)) closing.push(")");
                else if ("}".equals(text) || "]".equals(text) || ")".equals(text)) {
                    if (closing.isEmpty() || !closing.pop().equals(text)) {
                        return index;
                    }
                    if (closing.isEmpty()) {
                        return i + 1;
                    }
                }
                i++;
            }
            return index;
        }

        private boolean skipToNextVariableDeclarator() {
            int depth = 0;
            while (!atEnd()) {
                String text = peek().text();
                if (depth == 0) {
                    if (";".equals(text)) {
                        consume();
                        return false;
                    }
                    if (",".equals(text)) {
                        consume();
                        return true;
                    }
                }
                if ("{".equals(text) || "[".equals(text) || "(".equals(text)) {
                    depth++;
                } else if ("}".equals(text) || "]".equals(text) || ")".equals(text)) {
                    if (depth == 0) {
                        return false;
                    }
                    depth--;
                }
                consume();
            }
            return false;
        }

        private NekoEsmSpan absoluteSpan(NekoEsmToken token) {
            return new NekoEsmSpan(absoluteStart(token), absoluteEnd(token));
        }

        private int absoluteStart(NekoEsmToken token) {
            return statementSpan.start() + token.span().start();
        }

        private int absoluteEnd(NekoEsmToken token) {
            return statementSpan.start() + token.span().end();
        }

        private String sourceBetween(int start, int end) {
            return source.substring(Math.max(0, start), Math.min(source.length(), end));
        }

        private String remainingSource() {
            if (atEnd()) {
                return "";
            }
            return source.substring(peek().span().start());
        }
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

    private record DeclarationHead(String kind) {}

    private record ParsedBinding(String name, NekoEsmSpan span) {}

    private record Specifier(String value, NekoEsmSpan span) {}

    private record PendingFunctionParameters(int bodyStart, String raw, int absoluteOffset) {}

    private record PendingBlockBinding(int bodyStart, String raw, String kind, int absoluteOffset) {}

    private record ScopeFrame(int id, int parentId, NekoEsmScopeKind kind, int start, boolean classBody) {}

    private record FunctionDescriptor(NekoEsmSpan span, String raw, String name, NekoJsFunctionKind kind) {}

    private record ClassMemberHeader(int start, int end, String name, boolean isStatic, boolean isPrivate, boolean computed, boolean accessor, NekoJsFunctionKind functionKind) {}
}
