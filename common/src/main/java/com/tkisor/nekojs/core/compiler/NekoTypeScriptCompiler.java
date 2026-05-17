package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.IScriptCompiler;
import com.tkisor.nekojs.api.compiler.ScriptCompileResult;

import java.nio.file.Path;
import java.util.Locale;

public final class NekoTypeScriptCompiler implements IScriptCompiler {
    @Override
    public boolean canCompile(String extension) {
        if (extension == null) return false;
        String normalized = extension.toLowerCase(Locale.ROOT).trim();
        return ".ts".equals(normalized) || "ts".equals(normalized);
    }

    @Override
    public String compile(Path file, String sourceCode) throws Exception {
        return compileDetailed(file, sourceCode).code();
    }

    @Override
    public ScriptCompileResult compileDetailed(Path file, String sourceCode) throws Exception {
        return ScriptCompileResult.codeOnly(erase(file, sourceCode));
    }

    static String erase(Path file, String source) {
        return new Eraser(file, source == null ? "" : source).erase();
    }

    private static final class Eraser {
        private final Path file;
        private final String source;
        private final StringBuilder out;
        private final int length;

        private Eraser(Path file, String source) {
            this.file = file;
            this.source = source;
            this.out = new StringBuilder(source);
            this.length = source.length();
        }

        private String erase() {
            int i = 0;
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
                if (isIdentifierStart(c)) {
                    int end = readIdentifierEnd(i + 1);
                    String word = source.substring(i, end);
                    if ("interface".equals(word) || "type".equals(word)) {
                        i = eraseTypeDeclaration(i);
                        continue;
                    }
                    if ("enum".equals(word) || "namespace".equals(word) || "module".equals(word)) {
                        throw unsupported(word, i);
                    }
                    if ("declare".equals(word)) {
                        i = eraseDeclare(i);
                        continue;
                    }
                    if ("import".equals(word) && typeKeywordAfter(end)) {
                        i = eraseStatement(i);
                        continue;
                    }
                    if ("export".equals(word) && typeExportAfter(end)) {
                        i = eraseStatement(i);
                        continue;
                    }
                    if ("implements".equals(word)) {
                        i = eraseImplements(i, end);
                        continue;
                    }
                    if ("as".equals(word) || "satisfies".equals(word)) {
                        i = eraseAssertion(i, end);
                        continue;
                    }
                    i = end;
                    continue;
                }
                if (c == '<' && genericTypeArgumentsAt(i)) {
                    int end = matchingAngle(i);
                    eraseRange(i, end + 1);
                    i = end + 1;
                    continue;
                }
                if (c == ':' && typeAnnotationAt(i)) {
                    int end = typeAnnotationEnd(i + 1);
                    eraseRange(i, end);
                    i = end;
                    continue;
                }
                if (c == '!' && definiteAssignmentAt(i)) {
                    eraseRange(i, i + 1);
                    i++;
                    continue;
                }
                i++;
            }
            return out.toString();
        }

        private int eraseTypeDeclaration(int start) {
            int end = statementOrBlockDeclarationEnd(start);
            eraseRange(start, end);
            return end;
        }

        private int eraseDeclare(int start) {
            int after = nextNonWhitespace(start + "declare".length());
            if (startsWithKeyword(after, "global") || startsWithKeyword(after, "module") || startsWithKeyword(after, "namespace")) {
                int end = statementOrBlockDeclarationEnd(start);
                eraseRange(start, end);
                return end;
            }
            eraseRange(start, after);
            return after;
        }

        private int eraseStatement(int start) {
            int end = statementEnd(start);
            eraseRange(start, end);
            return end;
        }

        private int eraseImplements(int start, int wordEnd) {
            int end = wordEnd;
            while (end < length) {
                char c = source.charAt(end);
                if (c == '\'' || c == '"') {
                    end = skipString(end, c);
                    continue;
                }
                if (c == '`') {
                    end = skipTemplate(end);
                    continue;
                }
                if (c == '{') break;
                end++;
            }
            eraseRange(start, end);
            return end;
        }

        private int eraseAssertion(int start, int wordEnd) {
            if (!assertionContext(start)) {
                return wordEnd;
            }
            int end = typeExpressionEnd(wordEnd);
            eraseRange(start, end);
            return end;
        }

