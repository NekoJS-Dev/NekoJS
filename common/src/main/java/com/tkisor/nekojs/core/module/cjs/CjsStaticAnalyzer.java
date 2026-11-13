package com.tkisor.nekojs.core.module.cjs;

import com.tkisor.nekojs.core.module.esm.NekoEsmLexer;
import com.tkisor.nekojs.core.module.esm.NekoEsmToken;
import com.tkisor.nekojs.core.module.esm.NekoEsmTokenKind;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Static analyzer for CommonJS modules.
 *
 * <p>It reuses {@link NekoEsmLexer} to tokenize with string, comment, regex and template awareness,
 * then scans the token stream for the key CommonJS semantics:
 * <ul>
 *   <li>{@code require('literal')} calls become static dependencies (member access such as
 *       {@code x.require(...)} and non-literal arguments are skipped);</li>
 *   <li>whole reassignment through {@code module.exports = ...}, and member assignment through
 *       {@code exports.xxx = ...} / {@code module.exports.xxx = ...};</li>
 *   <li>the {@code __esModule} interop marker (Babel/TS compiler output);</li>
 *   <li>require shadowed by a local declaration (function parameter,
 *       {@code let/const/var require}, {@code require = ...}), which marks the static dependencies
 *       as unreliable.</li>
 * </ul>
 *
 * <p>Limits, as a conservative approximation rather than full AST semantics: assignments inside
 * conditional branches are not checked for reachability, dynamic require (non-literal) is not
 * extracted, and arrow function parameters that shadow require are not detected. When precise
 * semantics matter, the runtime behavior is authoritative.
 */
public final class CjsStaticAnalyzer {

    private CjsStaticAnalyzer() {}

    /** Analyzes CommonJS source; a {@code null} source is treated as an empty module. */
    public static CjsModuleRecord analyze(String source) {
        if (source == null || source.isEmpty()) {
            return CjsModuleRecord.EMPTY;
        }
        List<NekoEsmToken> tokens = new NekoEsmLexer(source).tokenize();

        Set<String> dependencies = new LinkedHashSet<>();
        boolean assignsModuleExports = false;
        boolean assignsExportsMember = false;
        boolean hasEsmInteropMarker = false;
        boolean shadowedRequire = false;

        for (int i = 0; i < tokens.size(); i++) {
            NekoEsmToken t = tokens.get(i);
            if (t.kind() != NekoEsmTokenKind.IDENTIFIER) continue;

            if (t.text("require")) {
                if (isMemberAccess(tokens, i)) continue;          // x.require / x?.require
                if (isShadowingDeclaration(tokens, i)) {
                    shadowedRequire = true;
                    continue;
                }
                NekoEsmToken paren = peek(tokens, i + 1);
                NekoEsmToken arg = peek(tokens, i + 2);
                if (paren != null && paren.text("(")
                        && arg != null && arg.kind() == NekoEsmTokenKind.STRING) {
                    dependencies.add(arg.value());
                }
                continue;
            }

            if (t.text("module")) {
                NekoEsmToken dot = peek(tokens, i + 1);
                NekoEsmToken exportsTok = peek(tokens, i + 2);
                if (dot == null || !dot.text(".") || exportsTok == null || !exportsTok.identifier("exports")) {
                    continue;
                }
                NekoEsmToken after = peek(tokens, i + 3);
                if (after == null) continue;
                if (after.text(".")) {
                    assignsExportsMember = true;
                    NekoEsmToken member = peek(tokens, i + 4);
                    if (member != null && member.identifier("__esModule") && isTrueAssignment(tokens, i + 5)) {
                        hasEsmInteropMarker = true;
                    }
                } else if (isAssignmentOperator(after)) {
                    assignsModuleExports = true;
                }
                continue;
            }

            if (t.text("exports")) {
                NekoEsmToken dot = peek(tokens, i + 1);
                NekoEsmToken member = peek(tokens, i + 2);
                if (dot == null || !dot.text(".") || member == null || member.kind() != NekoEsmTokenKind.IDENTIFIER) {
                    continue;
                }
                assignsExportsMember = true;
                if (member.text("__esModule") && isTrueAssignment(tokens, i + 3)) {
                    hasEsmInteropMarker = true;
                }
            }
        }

        return new CjsModuleRecord(
                shadowedRequire ? List.of() : List.copyOf(dependencies),
                assignsModuleExports,
                assignsExportsMember,
                hasEsmInteropMarker,
                shadowedRequire);
    }

    /** The previous token is {@code .} or {@code ?.}, so this is member access, not a module require reference. */
    private static boolean isMemberAccess(List<NekoEsmToken> tokens, int index) {
        NekoEsmToken prev = peek(tokens, index - 1);
        return prev != null && (prev.text(".") || prev.text("?."));
    }

    /**
     * The common patterns that shadow require with a local declaration:
     * <ul>
     *   <li>{@code let/const/var require} (declaration);</li>
     *   <li>{@code require = ...} or a compound assignment (assignment target, whatever the previous
     *       token is);</li>
     *   <li>{@code function name(require, ...)} or {@code function (require)} (function parameter).
     * </ul>
     */
    private static boolean isShadowingDeclaration(List<NekoEsmToken> tokens, int index) {
        NekoEsmToken prev = peek(tokens, index - 1);
        if (prev != null) {
            if (prev.identifier("let") || prev.identifier("const") || prev.identifier("var")) {
                return true;
            }
            // Function parameter: `function (` or `function name(` directly before require
            if (prev.text("(") || prev.text(",")) {
                return isInsideFunctionParamList(tokens, index);
            }
        }
        // Assignment target `require = ...`: require may be the first token of the file, so prev is
        // null here and this check stands on its own
        return isAssignmentOperator(peek(tokens, index + 1));
    }

    /**
     * An interop marker assignment of the form {@code = true} or {@code = !0}; any other
     * right-hand value does not count.
     */
    private static boolean isTrueAssignment(List<NekoEsmToken> tokens, int eqIndex) {
        NekoEsmToken eq = peek(tokens, eqIndex);
        NekoEsmToken value = peek(tokens, eqIndex + 1);
        if (eq == null || !eq.text("=")) return false;
        if (value != null && value.identifier("true")) return true;
        // `!0` is common in minified Babel output
        NekoEsmToken not = peek(tokens, eqIndex + 1);
        NekoEsmToken zero = peek(tokens, eqIndex + 2);
        return not != null && not.text("!") && zero != null && zero.text("0");
    }

    /**
     * Looks backwards from the require position for the nearest function keyword with no closing
     * parenthesis in between, meaning require is inside the parameter list.
     */
    private static boolean isInsideFunctionParamList(List<NekoEsmToken> tokens, int index) {
        for (int j = index - 1; j >= 0; j--) {
            NekoEsmToken t = tokens.get(j);
            if (t.text(")")) return false;   // Brackets already closed, so this is not a parameter list
            if (t.identifier("function")) return true;
        }
        return false;
    }

    private static boolean isAssignmentOperator(NekoEsmToken token) {
        if (token == null || token.kind() != NekoEsmTokenKind.PUNCTUATOR) return false;
        String text = token.text();
        return text.equals("=") || text.equals("+=") || text.equals("-=") || text.equals("*=")
                || text.equals("/=") || text.equals("%=") || text.equals("||=") || text.equals("&&=")
                || text.equals("??=");
    }

    private static NekoEsmToken peek(List<NekoEsmToken> tokens, int index) {
        if (index < 0 || index >= tokens.size()) return null;
        NekoEsmToken t = tokens.get(index);
        return t.kind() == NekoEsmTokenKind.EOF ? null : t;
    }
}
