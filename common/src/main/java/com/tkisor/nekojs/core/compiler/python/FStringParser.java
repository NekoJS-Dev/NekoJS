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

    /** 匹配 "python (parse|lex) error at line L, col C: " 前缀，用于换算相对坐标。 */
    private static final java.util.regex.Pattern ERROR_POSITION =
            java.util.regex.Pattern.compile("^python (?:parse|lex) error at line (\\d+), col (\\d+): ");

    private FStringParser() {}

    /**
     * @param baseLine FSTRING 记号所在源码行（1-based）
     * @param baseCol  f-string 首个内容字符的源码列（1-based；记号列指向引号，单引号串为 col+1，
     *                 三引号串差 2 列——可接受，行号总是准确的）
     */
    static PythonNode.FString parse(String raw, int baseLine, int baseCol) {
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
                PythonNode expr = parseInterpolation(exprText, baseLine, baseCol, start);
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

    /**
     * 把插值表达式重新词法+语法分析。解析错误原本以片段内相对坐标报告（f"{1 +}" 报 col 4），
     * 这里用 FSTRING 记号的行/列加上片段内偏移换算回源码坐标（首个物理行的错误按
     * baseCol + offset + 相对列 修正；跨行的错误行号累加、列号保持相对值）。
     *
     * @param offset 片段首字符在 f-string 原文内的 0-based 下标
     */
    private static PythonNode parseInterpolation(String exprText, int baseLine, int baseCol, int offset) {
        if (exprText.isEmpty()) {
            throw new IllegalArgumentException(
                    "python f-string: empty '{}' interpolation (line " + baseLine + ", col " + (baseCol + offset) + ")");
        }
        try {
            List<PythonToken> toks = new PythonLexer(exprText).tokenize();
            return new PythonParser(toks).parseTest();
        } catch (IllegalArgumentException e) {
            throw reposition(e, baseLine, baseCol, offset);
        }
    }

    private static IllegalArgumentException reposition(IllegalArgumentException e, int baseLine, int baseCol, int offset) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        int relLine = 1, relCol = 1;
        String rest = msg;
        java.util.regex.Matcher mt = ERROR_POSITION.matcher(msg);
        if (mt.find()) {
            relLine = Integer.parseInt(mt.group(1));
            relCol = Integer.parseInt(mt.group(2));
            rest = msg.substring(mt.end());
        }
        int line = baseLine + relLine - 1;
        int col = relLine == 1 ? baseCol + offset + relCol - 1 : relCol;
        return new IllegalArgumentException("python parse error at line " + line + ", col " + col
                + " (in f-string interpolation): " + rest, e);
    }
}
