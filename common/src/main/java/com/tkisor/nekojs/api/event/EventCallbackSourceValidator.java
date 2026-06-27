package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;
import com.tkisor.nekojs.script.ScriptType;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;
import graal.graalvm.polyglot.Value;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

final class EventCallbackSourceValidator {
    private EventCallbackSourceValidator() {}

    static void validateRegistration(Value listener, Class<?> eventType, ScriptType scriptType, String scriptId, String eventLabel) {
        if (listener == null || eventType == null || !listener.canExecute()) {
            return;
        }
        SourceSection section = sourceSection(listener);
        if (section == null) {
            return;
        }
        CharSequence chars = section.getCharacters();
        if (chars == null || chars.isEmpty()) {
            return;
        }
        String source = chars.toString();
        String parameter = firstParameterName(source);
        if (parameter == null || parameter.isBlank()) {
            return;
        }

        Set<String> validMembers = EventProxy.validMembers(eventType);
        Set<String> reported = new HashSet<>();
        scanMemberAccesses(source, parameter, (member, offset) -> {
            if (validMembers.contains(member) || !reported.add(member)) {
                return;
            }
            reportInvalidMember(section, source, offset, member, eventType, scriptType, scriptId, eventLabel);
        });
    }

    private static SourceSection sourceSection(Value listener) {
        try {
            return listener.getSourceLocation();
        } catch (UnsupportedOperationException | IllegalStateException ignored) {
            return null;
        } catch (Throwable t) {
            NekoJS.LOGGER.debug("Failed to get event callback source location", t);
            return null;
        }
    }

    private static void reportInvalidMember(
            SourceSection section,
            String source,
            int offset,
            String member,
            Class<?> eventType,
            ScriptType scriptType,
            String scriptId,
            String eventLabel
    ) {
        try {
            String message = EventProxy.unknownMemberMessage(eventType, member);

            int[] local = lineColumn(source, offset);
            int line = section.getStartLine() + local[0] - 1;
            int column = local[0] == 1 ? section.getStartColumn() + local[1] - 1 : local[1];
            Path file = sourcePath(section.getSource());
            NekoEsmDiagnostic diagnostic = new NekoEsmDiagnostic(file, new NekoEsmSpan(offset, offset + member.length()), line, column, message);
            String kind = "event-preflight"
                    + " bus=" + eventType.getName()
                    + " label=" + (eventLabel == null || eventLabel.isBlank() ? "unknown" : eventLabel)
                    + " script=" + (scriptId == null || scriptId.isBlank() ? "unknown" : scriptId);
            ScriptErrorReporter.recordCallbackError(scriptType, kind, new NekoEsmLinkException(diagnostic));
        } catch (Throwable t) {
            NekoJS.LOGGER.debug("Failed to report event callback validation error", t);
        }
    }

    private static Path sourcePath(Source source) {
        if (source == null) {
            return null;
        }
        try {
            if (source.getPath() != null && !source.getPath().isBlank()) {
                return Path.of(source.getPath());
            }
            if (source.getURI() != null && "file".equalsIgnoreCase(source.getURI().getScheme())) {
                return Path.of(source.getURI());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int[] lineColumn(String source, int offset) {
        int line = 1;
        int column = 1;
        int end = Math.min(Math.max(offset, 0), source.length());
        for (int i = 0; i < end; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[] { line, column };
    }

    private static String firstParameterName(String source) {
        int i = skipWhitespace(source, 0);
        if (source.startsWith("async", i) && isBoundary(source, i + 5)) {
            i = skipWhitespace(source, i + 5);
        }
        if (source.startsWith("function", i) && isBoundary(source, i + 8)) {
            int open = source.indexOf('(', i + 8);
            return open >= 0 ? firstParameterInParens(source, open) : null;
        }
        if (i < source.length() && source.charAt(i) == '(') {
            int close = matchingParen(source, i);
            if (close < 0) return null;
            int arrow = skipWhitespace(source, close + 1);
            if (source.startsWith("=>", arrow)) {
                return firstParameterInParens(source, i);
            }
            return null;
        }
        int start = i;
        if (!isIdentifierStart(charAt(source, i))) {
            return null;
        }
        i++;
        while (isIdentifierPart(charAt(source, i))) {
            i++;
        }
        int arrow = skipWhitespace(source, i);
        return source.startsWith("=>", arrow) ? source.substring(start, i) : null;
    }

    private static String firstParameterInParens(String source, int open) {
        int i = skipWhitespace(source, open + 1);
        char first = charAt(source, i);
        if (!isIdentifierStart(first)) {
            return null;
        }
        int start = i++;
        while (isIdentifierPart(charAt(source, i))) {
            i++;
        }
        return source.substring(start, i);
    }

    private static int matchingParen(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void scanMemberAccesses(String source, String parameter, MemberConsumer consumer) {
        for (int i = 0; i < source.length();) {
            char c = source.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuoted(source, i, c);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(source, i);
                continue;
            }
            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    i = skipLineComment(source, i + 2);
                    continue;
                }
                if (next == '*') {
                    i = skipBlockComment(source, i + 2);
                    continue;
                }
            }
            if (isIdentifierStart(c)) {
                int start = i++;
                while (isIdentifierPart(charAt(source, i))) {
                    i++;
                }
                if (source.substring(start, i).equals(parameter) && boundaryBefore(source, start)) {
                    int cursor = skipWhitespace(source, i);
                    if (source.startsWith("?.", cursor)) {
                        cursor += 2;
                    } else if (charAt(source, cursor) == '.') {
                        cursor++;
                    } else if (charAt(source, cursor) == '[') {
                        scanBracketMember(source, cursor, consumer);
                        continue;
                    } else {
                        continue;
                    }
                    cursor = skipWhitespace(source, cursor);
                    if (isIdentifierStart(charAt(source, cursor))) {
                        int memberStart = cursor++;
                        while (isIdentifierPart(charAt(source, cursor))) {
                            cursor++;
                        }
                        consumer.accept(source.substring(memberStart, cursor), memberStart);
                    }
                }
                continue;
            }
            i++;
        }
    }

    private static void scanBracketMember(String source, int open, MemberConsumer consumer) {
        int i = skipWhitespace(source, open + 1);
        char quote = charAt(source, i);
        if (quote != '\'' && quote != '"') {
            return;
        }
        int start = i + 1;
        int end = start;
        while (end < source.length()) {
            char c = source.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == quote) {
                int close = skipWhitespace(source, end + 1);
                if (charAt(source, close) == ']') {
                    consumer.accept(source.substring(start, end), start);
                }
                return;
            }
            end++;
        }
    }

    private static int skipWhitespace(String source, int index) {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int skipQuoted(String source, int start, char quote) {
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int skipTemplate(String source, int start) {
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '`') {
                return i + 1;
            }
        }
        return source.length();
    }

    private static int skipLineComment(String source, int start) {
        int end = source.indexOf('\n', start);
        return end < 0 ? source.length() : end + 1;
    }

    private static int skipBlockComment(String source, int start) {
        int end = source.indexOf("*/", start);
        return end < 0 ? source.length() : end + 2;
    }

    private static boolean boundaryBefore(String source, int index) {
        return index <= 0 || !isIdentifierPart(source.charAt(index - 1));
    }

    private static boolean isBoundary(String source, int index) {
        return index >= source.length() || !isIdentifierPart(source.charAt(index));
    }

    private static char charAt(String source, int index) {
        return index >= 0 && index < source.length() ? source.charAt(index) : '\0';
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return c == '_' || c == '$' || Character.isLetterOrDigit(c);
    }

    private interface MemberConsumer {
        void accept(String member, int offset);
    }
}
