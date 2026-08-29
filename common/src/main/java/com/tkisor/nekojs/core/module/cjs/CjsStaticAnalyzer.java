package com.tkisor.nekojs.core.module.cjs;

import com.tkisor.nekojs.core.module.esm.NekoEsmLexer;
import com.tkisor.nekojs.core.module.esm.NekoEsmToken;
import com.tkisor.nekojs.core.module.esm.NekoEsmTokenKind;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CJS 静态分析器（第二阶段：Java-backed CJS 基础设施的一部分）。
 *
 * <p>复用 {@link NekoEsmLexer} 做字符串/注释/正则/模板感知的 token 化，然后扫描 token 流
 * 识别 CJS 关键语义：
 * <ul>
 *   <li>{@code require('literal')} 调用 → 静态依赖（跳过成员访问 {@code x.require(...)} 与
 *       非字面量参数）；</li>
 *   <li>{@code module.exports = ...} 整体重赋值、{@code exports.xxx = ...} /
 *       {@code module.exports.xxx = ...} 成员赋值；</li>
 *   <li>{@code __esModule} 互操作标记（Babel/TS 编译产物）；</li>
 *   <li>require 被局部声明遮蔽（函数参数、{@code let/const/var require}、{@code require = ...}）
 *       → 静态依赖标记为不可靠。</li>
 * </ul>
 *
 * <p>限制（保守近似，不追求完整 AST 语义）：条件分支内赋值不区分可达性；动态 require
 * （非字面量）不提取；箭头函数参数影子 require 不检测。需要精确语义时以执行期为准。
 */
public final class CjsStaticAnalyzer {

    private CjsStaticAnalyzer() {}

    /** 分析 CJS 源码；{@code null} 源码按空模块处理。 */
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

    /** 前一个 token 是 {@code .} 或 {@code ?.} → 成员访问，不是模块 require 引用。 */
    private static boolean isMemberAccess(List<NekoEsmToken> tokens, int index) {
        NekoEsmToken prev = peek(tokens, index - 1);
        return prev != null && (prev.text(".") || prev.text("?."));
    }

    /**
     * require 被局部声明遮蔽的常见模式：
     * <ul>
     *   <li>{@code let/const/var require}（声明）；</li>
     *   <li>{@code require = ...} / 复合赋值（赋值目标，与前置 token 无关）；</li>
     *   <li>{@code function name(require, ...)} 或 {@code function (require)}（函数参数）。
     * </ul>
     */
    private static boolean isShadowingDeclaration(List<NekoEsmToken> tokens, int index) {
        NekoEsmToken prev = peek(tokens, index - 1);
        if (prev != null) {
            if (prev.identifier("let") || prev.identifier("const") || prev.identifier("var")) {
                return true;
            }
            // 函数参数：function 后跟 (require 或 function name(require
            if (prev.text("(") || prev.text(",")) {
                return isInsideFunctionParamList(tokens, index);
            }
        }
        // 赋值目标 require = ...：require 可能是文件首个 token（prev 为 null），独立判断
        return isAssignmentOperator(peek(tokens, index + 1));
    }

    /** {@code = true}（或 {@code = !0}）形式的互操作标记赋值；其它右侧值不算。 */
    private static boolean isTrueAssignment(List<NekoEsmToken> tokens, int eqIndex) {
        NekoEsmToken eq = peek(tokens, eqIndex);
        NekoEsmToken value = peek(tokens, eqIndex + 1);
        if (eq == null || !eq.text("=")) return false;
        if (value != null && value.identifier("true")) return true;
        // Babel 压缩产物常见 !0
        NekoEsmToken not = peek(tokens, eqIndex + 1);
        NekoEsmToken zero = peek(tokens, eqIndex + 2);
        return not != null && not.text("!") && zero != null && zero.text("0");
    }

    /** 从 require 位置向前找最近的 function 关键字，且中间无右括号闭合（即处于参数列表内）。 */
    private static boolean isInsideFunctionParamList(List<NekoEsmToken> tokens, int index) {
        for (int j = index - 1; j >= 0; j--) {
            NekoEsmToken t = tokens.get(j);
            if (t.text(")")) return false;   // 已闭合的括号，不是参数列表
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