        private boolean assertionContext(int start) {
            int previous = previousNonWhitespace(start - 1);
            return previous >= 0 && ")]}'\"`abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_$".indexOf(source.charAt(previous)) >= 0;
        }

        private boolean typeKeywordAfter(int index) {
            int i = nextNonWhitespace(index);
            return startsWithKeyword(i, "type");
        }

        private boolean typeExportAfter(int index) {
            int i = nextNonWhitespace(index);
            return startsWithKeyword(i, "type") || startsWithKeyword(i, "interface");
        }

        private boolean genericTypeArgumentsAt(int start) {
            int previous = previousNonWhitespace(start - 1);
            if (previous < 0) return false;
            char previousChar = source.charAt(previous);
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']') return false;
            int close = matchingAngle(start);
            if (close < 0) return false;
            int next = nextNonWhitespace(close + 1);
            return next < length && (source.charAt(next) == '(' || source.charAt(next) == '{');
        }

        private boolean typeAnnotationAt(int colon) {
            int previous = previousNonWhitespace(colon - 1);
            if (previous < 0) return false;
            char previousChar = source.charAt(previous);
            if (!isIdentifierPart(previousChar) && previousChar != ')' && previousChar != ']' && previousChar != '}') return false;
            int next = nextNonWhitespace(colon + 1);
            if (next >= length) return false;
            char nextChar = source.charAt(next);
            if (nextChar == ':' || nextChar == ',' || nextChar == ';' || nextChar == ')' || nextChar == '{') return false;
            return !objectLiteralPropertyColon(colon);
        }

        private boolean objectLiteralPropertyColon(int colon) {
            int previous = previousNonWhitespace(colon - 1);
            int next = nextNonWhitespace(colon + 1);
            if (previous < 0 || next >= length) return false;
            int beforeProperty = propertyStart(previous) - 1;
            int previousToken = previousNonWhitespace(beforeProperty);
            if (previousToken < 0) return false;
            char c = source.charAt(previousToken);
            return c == '{' || c == ',';
        }

        private int propertyStart(int endInclusive) {
            int i = endInclusive;
            if (source.charAt(i) == '\'' || source.charAt(i) == '"') {
                char quote = source.charAt(i);
                i--;
                while (i >= 0) {
                    if (source.charAt(i) == quote) return i;
                    i--;
                }
                return endInclusive;
            }
            while (i >= 0 && isIdentifierPart(source.charAt(i))) i--;
            return i + 1;
        }

        private int typeAnnotationEnd(int start) {
            return typeExpressionEnd(start);
        }

        private int typeExpressionEnd(int start) {
            int i = start;
            int angle = 0;
            int paren = 0;
            int bracket = 0;
            int brace = 0;
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
                if (c == '<') angle++;
                else if (c == '>' && angle > 0) angle--;
                else if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{') {
                    if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) return i;
                    brace++;
                } else if (c == '}' && brace > 0) brace--;
                if (angle == 0 && paren == 0 && bracket == 0 && brace == 0) {
                    if (c == '=' || c == ',' || c == ';' || c == ')' || c == '{' || c == '\n' || c == '\r') {
                        return i;
                    }
                }
                i++;
            }
            return i;
        }

        private boolean definiteAssignmentAt(int bang) {
            int previous = previousNonWhitespace(bang - 1);
            int next = nextNonWhitespace(bang + 1);
            return previous >= 0 && next < length && isIdentifierPart(source.charAt(previous)) && (source.charAt(next) == ':' || source.charAt(next) == '=' || source.charAt(next) == ';');
        }

        private int statementOrBlockDeclarationEnd(int start) {
            int bodyStart = -1;
            int i = start;
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
                if (c == '{') {
                    bodyStart = i;
                    break;
                }
                if (c == ';' || c == '\n' || c == '\r') return i + 1;
                i++;
            }
            if (bodyStart < 0) return statementEnd(start);
            int bodyEnd = matchingCloseBrace(bodyStart);
            return bodyEnd < 0 ? length : bodyEnd + 1;
        }

        private int statementEnd(int start) {
            int i = start;
            int paren = 0;
            int bracket = 0;
            int brace = 0;
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
                if (c == '(') paren++;
                else if (c == ')' && paren > 0) paren--;
                else if (c == '[') bracket++;
                else if (c == ']' && bracket > 0) bracket--;
                else if (c == '{') brace++;
                else if (c == '}' && brace > 0) brace--;
                if (paren == 0 && bracket == 0 && brace == 0 && (c == ';' || c == '\n' || c == '\r')) return i + 1;
                i++;
            }
            return i;
        }

        private int matchingCloseBrace(int open) {
            int depth = 0;
            int i = open;
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
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
                i++;
            }
            return -1;
        }

        private int matchingAngle(int open) {
            int depth = 0;
            int i = open;
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
                if (c == '<') depth++;
                else if (c == '>') {
                    depth--;
                    if (depth == 0) return i;
                }
                if ((c == ';' || c == '\n' || c == '\r') && depth > 0) return -1;
                i++;
            }
            return -1;
        }

        private int skipSlash(int slash) {
            if (slash + 1 >= length) return slash;
            char next = source.charAt(slash + 1);
            if (next == '/') return skipLineComment(slash + 2);
            if (next == '*') return skipBlockComment(slash + 2);
            if (looksLikeRegexStart(slash)) return skipRegex(slash + 1);
            return slash;
        }

        private int skipString(int start, char quote) {
            int i = start + 1;
            while (i < length) {
                char c = source.charAt(i);
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
                char c = source.charAt(i);
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
            while (i < length && source.charAt(i) != '\n' && source.charAt(i) != '\r') i++;
            return i;
        }

        private int skipBlockComment(int start) {
            int i = start;
            while (i + 1 < length) {
                if (source.charAt(i) == '*' && source.charAt(i + 1) == '/') return i + 2;
                i++;
            }
            return length;
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
                if (c == '[') inClass = true;
                else if (c == ']') inClass = false;
                else if (c == '/' && !inClass) {
                    i++;
                    while (i < length && isIdentifierPart(source.charAt(i))) i++;
                    return i;
                }
                i++;
            }
            return length;
        }

        private boolean looksLikeRegexStart(int slash) {
            int previous = previousNonWhitespace(slash - 1);
            if (previous < 0) return true;
            char c = source.charAt(previous);
            return "=(:,[!&|?;{}\n\r".indexOf(c) >= 0;
        }

        private int nextNonWhitespace(int index) {
            int i = Math.max(0, index);
            while (i < length && Character.isWhitespace(source.charAt(i))) i++;
            return i;
        }

        private int previousNonWhitespace(int index) {
            int i = Math.min(index, length - 1);
            while (i >= 0 && Character.isWhitespace(source.charAt(i))) i--;
            return i;
        }

        private int readIdentifierEnd(int start) {
            int i = start;
            while (i < length && isIdentifierPart(source.charAt(i))) i++;
            return i;
        }

        private boolean startsWithKeyword(int start, String keyword) {
            if (start < 0 || start + keyword.length() > length || !source.startsWith(keyword, start)) return false;
            boolean before = start == 0 || !isIdentifierPart(source.charAt(start - 1));
            boolean after = start + keyword.length() >= length || !isIdentifierPart(source.charAt(start + keyword.length()));
            return before && after;
        }

        private boolean isIdentifierStart(char c) {
            return Character.isUnicodeIdentifierStart(c) || c == '$' || c == '_';
        }

        private boolean isIdentifierPart(char c) {
            return Character.isUnicodeIdentifierPart(c) || c == '$' || c == '_';
        }

        private void eraseRange(int start, int end) {
            int safeEnd = Math.min(length, Math.max(start, end));
            for (int i = start; i < safeEnd; i++) {
                char c = source.charAt(i);
                if (c != '\n' && c != '\r') {
                    out.setCharAt(i, ' ');
                }
            }
        }

        private IllegalArgumentException unsupported(String syntax, int index) {
            return new IllegalArgumentException("Unsupported TypeScript syntax '" + syntax + "' in " + file + " at " + position(index) + ". Use plain erasable TypeScript or register a compiler plugin for this syntax.");
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
}
