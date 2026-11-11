package dev.latvian.mods.kubejs.util;

import dev.latvian.mods.rhino.Wrapper;
import graal.graalvm.polyglot.Value;
import java.util.regex.Pattern;

/** Converts legacy slash-delimited regex literals to native Java patterns. */
public interface RegExpKJS {
    /** Returns null for non-regex inputs so codec dispatch can try its other representations. */
    static Pattern wrap(Object input) {
        input = Wrapper.unwrapped(input);
        if (input instanceof Value value) {
            if (value.isHostObject()) return wrap(value.asHostObject());
            if (value.isString()) input = value.asString();
        }
        if (input instanceof Pattern pattern) return pattern;
        if (!(input instanceof CharSequence text)) return null;
        return ofString(text.toString());
    }

    static Pattern ofString(String literal) {
        if (literal.length() < 3 || literal.charAt(0) != '/') return null;
        StringBuilder expression = new StringBuilder();
        int position = 1;
        while (position < literal.length()) {
            char current = literal.charAt(position++);
            if (current == '\\' && position < literal.length() && literal.charAt(position) == '/') {
                position++;
                expression.append('/');
            } else if (current == '/') {
                break;
            } else {
                expression.append(current);
            }
        }
        int flags = 0;
        while (position < literal.length()) {
            int flag = switch (literal.charAt(position++)) {
                case 'd' -> Pattern.UNIX_LINES;
                case 'i' -> Pattern.CASE_INSENSITIVE;
                case 'x' -> Pattern.COMMENTS;
                case 'm' -> Pattern.MULTILINE;
                case 's' -> Pattern.DOTALL;
                case 'u' -> Pattern.UNICODE_CASE;
                case 'U' -> Pattern.UNICODE_CHARACTER_CLASS;
                default -> 0;
            };
            if (flag == 0) break;
            flags |= flag;
        }
        return Pattern.compile(expression.toString(), flags);
    }
}
