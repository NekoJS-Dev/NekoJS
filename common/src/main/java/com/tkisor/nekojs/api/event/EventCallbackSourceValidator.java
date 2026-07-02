package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.JavaMemberIndex;
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

/**
 * 事件回调注册时的加载时预检：解析回调源码，扫描其首参（事件对象）的成员访问，
 * 对照 {@link JavaMemberIndex#propertyMembersOf(Class)} 校验是否存在；不存在则上报。
 *
 * <p>成员访问扫描委托 {@link ScriptMemberAccessScanner}（与 {@code GlobalBindingMemberValidator} 共享）。
 * 这里只负责识别回调首参名（箭头函数 / function 表达式）并把命中位点映射回 {@link SourceSection} 的绝对行列。
 */
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

        Set<String> validMembers = JavaMemberIndex.propertyMembersOf(eventType);
        Set<String> reported = new HashSet<>();
        ScriptMemberAccessScanner.scan(source, Set.of(parameter), (identifier, member, offset) -> {
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
            String message = JavaMemberIndex.unknownMemberMessage(eventType, member);

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

    private static int skipWhitespace(String source, int index) {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
        return index;
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
}
