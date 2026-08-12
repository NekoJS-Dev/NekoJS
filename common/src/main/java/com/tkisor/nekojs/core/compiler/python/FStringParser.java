package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits an f-string's raw inner text into literal ({@link PythonNode.StrLit}) and interpolation
 * (expression) parts, producing a {@link PythonNode.FString}. Each {@code {expr}} interpolation
 * is re-lexed+parsed as a Python expression via {@link PythonLexer}/{@link PythonParser}.
 *
 * <p>Format specs ({@code {x:.2f}}) and conversions ({@code {x!r}}) are captured and lowered to a
 * runtime {@code __nekoFmt(value, spec, conv)} call (see PythonEmitter); plain {@code {expr}}
 * interpolations emit the value directly. {@code {{}}}/{@code {}}} produce literal braces.
 */
final class FStringParser {

    private FStringParser() {}

    static PythonNode.FString parse(String raw) {
        List<PythonNode> parts = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        int len = raw.length();
        while (i < len) {
            char c = raw.charAt(i);
            if (c == '{' && i + 1 < len && raw.charAt(i + 1) == '{') { lit.append('{'); i += 2; continue; }
            if (c == '}' && i + 1 < len && raw.charAt(i + 1) == '}') { lit.append('}'); i += 2; continue; }
            if (c == '{') {
                flush(parts, lit);
                i++; // skip '{'
                int depth = 1;
                int start = i;
                // read the expression until depth-0 '}', or ':'/'!' (format/conversion) at depth 1,
                // skipping over quoted string literals so a ':' or '}' inside one doesn't terminate.
                while (i < len) {
                    char ch = raw.charAt(i);
                    if (ch == '\'' || ch == '"') {
                        char q = ch;
                        i++;
                        while (i < len) {
                            char c2 = raw.charAt(i);
                            if (c2 == '\\' && i + 1 < len) { i += 2; continue; }
                            if (c2 == q) { i++; break; }
                            i++;
                        }
                        continue;
                    }
                    if (ch == '{') { depth++; i++; }
                    else if (ch == '}') { depth--; if (depth == 0) break; i++; }
                    else if (ch == ':' && depth == 1) break;
                    else if (ch == '!' && depth == 1 && i + 2 < len && raw.charAt(i + 2) == '}') break; // conversion
                    else i++;
                }
                String exprText = raw.substring(start, i).trim();
                // optional conversion (!r / !s / !a) then optional format spec (:…) up to the '}'
                String conv = null;
                String spec = null;
                if (i < len && raw.charAt(i) == '!' && i + 1 < len
                        && (raw.charAt(i + 1) == 'r' || raw.charAt(i + 1) == 's' || raw.charAt(i + 1) == 'a')) {
                    conv = String.valueOf(raw.charAt(i + 1));
                    i += 2;
                }
                if (i < len && raw.charAt(i) == ':') {
                    int specStart = ++i;
                    while (i < len && raw.charAt(i) != '}') i++;   // spec is a literal (no nested fields in v1)
                    spec = raw.substring(specStart, i);
                }
                if (i < len && raw.charAt(i) == '}') i++; // consume '}'
                PythonNode expr = parseInterpolation(exprText);
                parts.add((spec != null || conv != null)
                        ? new PythonNode.Formatted(expr, spec, conv)
                        : expr);
                continue;
            }
            lit.append(c);
            i++;
        }
        flush(parts, lit);
        return new PythonNode.FString(parts);
    }

    private static void flush(List<PythonNode> parts, StringBuilder lit) {
        if (!lit.isEmpty()) {
            parts.add(new PythonNode.StrLit(lit.toString()));
            lit.setLength(0);
        }
    }

    private static PythonNode parseInterpolation(String exprText) {
        if (exprText.isEmpty()) {
            throw new IllegalArgumentException("python f-string: empty '{}' interpolation");
        }
        List<PythonToken> toks = new PythonLexer(exprText).tokenize();
        return new PythonParser(toks).parseTest();
    }
}
