package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode.Param;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits JavaScript source from a {@link PythonNode} AST. Compound expressions self-parenthesize
 * so the emitted text re-parses with correct precedence without a minimal-parenthesis pass.
 *
 * <p>v1 mappings: Python functions use hoisted {@code function}; names use {@code var} (always
 * redeclarable); {@code range/len/print} map to JS idioms; {@code //} → {@code Math.floor(a/b)};
 * dict → object literal; tuple/list → array; set → {@code new Set([...])}; f-string → template
 * literal.
 */
public final class PythonEmitter {

    /**
     * The runtime helper that implements the common Python format-spec mini-language for
     * {@code f"{x:spec}"}. Emitted at the top of a module only when it actually uses a format spec
     * or conversion (detected via {@link #scanHelpers}). Handles: {@code .Nf}, width, alignment
     * ({@code < > ^ =}), zero-fill ({@code 0}), thousands ({@code ,}), precision (incl. string
     * truncation), and types {@code f e E x X o b % d c}.
     */
    private static final String[] FMT_HELPER = {
            "const __nekoFmt = function (__v, __spec, __conv) {",
            "  function rep(c, n) { var r = ''; for (var k = 0; k < n; k++) r += c; return r; }",
            "  function num(x) { return typeof x === 'number' ? x : parseFloat(x); }",
            "  if (__conv === 'r' || __conv === 'a') __v = JSON.stringify(__v);",
            "  else if (__conv === 's') __v = (__v === null) ? 'None' : String(__v);",
            "  else __v = (__v === null || __v === undefined) ? 'None' : String(__v);",
            "  if (!__spec) return __v;",
            "  var s = __spec, fill = ' ', align = null;",
            "  if (s.length >= 2 && (s.charAt(1) === '<' || s.charAt(1) === '>' || s.charAt(1) === '^' || s.charAt(1) === '=')) { fill = s.charAt(0); align = s.charAt(1); s = s.substring(2); }",
            "  else if (s.length >= 1 && (s.charAt(0) === '<' || s.charAt(0) === '>' || s.charAt(0) === '^' || s.charAt(0) === '=')) { align = s.charAt(0); s = s.substring(1); }",
            "  if (s.length >= 1 && s.charAt(0) === '0') { if (align === null) align = '='; if (fill === ' ') fill = '0'; s = s.substring(1); }",
            "  var width = 0, j = 0;",
            "  while (j < s.length && s.charAt(j) >= '0' && s.charAt(j) <= '9') { width = width * 10 + (s.charCodeAt(j) - 48); j++; }",
            "  s = s.substring(j);",
            "  var comma = s.length >= 1 && s.charAt(0) === ','; if (comma) s = s.substring(1);",
            "  var prec = -1;",
            "  if (s.length >= 1 && s.charAt(0) === '.') { s = s.substring(1); var p = ''; while (s.length >= 1 && s.charAt(0) >= '0' && s.charAt(0) <= '9') { p += s.charAt(0); s = s.substring(1); } prec = p ? parseInt(p, 10) : 0; }",
            "  var type = s.length >= 1 ? s.charAt(0) : '';",
            "  var isNum = (typeof __v === 'number'), out;",
            "  if (type === 'f' || type === 'F') out = num(__v).toFixed(prec < 0 ? 6 : prec);",
            "  else if (type === 'e' || type === 'E') { out = num(__v).toExponential(prec < 0 ? 6 : prec); if (type === 'E') out = out.toUpperCase(); }",
            "  else if (type === 'x' || type === 'X' || type === 'o' || type === 'b') { var iv = Math.trunc(num(__v)); out = (iv < 0 ? '-' : '') + Math.abs(iv).toString(type === 'x' || type === 'X' ? 16 : type === 'o' ? 8 : 2); if (type === 'X') out = out.toUpperCase(); }",
            "  else if (type === '%') out = (num(__v) * 100).toFixed(prec < 0 ? 6 : prec) + '%';",
            "  else if (type === 'd') out = String(Math.trunc(num(__v)));",
            "  else if (type === 'c') out = String.fromCodePoint(Math.trunc(num(__v)));",
            "  else if (prec >= 0 && typeof __v === 'string') out = __v.substring(0, prec);",
            "  else out = String(__v);",
            "  if (comma) { var d = out.indexOf('.'); var ip = d < 0 ? out : out.substring(0, d); var dp = d < 0 ? '' : out.substring(d); var tmp = '', nn = ip.length; for (var k = 0; k < nn; k++) { if (k > 0 && (nn - k) % 3 === 0 && ip.charAt(k) >= '0' && ip.charAt(k) <= '9') tmp += ','; tmp += ip.charAt(k); } out = tmp + dp; }",
            "  if (out.length < width) {",
            "    var pad = width - out.length;",
            "    if (align === '<') out = out + rep(fill, pad);",
            "    else if (align === '^') { var h = Math.floor(pad / 2); out = rep(fill, h) + out + rep(fill, pad - h); }",
            "    else if (align === '=') { var sg = (out.charAt(0) === '-' || out.charAt(0) === '+') ? out.charAt(0) : ''; out = sg + rep(fill, pad) + (sg ? out.substring(1) : out); }",
            "    else if (align === '>' || ((align === null) && (isNum || type !== ''))) out = rep(fill, pad) + out;",
            "    else out = out + rep(fill, pad);",
            "  }",
            "  return out;",
            "};"
    };

    /**
     * Python 真值助手：空数组/空字典(对象字面量)/空 Map/空 Set 为假（JS 中对象与数组恒真）。
     * 用户类实例（constructor !== Object）保持恒真，对应 Python 未定义 {@code __bool__}/
     * {@code __len__} 时的默认语义。仅当模块用到需要真值判断的构造时发射（见 {@link #scanHelpers}）。
     */
    private static final String TRUTHY_HELPER =
            "const __nekoTruthy = function (__v) {"
            + " if (Array.isArray(__v)) return __v.length > 0;"
            + " if (__v instanceof Map || __v instanceof Set) return __v.size > 0;"
            + " if (typeof __v === 'object' && __v !== null && __v.constructor === Object) return Object.keys(__v).length > 0;"
            + " return Boolean(__v); };";

    /**
     * Python and/or 是值语义（返回操作数本身而非布尔）且以 Python 真值短路。右操作数包成
     * thunk 保持惰性（短路），左操作数作为实参只求值一次。
     */
    private static final String AND_HELPER =
            "const __nekoAnd = function (__a, __b) { return __nekoTruthy(__a) ? __b() : __a; };";
    private static final String OR_HELPER =
            "const __nekoOr = function (__a, __b) { return __nekoTruthy(__a) ? __a : __b(); };";

    /** Python 取模：结果符号跟随除数（JS {@code %} 跟随被除数，{@code -7 % 2} 得 -1）。 */
    private static final String MOD_HELPER =
            "const __nekoMod = function (__a, __b) { return ((__a % __b) + __b) % __b; };";

    /**
     * 序列重复：{@code [0] * 4} / {@code 'ab' * 3}（两个方向都合法）重复序列，负数/零次为空，
     * 其余情况退回数值乘法。JS 的 {@code *} 对字符串/数组是 NaN/0——静默错误，故一律经此助手。
     */
    private static final String MUL_HELPER =
            "const __nekoMul = function (__a, __b) {"
            + " if (Array.isArray(__a) && typeof __b === 'number') { var __r = []; var __n = Math.max(0, Math.trunc(__b)); for (var __i = 0; __i < __n; __i++) __r = __r.concat(__a); return __r; }"
            + " if (Array.isArray(__b) && typeof __a === 'number') { var __r2 = []; var __n2 = Math.max(0, Math.trunc(__a)); for (var __j = 0; __j < __n2; __j++) __r2 = __r2.concat(__b); return __r2; }"
            + " if (typeof __a === 'string' && typeof __b === 'number') return __b > 0 ? __a.repeat(Math.trunc(__b)) : '';"
            + " if (typeof __b === 'string' && typeof __a === 'number') return __a > 0 ? __b.repeat(Math.trunc(__a)) : '';"
            + " return __a * __b; };";

    /**
     * {@code in} 成员判断：dict(对象字面量)按自有键、Map/Set 按 {@code .has}、数组/字符串按
     * {@code .includes}。旧实现对 dict/set 发射 {@code .includes} —— 运行时 TypeError。
     */
    private static final String IN_HELPER =
            "const __nekoIn = function (__v, __c) {"
            + " if (__c instanceof Map || __c instanceof Set) return __c.has(__v);"
            + " if (Array.isArray(__c) || typeof __c === 'string') return __c.includes(__v);"
            + " if (typeof __c === 'object' && __c !== null) return Object.prototype.hasOwnProperty.call(__c, __v);"
            + " return false; };";

    /**
     * 可迭代归一：dict(对象字面量)迭代键、Map 迭代键，其余（数组/字符串/生成器/Set）原样透传。
     * {@code for k in d:} 旧实现发射 {@code for (var k of d)} —— 对普通对象是运行时 TypeError。
     */
    private static final String ITER_HELPER =
            "const __nekoIter = function (__v) {"
            + " if (__v instanceof Map) return Array.from(__v.keys());"
            + " if (typeof __v === 'object' && __v !== null && __v.constructor === Object) return Object.keys(__v);"
            + " return __v; };";

    /** {@code len()}：数组/字符串取 length，Map/Set 取 size，dict(对象字面量)取键数。 */
    private static final String LEN_HELPER =
            "const __nekoLen = function (__v) {"
            + " if (__v instanceof Map || __v instanceof Set) return __v.size;"
            + " if (typeof __v === 'object' && __v !== null && __v.constructor === Object) return Object.keys(__v).length;"
            + " return __v.length; };";

    /**
     * 内建异常匹配助手：先按类匹配（prelude 类层级），再按 {@code Error.name} 兜底——这样
     * JS 原生错误（如 {@code null.foo} 抛的原生 TypeError）仍能被 {@code except TypeError} 捕获。
     */
    private static final String EXCIS_HELPER =
            "const __nekoExcIs = function (__e, __t) {"
            + " return (__e instanceof __t) || (__e instanceof Error && __e.name === __t.name); };";

    /**
     * 内建异常 prelude：{@code class X extends Error} 家族，让 {@code raise ValueError('boom')}
     * 发射 {@code throw new ValueError("boom")}（旧实现发射裸 {@code ValueError} 引用 → 运行时
     * ReferenceError）、{@code except ValueError} 按 {@code instanceof ValueError} 精确匹配。
     * 基类先于派生类声明；仅当模块引用了任一内建异常名（或含 assert）时发射。
     */
    private static final String[] EXC_PRELUDE = {
            "class Exception extends Error {}",
            "class ArithmeticError extends Exception {}",
            "class LookupError extends Exception {}",
            "class RuntimeError extends Exception {}",
            "class ValueError extends Exception {}",
            "class TypeError extends Exception {}",
            "class AttributeError extends Exception {}",
            "class NameError extends Exception {}",
            "class AssertionError extends Exception {}",
            "class StopIteration extends Exception {}",
            "class ImportError extends Exception {}",
            "class OSError extends Exception {}",
            "class EOFError extends Exception {}",
            "class MemoryError extends Exception {}",
            "class OverflowError extends ArithmeticError {}",
            "class ZeroDivisionError extends ArithmeticError {}",
            "class KeyError extends LookupError {}",
            "class IndexError extends LookupError {}",
            "class NotImplementedError extends RuntimeError {}",
            "class RecursionError extends RuntimeError {}",
            // JS 子类实例的 .name 默认继承 Error.prototype.name（"Error"）——按类名重设，
            // 使 e.name / e.message 与 Python 的 str(e) 约定一致，也让 __nekoExcIs 的原生错误
            // name 兜底匹配成立。
            "[Exception, ValueError, TypeError, AttributeError, NameError, AssertionError, StopIteration,"
            + " ImportError, OSError, EOFError, MemoryError, OverflowError, ZeroDivisionError, ArithmeticError,"
            + " LookupError, KeyError, IndexError, NotImplementedError, RecursionError]"
            + " .forEach(function (C) { C.prototype.name = C.name; })",
    };

    /**
     * dict 方法劫持的 worst-offender 名单：这些名字既是 dict 方法也是常见用户方法名，
     * 需要额外的接收者防护（见 {@link #rtDispatch}）。
     */
    private static final java.util.Set<String> DICT_METHODS = java.util.Set.of(
            "get", "keys", "values", "items", "update", "pop");

    private final StringBuilder out = new StringBuilder();
    private int indent = 0;
    private int tempCounter = 0;
    private int jsLine = 0;   // 0-based number of the next line to be emitted
    private final List<int[]> mappings = new ArrayList<>();   // {generatedJsLine, originalPythonLine0}
    private final IdentityHashMap<PythonNode, Integer> srcLines;
    private boolean rewriteSelf = false;   // true inside a class method body (self → this)
    private final java.util.Set<String> classNames = new java.util.HashSet<>();   // for `new` on calls
    /** Top-level names defined by this module (def/class/assign), re-exported so .py files are importable. */
    private final java.util.Set<String> definedNames = new java.util.LinkedHashSet<>();
    /** Functions/methods/classes that declare {@code **kwargs} → accept a tagged trailing object at call sites. */
    private final java.util.Set<String> kwFunctions = new java.util.HashSet<>();
    private final java.util.Set<String> kwMethods = new java.util.HashSet<>();
    private final java.util.Set<String> kwClassNames = new java.util.HashSet<>();
    /** True when any f-string format spec / conversion is used → the __nekoFmt helper is emitted. */
    private boolean needsFmt = false;
    /** 按需发射的运行时助手开关（一次 AST 预扫描 {@link #scanHelpers} 收集，见 {@link #emitRuntimeHelpers}）。 */
    private boolean needsTruthy = false;   // if/while/not/and/or/bool/any/all/推导式过滤/三元/assert/match guard
    private boolean needsMod = false;      // %、%=、divmod
    private boolean needsMul = false;      // *、*=
    private boolean needsIn = false;       // in / not in
    private boolean needsIter = false;     // for 语句 / 推导式 for 子句
    private boolean needsLen = false;      // len()
    private boolean needsExc = false;      // 内建异常名被引用 / assert → 异常 prelude + __nekoExcIs
    /** 当前正在发射的语句的 Python 源码行（1-based；由 emitStmt 从 srcLines 维护），供 {@link #err} 附加位置。 */
    private int curLine = -1;
    /** The variable bound to the current exception in each enclosing except clause (top = innermost). */
    private final java.util.Deque<String> errStack = new java.util.ArrayDeque<>();
    /** For each enclosing loop: the else-flag var name (or null if the loop has no else). Top = innermost. */
    private final java.util.Deque<String> loopFlags = new java.util.LinkedList<>();

    public PythonEmitter(IdentityHashMap<PythonNode, Integer> srcLines) {
        this.srcLines = srcLines;
    }

    public String emit(PythonNode.Module module) {
        // Pass 1: collect top-level defined names so the module can re-export them (Python modules
        // expose all top-level bindings; this lets sibling .py files `from <this> import <name>`),
        // collect **kwargs-aware functions/methods/classes so call sites can route keyword args,
        // and scan for constructs that need a runtime helper (so each is emitted at most once).
        for (PythonNode stmt : module.body()) {
            collectDefinitions(stmt);
            collectKwAware(stmt);
            scanHelpers(stmt);
        }
        // Pass 2: ESM import declarations must precede all other statements; emit them first, each
        // mapped back to its Python source line. Module specifiers are relative to this file
        // (foo → ./foo, a.b.c → ./a/b/c); NekoModuleResolver probes .py / .js / index.* automatically.
        for (PythonNode stmt : module.body()) {
            Integer stmtLine = srcLines.get(stmt);
            if (stmtLine != null) curLine = stmtLine;   // import 阶段的报错同样带上源码行
            if (stmt instanceof PythonNode.Import imp) {
                recordMapping(stmt);
                for (PythonNode.Spec s : imp.specs()) line(esmNamespaceImport(s));
            } else if (stmt instanceof PythonNode.ImportFrom impf) {
                // 魔法 import：`from nekojs import *`（或具名）是给 IDE/pyright 看的类型桩入口，
                // 运行时无意义——剥离之（不 recordMapping、不 line，source map 零影响）。
                if ("nekojs".equals(impf.module())) continue;
                if (impf.star()) throw err("python 'from X import *' is not supported");
                recordMapping(stmt);
                line(esmNamedImport(impf));
            }
        }
        // Pass 3: runtime helpers (only the ones this module needs), then the remaining statements.
        emitRuntimeHelpers();
        for (PythonNode stmt : module.body()) {
            if (stmt instanceof PythonNode.Import || stmt instanceof PythonNode.ImportFrom) continue;
            emitStmt(stmt);
        }
        // Pass 4: emit the export block. Has no Python source line → no source-map entry. Skipped when
        // the module defines no names (e.g. a bare expression script stays plain JS, not ESM).
        if (!definedNames.isEmpty()) emitExportBlock();
        return out.toString();
    }

    /**
     * 按需发射运行时助手与异常 prelude：每个至多一次、先于所有语句（无 source map 行，与
     * {@link #FMT_HELPER} 注入方式一致）。开关由 {@link #scanHelpers} 预扫描设置。
     */
    private void emitRuntimeHelpers() {
        if (needsFmt) { for (String l : FMT_HELPER) line0(l); }
        if (needsTruthy) { line0(TRUTHY_HELPER); line0(AND_HELPER); line0(OR_HELPER); }
        if (needsMod) line0(MOD_HELPER);
        if (needsMul) line0(MUL_HELPER);
        if (needsIn) line0(IN_HELPER);
        if (needsIter) line0(ITER_HELPER);
        if (needsLen) line0(LEN_HELPER);
        if (needsExc) { for (String l : EXC_PRELUDE) line0(l); line0(EXCIS_HELPER); }
    }

    /** (generatedJsLine, originalPythonLine0Based) pairs, one per statement's first emitted line. */
    public List<int[]> mappings() {
        return mappings;
    }

    // ---- statements ----

    private void emitStmt(PythonNode node) {
        // Record a statement-level mapping (the line about to be emitted ← its Python source line).
        if (!(node instanceof PythonNode.Pass)) recordMapping(node);
        // 记录当前语句的源码行（供 err() 附加位置）；嵌套语句由递归的 emitStmt 自行保存/恢复。
        Integer ln = srcLines.get(node);
        int prevLine = curLine;
        if (ln != null) curLine = ln;
        switch (node) {
            case PythonNode.Module m -> throw err("nested module");
            case PythonNode.FunctionDef f -> {
                if (hasKwargs(f.params())) {
                    // **kwargs → empty signature; a prologue reconstructs binding from `arguments`.
                    line("function" + (f.isGenerator() ? "* " : " ") + f.name() + "() {");
                    emitKwPrologue(f.params(), false);
                } else {
                    line("function" + (f.isGenerator() ? "* " : " ") + f.name() + "(" + emitParams(f.params()) + ") {");
                }
                block(f.body());
                line("}");
                applyDecorators(f.name(), f.decorators());
            }
            case PythonNode.ClassDef c -> emitClass(c);
            case PythonNode.With w -> emitWith(w, 0);
            case PythonNode.Match m -> emitMatch(m);
            case PythonNode.Try t -> {
                boolean hasElse = !t.elseBody().isEmpty();
                boolean hasFinally = !t.finallyBody().isEmpty();
                // else runs only if the try body raised nothing, so track success with a flag; it must
                // also run BEFORE finally and outside the except handlers (an else-body exception is not
                // caught by the same excepts). When both else and finally are present, wrap so finally
                // still runs after the else.
                String elseFlag = hasElse ? ("__nekoOk" + (tempCounter++)) : null;
                if (hasElse) line("var " + elseFlag + " = true;");
                boolean outerWrap = hasElse && hasFinally;
                if (outerWrap) { line("try {"); indent++; }
                line("try {");
                block(t.body());
                List<PythonNode.ExceptClause> excepts = t.excepts();
                if (!excepts.isEmpty()) {
                    boolean typed = excepts.size() > 1 || !excepts.get(0).types().isEmpty();
                    if (!typed) {
                        // bare single except → plain catch (no type check)
                        PythonNode.ExceptClause only = excepts.get(0);
                        String bound = only.name() != null ? only.name() : "__nekoErr";
                        line("} catch (" + bound + ") {");
                        if (elseFlag != null) line(elseFlag + " = false;");
                        errStack.push(bound);
                        block(only.body());
                        errStack.pop();
                    } else {
                        // typed excepts → one catch + instanceof chain; unmatched errors rethrow.
                        line("} catch (__nekoErr) {");
                        indent++;
                        if (elseFlag != null) line(elseFlag + " = false;");
                        errStack.push("__nekoErr");
                        boolean isFirst = true;
                        boolean hasBare = false;
                        for (PythonNode.ExceptClause c : excepts) {
                            if (c.types().isEmpty()) {   // bare except — parser enforces it is last
                                hasBare = true;
                                line(isFirst ? "if (true) {" : "} else {");
                            } else {
                                line((isFirst ? "if (" : "} else if (")
                                        + instanceOfCond(c.types(), "__nekoErr") + ") {");
                            }
                            indent++;
                            if (c.name() != null && !c.name().equals("__nekoErr")) {
                                line("var " + c.name() + " = __nekoErr;");
                            }
                            block(c.body());
                            indent--;
                            isFirst = false;
                        }
                        if (hasBare) {
                            line("}");
                        } else {
                            line("} else {");
                            indent++;
                            line("throw __nekoErr;");
                            indent--;
                            line("}");
                        }
                        indent--;
                        errStack.pop();
                    }
                }
                if (hasFinally && !outerWrap) {
                    line("} finally {");
                    block(t.finallyBody());
                }
                line("}");
                if (hasElse) {
                    line("if (" + elseFlag + ") {");
                    block(t.elseBody());
                    line("}");
                }
                if (outerWrap) {
                    indent--;
                    line("} finally {");
                    indent++;
                    block(t.finallyBody());
                    indent--;
                    line("}");
                }
            }
            case PythonNode.If i -> writeIf(i, true);
            case PythonNode.For f -> {
                String flag = f.elseBody().isEmpty() ? null : ("__nekoBrk" + (tempCounter++));
                if (flag != null) line("var " + flag + " = true;");   // stays true unless the body `break`s
                // dict/Map 经 __nekoIter 归一为键迭代（其余可迭代对象原样透传）
                line("for (var " + emitTarget(f.target()) + " of __nekoIter(" + emitExpr(f.iter()) + ")) {");
                loopFlags.push(flag);
                block(f.body());
                loopFlags.pop();
                line("}");
                if (flag != null) {
                    line("if (" + flag + ") {");
                    block(f.elseBody());
                    line("}");
                }
            }
            case PythonNode.While w -> {
                String flag = w.elseBody().isEmpty() ? null : ("__nekoBrk" + (tempCounter++));
                if (flag != null) line("var " + flag + " = true;");
                line("while (__nekoTruthy(" + emitExpr(w.cond()) + ")) {");
                loopFlags.push(flag);
                block(w.body());
                loopFlags.pop();
                line("}");
                if (flag != null) {
                    line("if (" + flag + ") {");
                    block(w.elseBody());
                    line("}");
                }
            }
            case PythonNode.Return r -> line(r.value() == null ? "return;" : "return " + emitExpr(r.value()) + ";");
            case PythonNode.Raise r -> {
                if (r.exc() == null) {
                    if (errStack.isEmpty()) {
                        throw err("python bare 'raise' is only valid inside an except clause");
                    }
                    line("throw " + errStack.peek() + ";");   // re-raise the current exception
                } else if (r.exc() instanceof PythonNode.Name rn && BUILTIN_EXCEPTIONS.contains(rn.id())) {
                    line("throw new " + rn.id() + "();");   // raise ValueError → throw new ValueError()
                } else {
                    // raise ValueError('x') → throw new ValueError("x")（内建异常类由 prelude 提供）
                    line("throw " + emitExpr(r.exc()) + ";");
                }
            }
            case PythonNode.Assert a -> {
                String thrown = a.msg() != null ? emitExpr(a.msg()) : "\"AssertionError\"";
                line("if (!(__nekoTruthy(" + emitExpr(a.cond()) + "))) throw new AssertionError(" + thrown + ");");
            }
            case PythonNode.Del d -> {
                for (PythonNode t : d.targets()) {
                    if (t instanceof PythonNode.Name n) {
                        // `delete x;` 在 ESM 严格模式下是语法错误，且 JS 无法解除 var 绑定
                        // （Python 的 del x 语义）→ 编译期报错；仅支持 del d[k] / del obj.attr。
                        throw err("python 'del " + n.id()
                                + "' is not supported (JS cannot unbind a name; use del d[key] / del obj.attr)");
                    }
                    line("delete " + emitExpr(t) + ";");
                }
            }
            case PythonNode.Yield y -> line(y.from()
                    ? "yield* " + emitExpr(y.value()) + ";"
                    : "yield " + (y.value() != null ? emitExpr(y.value()) : "") + ";");
            case PythonNode.Break b -> {
                String f = loopFlags.peek();
                if (f != null) line(f + " = false;");   // mark the nearest loop as broken (skips its else)
                line("break;");
            }
            case PythonNode.Continue c -> line("continue;");
            case PythonNode.Pass p -> { /* emit nothing */ }
            case PythonNode.Assign a -> emitAssign(a);
            case PythonNode.AugAssign a -> emitAugAssign(a);
            case PythonNode.ExprStmt e -> line(emitExpr(e.expr()) + ";");
            case PythonNode.Import imp -> throw err(
                    "python 'import' is only supported at module top level");
            case PythonNode.ImportFrom imp -> throw err(
                    "python 'from ... import' is only supported at module top level");
            default -> throw err("unsupported statement: " + node.getClass().getSimpleName());
        }
        curLine = prevLine;
    }

    private void recordMapping(PythonNode node) {
        Integer py = srcLines.get(node);
        if (py != null) mappings.add(new int[]{jsLine, py - 1});
    }

    /** Collects top-level binding names (def/class/assign targets) for the module export block. */
    private void collectDefinitions(PythonNode stmt) {
        switch (stmt) {
            case PythonNode.FunctionDef f -> definedNames.add(f.name());
            case PythonNode.ClassDef c -> definedNames.add(c.name());
            case PythonNode.Assign a -> { for (PythonNode t : a.targets()) collectTargetNames(t); }
            default -> { }
        }
    }

    private void collectTargetNames(PythonNode target) {
        switch (target) {
            case PythonNode.Name n -> definedNames.add(n.id());
            case PythonNode.TupleLit t -> { for (PythonNode e : t.elements()) collectTargetNames(e); }
            default -> { }   // Attribute / Index targets mutate, they don't create new bindings
        }
    }

    /**
     * One pre-scan walk (same reflection traversal as the old {@code containsFormatted}) that sets
     * every on-demand runtime-helper flag: a {@code Formatted} f-string field or {@code format(x, s)}
     * needs {@code __nekoFmt}; conditions/boolean operators need Python truthiness; {@code %}/{@code *}
     * need the modulo/repetition helpers; {@code in} needs membership; for-statements and comprehension
     * {@code for} clauses need iterable normalization; {@code len()} needs the len helper; any builtin
     * exception name reference (or an {@code assert}, which raises AssertionError) needs the exception
     * prelude.
     */
    private void scanHelpers(Object o) {
        if (o == null) return;
        if (o instanceof PythonNode.Formatted) {
            needsFmt = true;
        } else if (o instanceof PythonNode.Name n) {
            if (BUILTIN_EXCEPTIONS.contains(n.id())) needsExc = true;
        } else if (o instanceof PythonNode.Call call && call.func() instanceof PythonNode.Name fn) {
            String id = fn.id();
            if ("format".equals(id) && call.args().size() == 2) needsFmt = true;
            if ("bool".equals(id) || "any".equals(id) || "all".equals(id)) needsTruthy = true;
            if ("len".equals(id)) needsLen = true;
            if ("divmod".equals(id)) needsMod = true;
        } else if (o instanceof PythonNode.Assert) {
            needsTruthy = true;   // assert 以 Python 真值判定条件
            needsExc = true;      // assert 失败 → new AssertionError(...)
        } else if (o instanceof PythonNode.If || o instanceof PythonNode.While || o instanceof PythonNode.Ternary
                || o instanceof PythonNode.IfComp) {
            needsTruthy = true;
        } else if (o instanceof PythonNode.Unary u && "not".equals(u.op())) {
            needsTruthy = true;
        } else if (o instanceof PythonNode.Binary b) {
            switch (b.op()) {
                case "and", "or" -> needsTruthy = true;
                case "%" -> needsMod = true;
                case "*" -> needsMul = true;
                default -> { }
            }
        } else if (o instanceof PythonNode.AugAssign ag) {
            if ("%=".equals(ag.op())) needsMod = true;
            else if ("*=".equals(ag.op())) needsMul = true;
        } else if (o instanceof PythonNode.Compare cmp && ("in".equals(cmp.op()) || "not in".equals(cmp.op()))) {
            needsIn = true;
        } else if (o instanceof PythonNode.For || o instanceof PythonNode.ForComp) {
            needsIter = true;
        } else if (o instanceof PythonNode.Match mt) {
            // match 的 case guard 以 Python 真值判定
            for (var mc : mt.cases()) if (mc.guard() != null) needsTruthy = true;
        }
        // 深度遍历 record 组件 / list 元素，继续收集标记（与具体分支判定互不干扰）。
        Class<?> c = o.getClass();
        if (c.isRecord()) {
            for (var rc : c.getRecordComponents()) {
                try {
                    scanHelpers(rc.getAccessor().invoke(o));
                } catch (ReflectiveOperationException ignored) { }
            }
            return;
        }
        if (o instanceof java.util.List<?> list) {
            for (var e : list) scanHelpers(e);
        }
    }

    /** True if a parameter list declares {@code **kwargs} (the only trigger for the kw-aware lowering). */
    private static boolean hasKwargs(List<Param> params) {
        for (Param p : params) if (p.kwDict()) return true;
        return false;
    }

    /** Pre-pass: record every function/method/class that declares {@code **kwargs} (for call-site routing). */
    private void collectKwAware(PythonNode node) {
        switch (node) {
            case PythonNode.Module m -> { for (PythonNode s : m.body()) collectKwAware(s); }
            case PythonNode.FunctionDef f -> {
                if (hasKwargs(f.params())) kwFunctions.add(f.name());
                for (PythonNode s : f.body()) collectKwAware(s);   // nested defs
            }
            case PythonNode.ClassDef c -> {
                for (PythonNode member : c.body()) {
                    if (member instanceof PythonNode.FunctionDef m) {
                        if (hasKwargs(m.params())) {
                            kwMethods.add(m.name());
                            if ("__init__".equals(m.name())) kwClassNames.add(c.name());
                        }
                        collectKwAware(m);
                    } else collectKwAware(member);
                }
            }
            case PythonNode.If i -> { for (PythonNode s : i.thenBody()) collectKwAware(s); for (PythonNode s : i.elseBody()) collectKwAware(s); }
            case PythonNode.For f -> { for (PythonNode s : f.body()) collectKwAware(s); }
            case PythonNode.While w -> { for (PythonNode s : w.body()) collectKwAware(s); }
            case PythonNode.Try t -> {
                for (PythonNode s : t.body()) collectKwAware(s);
                for (var ex : t.excepts()) for (PythonNode s : ex.body()) collectKwAware(s);
                for (PythonNode s : t.finallyBody()) collectKwAware(s);
            }
            case PythonNode.With w -> { for (PythonNode s : w.body()) collectKwAware(s); }
            default -> { }
        }
    }

    /**
     * Emits the prologue that reconstructs Python parameter binding from a JS {@code arguments}
     * object, used only by functions/methods that declare {@code **kwargs}. Call sites pass keyword
     * args as a single tagged trailing object {@code { name: value, ..., __nekoKw: true }}; this
     * prologue separates it from positional args, binds each named positional param (positional
     * beats keyword beats default), collects {@code *args}, and gathers the remaining keyword args
     * into the {@code **kwargs} dict (excluding names that bound to a positional param).
     *
     * @param skipFirst drop the leading positional param (the implicit {@code self}/{@code cls} of a method)
     */
    private void emitKwPrologue(List<Param> params, boolean skipFirst) {
        List<Param> positional = new ArrayList<>();
        Param starParam = null;
        Param kwParam = null;
        for (Param p : params) {
            if (p.kwDict()) kwParam = p;
            else if (p.starArg()) starParam = p;
            else positional.add(p);
        }
        int start = skipFirst ? 1 : 0;
        line("var __nekoLast = arguments.length > 0 ? arguments[arguments.length - 1] : undefined;");
        line("var __nekoHasKw = (typeof __nekoLast === \"object\" && __nekoLast !== null && __nekoLast.__nekoKw === true);");
        line("var __kw = __nekoHasKw ? __nekoLast : {};");
        line("var __posCount = arguments.length - (__nekoHasKw ? 1 : 0);");
        for (int i = start; i < positional.size(); i++) {
            Param p = positional.get(i);
            String def = p.defaultValue() != null ? emitExpr(p.defaultValue()) : "undefined";
            String pick = "(__posCount > " + (i - start) + ") ? arguments[" + (i - start) + "]"
                    + " : (\"" + p.name() + "\" in __kw ? __kw[\"" + p.name() + "\"] : " + def + ")";
            line("var " + p.name() + " = " + pick + ";");
        }
        if (starParam != null) {
            line("var " + starParam.name() + " = [];");
            line("for (var __i = " + (positional.size() - start) + "; __i < __posCount; __i++) "
                    + starParam.name() + ".push(arguments[__i]);");
        }
        if (kwParam != null) {
            StringBuilder excl = new StringBuilder(" __nekoK !== \"__nekoKw\"");
            for (int i = start; i < positional.size(); i++) excl.append(" && __nekoK !== \"").append(positional.get(i).name()).append("\"");
            line("var " + kwParam.name() + " = {};");
            line("for (var __nekoK in __kw) { if (" + excl + ") " + kwParam.name() + "[__nekoK] = __kw[__nekoK]; }");
        }
    }

    /** Builds the tagged trailing object carrying keyword args (+ any {@code **} spreads) at a call site. */
    private String kwObjectLiteral(Map<String, PythonNode> kwargs, List<PythonNode> kwSpreads) {
        StringBuilder sb = new StringBuilder("{ ");
        boolean first = true;
        for (PythonNode spread : kwSpreads) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("...").append(emitExpr(spread));
        }
        for (var e : kwargs.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(e.getKey()).append(": ").append(emitExpr(e.getValue()));
        }
        return sb.append(", __nekoKw: true }").toString();
    }

    /**
     * Builds the ESM import for one {@code import m[.sub][ as alias]} spec. Dots map to path
     * separators ({@code a.b.c} → {@code ./a/b/c}); the local binding is the alias or the leaf
     * segment, so {@code import utils} exposes a namespace accessed as {@code utils.x}.
     */
    private String esmNamespaceImport(PythonNode.Spec s) {
        String local = s.alias() != null ? s.alias() : lastSegment(s.name());
        return "import * as " + local + " from '" + moduleSpecifier(s.name()) + "';";
    }

    /** Builds the ESM named import for {@code from m[.sub] import a [as x], b}. */
    private String esmNamedImport(PythonNode.ImportFrom imp) {
        StringBuilder sb = new StringBuilder("import { ");
        List<PythonNode.Spec> specs = imp.specs();
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode.Spec s = specs.get(i);
            sb.append(s.name());
            if (s.alias() != null) sb.append(" as ").append(s.alias());
        }
        return sb.append(" } from '").append(moduleSpecifier(imp.module())).append("';").toString();
    }

    /** {@code foo} → {@code './foo'}; {@code a.b.c} → {@code './a/b/c'} (sibling-file / package path). */
    private static String moduleSpecifier(String dotted) {
        return "./" + dotted.replace('.', '/');
    }

    private void emitExportBlock() {
        StringBuilder sb = new StringBuilder("export { ");
        int i = 0;
        for (String n : definedNames) {
            if (i++ > 0) sb.append(", ");
            sb.append(n);
        }
        line(sb.append(" };").toString());
    }

    /**
     * Applies Python decorators as post-definition rewrites: {@code @a / @b / def f} becomes
     * {@code f = b(f); f = a(f);} (nearest decorator applied first), yielding {@code f = a(b(f))}.
     * Class decorators apply the same way after the class body.
     */
    private void applyDecorators(String name, List<String> decorators) {
        for (int i = decorators.size() - 1; i >= 0; i--) {
            line(name + " = " + decorators.get(i) + "(" + name + ");");
        }
    }

    private void emitAssign(PythonNode.Assign a) {
        String value = emitExpr(a.value());
        if (a.targets().size() == 1) {
            line(emitAssignTarget(a.targets().get(0), value));
        } else {
            // a = b = v  →  evaluate once into a temp, then assign each target
            String tmp = "__neko_py_t" + (tempCounter++);
            line("var " + tmp + " = " + value + ";");
            for (PythonNode t : a.targets()) line(emitAssignTarget(t, tmp));
        }
    }

    /** Returns a full assignment statement (with trailing {@code ;}) for one target. */
    private String emitAssignTarget(PythonNode target, String value) {
        return switch (target) {
            case PythonNode.Name n -> "var " + n.id() + " = " + value + ";";
            case PythonNode.TupleLit t -> "var " + emitTarget(t) + " = " + value + ";";
            default -> emitExpr(target) + " = " + value + ";";   // Attribute / Index
        };
    }

    private void emitAugAssign(PythonNode.AugAssign a) {
        String target = emitExpr(a.target());
        String value = emitExpr(a.value());
        String op = a.op();
        if (op.equals("//=")) {
            line(target + " = Math.floor(" + target + " / " + value + ");");
        } else if (op.equals("%=")) {
            // Python 取模符号跟随除数 → 经助手（JS %= 跟随被除数）
            line(target + " = __nekoMod(" + target + ", " + value + ");");
        } else if (op.equals("*=")) {
            // 序列重复（[0] *= 4 / 'ab' *= 3）或数值乘法 → 经助手
            line(target + " = __nekoMul(" + target + ", " + value + ");");
        } else if (op.equals("@=")) {
            throw err("python matmul '@=' is not supported");
        } else {
            // += -= /= **= &= |= ^= <<= >>=  — all valid JS augmented operators
            line(target + " " + op + " " + value + ";");
        }
    }

    private void emitClass(PythonNode.ClassDef c) {
        classNames.add(c.name());   // track so calls like Counter(10) emit `new Counter(10)`
        StringBuilder header = new StringBuilder("class ").append(c.name());
        if (c.base() != null) header.append(" extends ").append(emitExpr(c.base()));
        header.append(" {");
        line(header.toString());
        indent++;
        for (PythonNode member : c.body()) {
            if (member instanceof PythonNode.FunctionDef m) emitMethod(m);
            else if (member instanceof PythonNode.Assign a) emitClassField(a);
            else if (member instanceof PythonNode.ExprStmt e && e.expr() instanceof PythonNode.StrLit doc)
                emitClassDocstring(e, doc);
            else emitStmt(member);
        }
        indent--;
        line("}");
        applyDecorators(c.name(), c.decorators());
    }

    /**
     * 类体中的普通赋值（{@code class C:\n    x = 5}）在 Python 中是类属性；JS 类体不允许
     * {@code var}/普通语句（会是语法错误），因此降级为 ES2022 静态类字段 {@code static x = 5;}，
     * 通过 {@code C.x} 访问，对应 Python 的类属性语义。多重赋值 / 元组解包等复杂形式暂不支持，
     * 编译期报错（与文件内其它不支持特性的处理一致）。
     */
    private void emitClassField(PythonNode.Assign a) {
        if (a.targets().size() != 1 || !(a.targets().get(0) instanceof PythonNode.Name n)) {
            throw err(
                    "python class-body assignments support only a single name target");
        }
        recordMapping(a);
        line("static " + n.id() + " = " + emitExpr(a.value()) + ";");
    }

    /**
     * 类 docstring（类体中的裸字符串语句）：JS 类体不允许裸表达式语句，且 Python 中它也
     * 不产生运行时效果，因此降级为一行注释（source map 仍映射该语句行）。
     */
    private void emitClassDocstring(PythonNode.ExprStmt e, PythonNode.StrLit doc) {
        recordMapping(e);
        line("// docstring: " + doc.value().replace('\n', ' ').replace('\r', ' '));
    }

    private void emitMethod(PythonNode.FunctionDef m) {
        List<String> decos = m.decorators();
        boolean isStatic = decos.contains("staticmethod");
        boolean isClass = decos.contains("classmethod");
        boolean isProp = decos.contains("property");
        for (String d : decos) {
            if (!d.equals("staticmethod") && !d.equals("classmethod") && !d.equals("property")) {
                throw err(
                        "python method decorators other than @staticmethod/@classmethod/@property are not supported (got @" + d + ")");
            }
        }
        boolean prev = rewriteSelf;
        rewriteSelf = !isStatic && !isClass;   // instance/property methods rewrite self → this
        String star = m.isGenerator() ? "*" : "";   // generator method: *name(...)
        String name = jsMethodName(m.name());
        String prefix = isProp ? "get " : (isStatic || isClass) ? "static " : "";
        String params = isStatic ? emitParams(m.params()) : dropFirstParam(m.params());
        if (hasKwargs(m.params())) {
            line(prefix + star + name + "() {");
            if (isClass) line("var cls = this;");   // classmethod: cls is the called constructor
            emitKwPrologue(m.params(), !isStatic);   // instance/class/property drop the leading self/cls
        } else {
            line(prefix + star + name + "(" + params + ") {");
            if (isClass) line("var cls = this;");
        }
        block(m.body());
        line("}");
        rewriteSelf = prev;
    }

    /** Drops the leading self/cls parameter of an instance method. */
    private String dropFirstParam(List<Param> params) {
        if (params.isEmpty()) return "";
        return emitParams(params.subList(1, params.size()));
    }

    private static String jsMethodName(String name) {
        return switch (name) {
            case "__init__" -> "constructor";
            case "__str__" -> "toString";
            default -> name;   // snake_case etc. preserved (valid JS method names)
        };
    }

    /**
     * Python builtin exception names → prelude classes (see {@link #EXC_PRELUDE}), so
     * {@code raise ValueError('x')} / {@code except ValueError} work end to end with the right type.
     * The {@code Exception} root maps to plain {@code Error} (prelude exceptions all extend Error, and
     * this keeps catching JS-native errors working); other builtin names match via
     * {@code __nekoExcIs(e, T)} which also accepts JS-native errors by {@code .name}.
     */
    private static final java.util.Set<String> BUILTIN_EXCEPTIONS = java.util.Set.of(
            "Exception", "ValueError", "TypeError", "KeyError", "IndexError", "RuntimeError",
            "AttributeError", "NameError", "ZeroDivisionError", "ArithmeticError", "LookupError",
            "AssertionError", "OverflowError", "NotImplementedError", "StopIteration", "ImportError",
            "OSError", "EOFError", "MemoryError", "RecursionError");

    /** One disjunct of an except/isinstance type check over {@code varName}. */
    private String instanceOfCond(List<PythonNode> types, String varName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(exceptionCond(varName, types.get(i)));
        }
        return sb.toString();
    }

    /**
     * 单个异常类型的匹配条件：{@code Exception} 根 → {@code instanceof Error}（内建与 JS 原生
     * 错误通吃）；其余内建异常名 → {@code __nekoExcIs(e, T)}（prelude 类 + JS 原生 Error 按 name
     * 兜底）；用户类 → 普通 {@code instanceof}。
     */
    private String exceptionCond(String varName, PythonNode type) {
        if (type instanceof PythonNode.Name n) {
            if ("Exception".equals(n.id())) return "(" + varName + " instanceof Error)";
            if (BUILTIN_EXCEPTIONS.contains(n.id())) return "__nekoExcIs(" + varName + ", " + n.id() + ")";
        }
        return "(" + varName + " instanceof " + emitExpr(type) + ")";
    }

    /**
     * Lowers {@code isinstance(x, T)} / {@code isinstance(x, (T1, T2))} / {@code isinstance(x, [T1, T2])}
     * to a chain of {@link #exceptionCond} disjuncts over {@code valueExpr}, so builtin exception
     * names follow the same matching rules as except clauses.
     */
    private String instanceOfTypeCheck(PythonNode type, String valueExpr) {
        List<PythonNode> types = switch (type) {
            case PythonNode.TupleLit tl -> tl.elements();
            case PythonNode.ListLit ll -> ll.elements();
            default -> List.of(type);
        };
        if (types.isEmpty()) return "false";   // isinstance(x, ()) is always False in Python
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append(exceptionCond(valueExpr, types.get(i)));
        }
        return sb.append(")").toString();
    }

    private static String lastSegment(String dotted) {
        int idx = dotted.lastIndexOf('.');
        return idx < 0 ? dotted : dotted.substring(idx + 1);
    }

    /** If {@code idx} is a negative numeric literal, returns its magnitude; otherwise -1. */
    private static long negativeLiteralMagnitude(PythonNode idx) {
        if (idx instanceof PythonNode.IntLit lit && lit.value() < 0) return -lit.value();
        if (idx instanceof PythonNode.Unary u && "-".equals(u.op())
                && u.operand() instanceof PythonNode.IntLit ol && ol.value() >= 0) {
            return ol.value();
        }
        return -1;
    }

    /**
     * Emits a slice subscript. No-step slices use the fast {@code .slice(lo, hi)} path; any step
     * (including {@code [::-1]}) lowers to a Python-faithful helper that honours negative indices,
     * runtime-determined step sign, and the per-sign {@code None} defaults (start=0/stop=length for
     * positive step; start=length-1/stop=-1 sentinel for negative step). Strings join back to a
     * string; everything else returns an array.
     */
    private String emitSlice(String obj, PythonNode.Slice s) {
        if (s.step() == null) {
            String lo = s.lower() != null ? emitExpr(s.lower()) : "0";
            return s.upper() != null ? obj + ".slice(" + lo + ", " + emitExpr(s.upper()) + ")"
                    : obj + ".slice(" + lo + ")";
        }
        String lo = s.lower() != null ? emitExpr(s.lower()) : "null";
        String hi = s.upper() != null ? emitExpr(s.upper()) : "null";
        String step = emitExpr(s.step());
        return "((function (__s) {\n"
                + "  var __n = __s.length, __step = " + step + ";\n"
                + "  if (__step === 0) throw new Error(\"slice step cannot be zero\");\n"
                + "  var __lo = " + lo + ", __hi = " + hi + ", __r = [];\n"
                + "  if (__step > 0) {\n"
                + "    var __start = (__lo === null) ? 0 : __lo, __stop = (__hi === null) ? __n : __hi;\n"
                + "    if (__start < 0) { __start += __n; if (__start < 0) __start = 0; } if (__start > __n) __start = __n;\n"
                + "    if (__stop < 0) { __stop += __n; if (__stop < 0) __stop = 0; } if (__stop > __n) __stop = __n;\n"
                + "    for (var __i = __start; __i < __stop; __i += __step) __r.push(__s[__i]);\n"
                + "  } else {\n"
                + "    var __start = (__lo === null) ? (__n - 1) : __lo, __stop = (__hi === null) ? -1 : __hi;\n"
                + "    if (__start < 0) { __start += __n; if (__start < 0) __start = -1; } if (__start >= __n) __start = __n - 1;\n"
                + "    if (__hi !== null) { if (__stop < 0) { __stop += __n; if (__stop < 0) __stop = -1; } if (__stop >= __n) __stop = __n - 1; }\n"
                + "    for (var __i = __start; __i > __stop; __i += __step) __r.push(__s[__i]);\n"
                + "  }\n"
                + "  return (typeof __s === \"string\") ? __r.join(\"\") : __r;\n"
                + "})(" + obj + "))";
    }

    /**
     * Maps common Python str/list/dict/set method calls to JS idioms. Returns null for unmapped
     * methods — and now also for arity mismatches — so those fall through to a verbatim
     * {@code obj.method(args)} call (a loud runtime TypeError instead of silently applying the wrong
     * mapping). The worst-offender dict method names ({@link #DICT_METHODS}) are additionally
     * hijack-guarded: a user class defining {@code def keys(self)} keeps its own method (see
     * {@link #rtDispatch}).
     */
    private String emitMethodCall(PythonNode.Attribute attr, List<PythonNode> args) {
        String obj = emitExpr(attr.obj());
        String m = attr.attr();
        String a = emitArgs(args);
        String e0 = args.isEmpty() ? "" : emitExpr(args.get(0));
        String e1 = args.size() > 1 ? emitExpr(args.get(1)) : null;
        boolean userInstance = DICT_METHODS.contains(m) && hijackReceiverIsUserInstance(attr);
        boolean plainReceiver = DICT_METHODS.contains(m) && hijackReceiverIsPlain(attr);
        return switch (m) {
            // str
            case "upper" -> args.isEmpty() ? obj + ".toUpperCase()" : null;
            case "lower" -> args.isEmpty() ? obj + ".toLowerCase()" : null;
            case "strip" -> args.isEmpty() ? obj + ".trim()" : null;
            case "lstrip" -> args.isEmpty() ? obj + ".trimStart()" : null;
            case "rstrip" -> args.isEmpty() ? obj + ".trimEnd()" : null;
            case "find" -> args.size() == 1 ? obj + ".indexOf(" + a + ")" : null;
            case "rfind" -> args.size() == 1 ? obj + ".lastIndexOf(" + a + ")" : null;
            case "index" -> args.size() == 1 ? obj + ".indexOf(" + a + ")" : null;
            case "ljust" -> args.size() == 1 ? obj + ".padEnd(" + a + ")" : null;
            case "rjust" -> args.size() == 1 ? obj + ".padStart(" + a + ")" : null;
            case "zfill" -> args.size() == 1 ? obj + ".padStart(" + a + ", \"0\")" : null;
            case "replace" -> args.size() == 2 ? obj + ".replaceAll(" + a + ")" : null;
            case "startswith" -> args.size() == 1 ? obj + ".startsWith(" + a + ")" : null;
            case "endswith" -> args.size() == 1 ? obj + ".endsWith(" + a + ")" : null;
            case "count" -> args.size() == 1 ? obj + ".split(" + e0 + ").length - 1" : null;
            case "split" -> args.size() <= 1
                    ? (args.isEmpty()
                        ? obj + ".trim().split(/\\s+/).filter(function (x) { return x !== \"\"; })"
                        : obj + ".split(" + a + ")")
                    : null;
            case "join" -> args.size() == 1 ? obj + ".join(" + a + ")" : null;
            // list
            case "append" -> args.size() == 1 ? obj + ".push(" + a + ")" : null;
            case "copy" -> args.isEmpty() ? obj + ".slice()" : null;
            case "reverse" -> args.isEmpty() ? obj + ".reverse()" : null;
            case "insert" -> (args.size() == 2 ? obj + ".splice(" + e0 + ", 0, " + e1 + ")" : null);
            case "remove" -> (args.size() == 1
                    ? "((function (arr, v) { var i = arr.indexOf(v); if (i >= 0) arr.splice(i, 1); })(" + obj + ", " + e0 + "))"
                    : null);
            case "pop" -> {
                // JS 数组自带 .pop（运行时探测会误命中），故 pop 不做探测，仅防用户类劫持
                if (args.size() > 1 || userInstance) yield null;
                yield args.isEmpty() ? obj + ".pop()" : obj + ".splice(" + e0 + ", 1)[0]";
            }
            case "sort" -> args.isEmpty()
                    // 与 sorted() 相同的比较器：数值按大小（JS 默认 sort 是字典序，[10,2,1] → [1,10,2]）
                    ? obj + ".sort((a, b) => ((a < b) ? -1 : ((a > b) ? 1 : 0)))"
                    : null;
            // dict (obj is a plain JS object) — worst offenders 走劫持防护
            case "keys" -> {
                if (!args.isEmpty() || userInstance) yield null;
                yield plainReceiver ? rtDispatch(obj, m, obj + ".keys()", "Object.keys(" + obj + ")")
                        : "Object.keys(" + obj + ")";
            }
            case "values" -> {
                if (!args.isEmpty() || userInstance) yield null;
                yield plainReceiver ? rtDispatch(obj, m, obj + ".values()", "Object.values(" + obj + ")")
                        : "Object.values(" + obj + ")";
            }
            case "items" -> {
                if (!args.isEmpty() || userInstance) yield null;
                yield plainReceiver ? rtDispatch(obj, m, obj + ".items()", "Object.entries(" + obj + ")")
                        : "Object.entries(" + obj + ")";
            }
            case "update" -> {
                if (args.size() != 1 || userInstance) yield null;
                yield plainReceiver
                        ? rtDispatch(obj, m, obj + ".update(" + a + ")", "Object.assign(" + obj + ", " + a + ")")
                        : "Object.assign(" + obj + ", " + a + ")";
            }
            case "get" -> {
                if (userInstance) yield null;   // 用户类自己的 get 方法（含零参 def get(self)）
                if (args.isEmpty()) {
                    // 零参 .get() 旧实现发射 `obj[]`（非法 JS）——编译期显式报错
                    throw err("python '.get()' needs a key argument (get(key[, default]))");
                }
                if (args.size() > 2) yield null;
                String fb = (e1 != null)
                        ? "(" + obj + "[" + e0 + "] !== undefined ? " + obj + "[" + e0 + "] : " + e1 + ")"
                        : obj + "[" + e0 + "]";
                yield plainReceiver ? rtDispatch(obj, m, obj + ".get(" + a + ")", fb) : fb;
            }
            // set
            case "discard" -> args.size() == 1 ? obj + ".delete(" + a + ")" : null;
            default -> null;
        };
    }

    /**
     * dict 方法名劫持防护（之一）：receiver 是用户类实例的两种静态可判定情形——
     * {@code UserClass(...).m(...)} 构造调用、方法体内的 {@code self.m(...)}。返回 true 表示
     * 不拦截该方法名（走原生 {@code obj.m(args)} 调用）。
     */
    private boolean hijackReceiverIsUserInstance(PythonNode.Attribute attr) {
        if (attr.obj() instanceof PythonNode.Call cc
                && cc.func() instanceof PythonNode.Name cn && classNames.contains(cn.id())) return true;
        return rewriteSelf && attr.obj() instanceof PythonNode.Name sn && "self".equals(sn.id());
    }

    /** dict 方法名劫持防护（之二）：receiver 是无副作用的简单表达式（Name/属性链/索引）。 */
    private static boolean hijackReceiverIsPlain(PythonNode.Attribute attr) {
        PythonNode o = attr.obj();
        return o instanceof PythonNode.Name || o instanceof PythonNode.Attribute || o instanceof PythonNode.Index;
    }

    /**
     * dict 方法名劫持防护（之三）：运行时探测分发——receiver 定义了同名方法（用户类实例 /
     * Map 自带 get/keys 等）则调用之，否则走 dict 降级映射。receiver 必须是简单表达式
     * （{@link #hijackReceiverIsPlain}），因为 obj 文本会被求值多次。
     */
    private static String rtDispatch(String obj, String attrName, String userForm, String dictForm) {
        return "(typeof " + obj + "." + attrName + " === \"function\" ? " + userForm + " : " + dictForm + ")";
    }

    /**
     * Lowers {@code with ctx [as tgt]: body} to an in-scope acquire/try/finally (not wrapped in an
     * IIFE) so that {@code return}/{@code break}/{@code continue} inside the body propagate to the
     * enclosing scope. If the context object exposes JS {@code __enter__}/{@code __exit__} methods
     * (a Python-style context manager), they are called; otherwise the value is bound as-is, which
     * covers the common {@code with EXPR as x:} binding case. Multiple items nest.
     */
    private void emitWith(PythonNode.With w, int idx) {
        if (idx == w.items().size()) {
            for (PythonNode s : w.body()) emitStmt(s);   // body sits inside the innermost try { }
            return;
        }
        PythonNode.WithItem item = w.items().get(idx);
        String ctxVar = "__nekoCtx" + (tempCounter++);
        line("var " + ctxVar + " = " + emitExpr(item.context()) + ";");
        String entered = "((" + ctxVar + " != null && typeof " + ctxVar + ".__enter__ === \"function\") ? "
                + ctxVar + ".__enter__() : " + ctxVar + ")";
        if (item.target() != null) {
            line("var " + emitTarget(item.target()) + " = " + entered + ";");
        } else {
            line(entered + ";");   // call __enter__ for its side effect, discard the value
        }
        line("try {");
        indent++;
        emitWith(w, idx + 1);
        indent--;
        line("} finally {");
        indent++;
        line("if (" + ctxVar + " != null && typeof " + ctxVar + ".__exit__ === \"function\") " + ctxVar + ".__exit__();");
        indent--;
        line("}");
    }

    /**
     * Lowers a generator expression {@code (expr for x in iter if cond)} to an immediately-invoked
     * generator function, so it yields lazily like Python and interoperates with {@code list()}/
     * {@code sum()}/{@code any()} (which spread). Multi-clause for/if nest as JS for-of / if blocks.
     */
    private String emitGenExp(PythonNode.GenExp g) {
        StringBuilder sb = new StringBuilder("((function* () {\n");
        int d = 1;
        for (PythonNode.CompClause c : g.clauses()) {
            String pad = "  ".repeat(d);
            if (c instanceof PythonNode.ForComp fc) {
                sb.append(pad).append("for (var ").append(emitTarget(fc.target())).append(" of __nekoIter(")
                        .append(emitExpr(fc.iter())).append(")) {\n");
            } else if (c instanceof PythonNode.IfComp ic) {
                sb.append(pad).append("if (__nekoTruthy(").append(emitExpr(ic.cond())).append(")) {\n");
            }
            d++;
        }
        sb.append("  ".repeat(d)).append("yield ").append(emitExpr(g.element())).append(";\n");
        for (int k = 0; k < g.clauses().size(); k++) {
            d--;
            sb.append("  ".repeat(d)).append("}\n");
        }
        return sb.append("})())").toString();
    }

    /**
     * Lowers a {@code match} statement to an if/else-if chain guarded by a "matched" flag (so a
     * guard that fails, or a non-matching case, falls through to the next case; the first matching
     * case's body runs and the match then ends, matching Python). Each case contributes a pattern
     * condition (over the captured subject) and zero or more bindings; bindings are emitted inside
     * the case's block before the guard so the guard can reference them.
     */
    private void emitMatch(PythonNode.Match m) {
        String subj = "__nekoSubj" + (tempCounter++);
        String matched = "__nekoMatched" + (tempCounter++);
        line("var " + subj + " = " + emitExpr(m.subject()) + ";");
        line("var " + matched + " = false;");
        for (PythonNode.MatchCase mc : m.cases()) {
            List<String> conds = new ArrayList<>();
            List<String> binds = new ArrayList<>();
            emitMatchCond(mc.pattern(), subj, conds, binds);
            String patCond = conds.isEmpty() ? "true" : "(" + String.join(" && ", conds) + ")";
            line("if (!" + matched + " && " + patCond + ") {");
            indent++;
            for (String b : binds) line(b);
            String guard = mc.guard() != null ? "__nekoTruthy(" + emitExpr(mc.guard()) + ")" : "true";
            line("if (" + guard + ") {");
            indent++;
            line(matched + " = true;");
            for (PythonNode s : mc.body()) emitStmt(s);
            indent--;
            line("}");
            indent--;
            line("}");
        }
    }

    /** Accumulates the match condition terms (conds) and binding statements (binds) for one pattern. */
    private void emitMatchCond(PythonNode.Pattern p, String subj, List<String> conds, List<String> binds) {
        if (p instanceof PythonNode.LiteralPat lp) {
            conds.add(subj + " === " + emitExpr(lp.value()));
        } else if (p instanceof PythonNode.CapturePat cp) {
            if (!"_".equals(cp.name())) binds.add("var " + cp.name() + " = " + subj + ";");   // wildcard → no bind
        } else if (p instanceof PythonNode.OrPat op) {
            List<String> altConds = new ArrayList<>();
            for (PythonNode.Pattern alt : op.alts()) {
                List<String> ac = new ArrayList<>();
                emitMatchCond(alt, subj, ac, new ArrayList<>());   // OR alternatives must not bind (Python rule)
                altConds.add(ac.isEmpty() ? "true" : "(" + String.join(" && ", ac) + ")");
            }
            conds.add("(" + String.join(" || ", altConds) + ")");
        } else if (p instanceof PythonNode.SequencePat sp) {
            conds.add("Array.isArray(" + subj + ")");   // sequence patterns don't match strings (Python)
            int n = sp.elements().size();
            conds.add(sp.starName() == null ? subj + ".length === " + n : subj + ".length >= " + n);
            for (int i = 0; i < n; i++) {
                String child = (i < sp.starIndex()) ? subj + "[" + i + "]"
                        : subj + "[" + subj + ".length - " + (n - i) + "]";
                emitMatchCond(sp.elements().get(i), child, conds, binds);
            }
            if (sp.starName() != null) {
                binds.add("var " + sp.starName() + " = " + subj + ".slice(" + sp.starIndex() + ", "
                        + subj + ".length - " + (n - sp.starIndex()) + ");");
            }
        } else if (p instanceof PythonNode.MappingPat mp) {
            for (int i = 0; i < mp.keys().size(); i++) {
                String k = emitExpr(mp.keys().get(i));
                conds.add(subj + "[" + k + "] !== undefined");
                binds.add("var " + mp.valueNames().get(i) + " = " + subj + "[" + k + "];");
            }
            if (mp.restName() != null) {
                StringBuilder copy = new StringBuilder("(function (__o) { var __r = {}; for (var __k in __o) __r[__k] = __o[__k];");
                for (PythonNode key : mp.keys()) copy.append(" delete __r[").append(emitExpr(key)).append("];");
                copy.append(" return __r; })(").append(subj).append(")");
                binds.add("var " + mp.restName() + " = " + copy + ";");
            }
        } else if (p instanceof PythonNode.ClassPat cp) {
            conds.add(subj + " instanceof " + cp.className());
            for (var e : cp.keyword().entrySet()) {
                emitMatchCond(e.getValue(), subj + "." + e.getKey(), conds, binds);
            }
        }
    }

    private void writeIf(PythonNode.If i, boolean leadIndent) {
        if (leadIndent) out.append(ind());
        // 条件经 Python 真值判定（空容器为假）；条件串里可能内嵌多行表达式（步进切片/生成器
        // 表达式），其内嵌换行同样要推进 jsLine（与 line() 的记账一致），否则 source map 漂移。
        String cond = emitExpr(i.cond());
        jsLine += countNewlines(cond);
        out.append("if (__nekoTruthy(").append(cond).append(")) {");
        br();
        block(i.thenBody());
        List<PythonNode> els = i.elseBody();
        if (els.isEmpty()) {
            out.append(ind()).append("}");
            br();
        } else if (els.size() == 1 && els.get(0) instanceof PythonNode.If nested) {
            out.append(ind()).append("} else ");
            writeIf(nested, false);
        } else {
            out.append(ind()).append("} else {");
            br();
            block(els);
            out.append(ind()).append("}");
            br();
        }
    }

    // ---- expressions (each compound wraps itself in parens) ----

    private String emitExpr(PythonNode node) {
        return switch (node) {
            case PythonNode.IntLit l -> Long.toString(l.value());
            case PythonNode.FloatLit l -> Double.toString(l.value());
            case PythonNode.StrLit l -> jsString(l.value());
            case PythonNode.FString f -> jsTemplate(f);
            case PythonNode.BoolLit l -> Boolean.toString(l.value());
            case PythonNode.NoneLit l -> "null";
            case PythonNode.Name n -> (rewriteSelf && "self".equals(n.id())) ? "this" : n.id();
            case PythonNode.Attribute a -> emitExpr(a.obj()) + "." + a.attr();
            case PythonNode.Index ix -> {
                if (ix.index() instanceof PythonNode.Slice s) {
                    yield emitSlice(emitExpr(ix.obj()), s);
                }
                // negative literal index → Python last-element semantics via slice
                // (`-1` parses as Unary("-", IntLit), so detect both forms)
                long mag = negativeLiteralMagnitude(ix.index());
                if (mag >= 0) {
                    yield emitExpr(ix.obj()) + ".slice(-" + mag + ")[0]";
                }
                yield emitExpr(ix.obj()) + "[" + emitExpr(ix.index()) + "]";
            }
            case PythonNode.Call c -> emitCall(c);
            case PythonNode.Unary u -> "not".equals(u.op())
                    ? "(!__nekoTruthy(" + emitExpr(u.operand()) + "))"   // not 以 Python 真值判定（空容器为假）
                    : "(" + u.op() + emitExpr(u.operand()) + ")";   // - + ~
            case PythonNode.Binary b -> emitBinary(b);
            case PythonNode.Compare c -> emitCompare(c);
            case PythonNode.Ternary t -> "(__nekoTruthy(" + emitExpr(t.cond()) + ") ? " + emitExpr(t.ifTrue()) + " : " + emitExpr(t.ifFalse()) + ")";
            case PythonNode.Walrus w -> "(" + w.name() + " = " + emitExpr(w.value()) + ")";
            case PythonNode.GenExp g -> emitGenExp(g);
            case PythonNode.Starred s -> "..." + emitExpr(s.value());   // standalone (rare); spreads normally apply at emitArgs/emitElements
            case PythonNode.ListLit l -> emitElements(l.elements());
            case PythonNode.TupleLit l -> emitElements(l.elements());
            case PythonNode.DictLit d -> emitDict(d);
            case PythonNode.SetLit l -> "new Set(" + emitElements(l.elements()) + ")";
            case PythonNode.Lambda lam -> {
                if (hasKwargs(lam.params())) {
                    throw err("python **kwargs is not supported in a lambda (use a def)");
                }
                yield "((" + emitParams(lam.params()) + ") => (" + emitExpr(lam.body()) + "))";
            }
            case PythonNode.ListComp lc -> compChain(lc.clauses(), emitExpr(lc.element()));
            case PythonNode.Yield y -> y.from()
                    ? ("(yield* " + emitExpr(y.value()) + ")")
                    : ("(yield" + (y.value() != null ? " " + emitExpr(y.value()) : "") + ")");
            case PythonNode.DictComp dc -> "Object.fromEntries(" + compChain(dc.clauses(),
                    "[" + emitExpr(dc.key()) + ", " + emitExpr(dc.value()) + "]") + ")";
            case PythonNode.SetComp sc -> "new Set(" + compChain(sc.clauses(), emitExpr(sc.element())) + ")";
            default -> throw err("unsupported expression: " + node.getClass().getSimpleName());
        };
    }

    private String emitCall(PythonNode.Call c) {
        // super().__init__(args) → super(args);  super().method(args) → super.method(args)
        if (c.func() instanceof PythonNode.Attribute attr
                && attr.obj() instanceof PythonNode.Call sup
                && sup.func() instanceof PythonNode.Name sn && "super".equals(sn.id()) && sup.args().isEmpty()) {
            // kwargs to super() cannot be lowered (JS super() takes positionals only) — reject them
            // instead of silently dropping them, which previously produced wrong behaviour.
            for (PythonNode a : c.args()) {
                if (a instanceof PythonNode.Kwarg k) {
                    throw err("python keyword argument '" + k.name()
                            + "=' in super() is not supported");
                }
                if (a instanceof PythonNode.Starred s && s.dictSpread()) {
                    throw err("python **kwargs spread in super() is not supported");
                }
            }
            String args = emitArgs(c.args());
            if ("__init__".equals(attr.attr())) return "super(" + args + ")";
            return "super." + attr.attr() + "(" + args + ")";
        }
        // separate positional args / keyword args / **dict-spreads first (so method-call mappings never see kwargs)
        List<PythonNode> positional = new ArrayList<>();   // may contain Starred(*) → spread by emitArgs
        Map<String, PythonNode> kwargs = new LinkedHashMap<>();
        List<PythonNode> kwSpreads = new ArrayList<>();     // **expr → merged into the trailing kw object
        for (PythonNode a : c.args()) {
            if (a instanceof PythonNode.Kwarg k) kwargs.put(k.name(), k.value());
            else if (a instanceof PythonNode.Starred s && s.dictSpread()) kwSpreads.add(s.value());
            else positional.add(a);
        }
        boolean hasKw = !kwargs.isEmpty() || !kwSpreads.isEmpty();
        // method calls: map common str/list/dict/set methods to JS idioms (they take no kwargs)
        if (!hasKw && c.func() instanceof PythonNode.Attribute mem) {
            String mapped = emitMethodCall(mem, positional);
            if (mapped != null) return mapped;
        }
        if (c.func() instanceof PythonNode.Name fn) {
            String e0 = positional.isEmpty() ? "" : emitExpr(positional.get(0));
            switch (fn.id()) {
                case "range" -> { return emitRange(positional); }
                case "len" -> { if (positional.size() == 1) return "__nekoLen(" + e0 + ")"; }
                case "print" -> { return emitPrint(positional, kwargs); }
                case "abs" -> { if (positional.size() == 1) return "Math.abs(" + e0 + ")"; }
                case "min" -> {
                    PythonNode keyFn = kwargs.get("key");
                    if (keyFn != null && positional.size() == 1) {
                        String kf = emitExpr(keyFn);
                        return "([...(" + e0 + ")]).reduce(function (a, b) { return (" + kf + "(a) <= " + kf + "(b)) ? a : b; })";
                    }
                    return positional.size() == 1 ? "Math.min(..." + e0 + ")" : "Math.min(" + emitArgs(positional) + ")";
                }
                case "max" -> {
                    PythonNode keyFn = kwargs.get("key");
                    if (keyFn != null && positional.size() == 1) {
                        String kf = emitExpr(keyFn);
                        return "([...(" + e0 + ")]).reduce(function (a, b) { return (" + kf + "(a) >= " + kf + "(b)) ? a : b; })";
                    }
                    return positional.size() == 1 ? "Math.max(..." + e0 + ")" : "Math.max(" + emitArgs(positional) + ")";
                }
                case "sum" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).reduce((a, b) => (a + b), 0)"; }
                case "str" -> { if (positional.size() == 1) return "String(" + e0 + ")"; }
                case "int" -> { return "parseInt(" + emitArgs(positional) + ")"; }
                case "float" -> { if (positional.size() == 1) return "Number(" + e0 + ")"; }
                case "bool" -> { if (positional.size() == 1) return "__nekoTruthy(" + e0 + ")"; }
                case "list" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "dict" -> { return positional.isEmpty() ? "({})" : "Object.fromEntries(" + e0 + ")"; }
                case "sorted" -> { return emitSorted(positional, kwargs); }
                case "enumerate" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).map((v, i) => [i, v])"; }
                case "set" -> { return positional.isEmpty() ? "new Set()" : "new Set(" + e0 + ")"; }
                case "tuple" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "any" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).some((x) => __nekoTruthy(x))"; }
                case "all" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).every((x) => __nekoTruthy(x))"; }
                case "ord" -> { if (positional.size() == 1) return "(" + e0 + ").codePointAt(0)"; }
                case "chr" -> { if (positional.size() == 1) return "String.fromCodePoint(" + e0 + ")"; }
                case "pow" -> { if (positional.size() == 2) return "Math.pow(" + emitArgs(positional) + ")"; }
                case "callable" -> { if (positional.size() == 1) return "(typeof " + e0 + " === \"function\")"; }
                case "isinstance" -> {
                    if (positional.size() == 2) {
                        return instanceOfTypeCheck(positional.get(1), e0);
                    }
                }
                case "type" -> { if (positional.size() == 1) return "(" + e0 + ").constructor"; }
                case "hex" -> { if (positional.size() == 1) return signPrefix(e0, "0x", 16); }
                case "oct" -> { if (positional.size() == 1) return signPrefix(e0, "0o", 8); }
                case "bin" -> { if (positional.size() == 1) return signPrefix(e0, "0b", 2); }
                case "repr" -> { if (positional.size() == 1) return "JSON.stringify(" + e0 + ")"; }
                case "round" -> {
                    if (positional.size() == 1) return "Math.round(" + e0 + ")";
                    if (positional.size() == 2) {
                        String p1 = emitExpr(positional.get(1));
                        return "(Math.round(" + e0 + " * Math.pow(10, " + p1 + ")) / Math.pow(10, " + p1 + "))";
                    }
                }
                case "divmod" -> {
                    if (positional.size() == 2) {
                        String p1 = emitExpr(positional.get(1));
                        return "[Math.floor(" + e0 + " / " + p1 + "), __nekoMod(" + e0 + ", " + p1 + ")]";
                    }
                }
                case "reversed" -> { if (positional.size() == 1) return "[...(" + e0 + ")].reverse()"; }
                case "map" -> {
                    if (positional.size() == 2)
                        return "[...(" + emitExpr(positional.get(1)) + ")].map(" + emitExpr(positional.get(0)) + ")";
                }
                case "filter" -> {
                    if (positional.size() == 2)
                        return "[...(" + emitExpr(positional.get(1)) + ")].filter(" + emitExpr(positional.get(0)) + ")";
                }
                case "zip" -> {
                    return "((function () { var __its = [" + emitArgs(positional)
                            + "].map(function (x) { return [...x]; }); var __n = __its.length ? Math.min.apply(null, __its.map(function (a) { return a.length; })) : 0; var __r = [];"
                            + " for (var __i = 0; __i < __n; __i++) { var __t = []; for (var __j = 0; __j < __its.length; __j++) __t.push(__its[__j][__i]); __r.push(__t); } return __r; })())";
                }
                case "format" -> {
                    if (positional.size() == 2)
                        return "__nekoFmt(" + e0 + ", " + emitExpr(positional.get(1)) + ", null)";
                }
                case "getattr" -> {
                    if (positional.size() == 2) return e0 + "[" + emitExpr(positional.get(1)) + "]";
                    if (positional.size() == 3) {
                        String k = emitExpr(positional.get(1)), d = emitExpr(positional.get(2));
                        return "(" + e0 + "[" + k + "] !== undefined ? " + e0 + "[" + k + "] : " + d + ")";
                    }
                }
                case "hasattr" -> { if (positional.size() == 2) return "(" + e0 + "[" + emitExpr(positional.get(1)) + "] !== undefined)"; }
                case "setattr" -> { if (positional.size() == 3) return "(" + e0 + "[" + emitExpr(positional.get(1)) + "] = " + emitExpr(positional.get(2)) + ")"; }
                case "delattr" -> { if (positional.size() == 2) return "(delete " + e0 + "[" + emitExpr(positional.get(1)) + "])"; }
                case "iter" -> { if (positional.size() == 1) return "(" + e0 + "[Symbol.iterator]())"; }
                case "next" -> { if (positional.size() == 1) return "(" + e0 + ".next().value)"; }
                case "frozenset" -> { return positional.isEmpty() ? "new Set()" : "new Set(" + e0 + ")"; }
                default -> {}
            }
            // 内建异常类构造：ValueError('x') → new ValueError("x")（类由 EXC_PRELUDE 提供；
            // 旧实现发射裸调用 → 运行时 ReferenceError，且被 except 以错误类型意外捕获）
            if (!hasKw && BUILTIN_EXCEPTIONS.contains(fn.id())) {
                return "new " + fn.id() + "(" + emitArgs(positional) + ")";
            }
            if (classNames.contains(fn.id())) {
                if (hasKw) {
                    if (!kwClassNames.contains(fn.id())) {
                        throw err("python keyword arguments to '" + fn.id()
                                + "()' require its __init__ to declare **kwargs");
                    }
                    return "new " + fn.id() + "(" + emitArgs(positional)
                            + (positional.isEmpty() ? "" : ", ") + kwObjectLiteral(kwargs, kwSpreads) + ")";
                }
                return "new " + fn.id() + "(" + emitArgs(positional) + ")";
            }
        }
        if (hasKw) {
            // Keyword args route to a tagged trailing object only when the callee declares **kwargs.
            boolean kwAware = false;
            String label = "()";
            if (c.func() instanceof PythonNode.Name fn) {
                kwAware = kwFunctions.contains(fn.id());
                label = fn.id() + "()";
            } else if (c.func() instanceof PythonNode.Attribute a) {
                kwAware = kwMethods.contains(a.attr());
                label = "." + a.attr() + "()";
            }
            if (!kwAware) {
                throw err("python keyword arguments require the target to declare **kwargs"
                        + " (or be print/sorted); '" + label + "' does not");
            }
            return emitExpr(c.func()) + "(" + emitArgs(positional)
                    + (positional.isEmpty() ? "" : ", ") + kwObjectLiteral(kwargs, kwSpreads) + ")";
        }
        return emitExpr(c.func()) + "(" + emitArgs(positional) + ")";
    }

    /** print(args, sep=, end=) → console.log([args].join(sep)); end= ignored (console.log adds newline). */
    private String emitPrint(List<PythonNode> args, Map<String, PythonNode> kwargs) {
        String sep = kwargs.containsKey("sep") ? emitExpr(kwargs.get("sep")) : "\" \"";
        return "console.log([" + emitArgs(args) + "].join(" + sep + "))";
    }

    /** sorted(iter, reverse=) → numeric/string sort; reverse honours a True/False literal (else runtime). */
    private String emitSorted(List<PythonNode> args, Map<String, PythonNode> kwargs) {
        if (args.size() != 1) breakKeyword("sorted", args.size());
        PythonNode keyFn = kwargs.get("key");
        String cmp = keyFn != null
                ? "function (a, b) { var ka = (" + emitExpr(keyFn) + ")(a), kb = (" + emitExpr(keyFn) + ")(b); return (ka < kb) ? -1 : (ka > kb) ? 1 : 0; }"
                : "(a, b) => ((a < b) ? -1 : ((a > b) ? 1 : 0))";
        String sorted = "([...(" + emitExpr(args.get(0)) + ")]).sort(" + cmp + ")";
        PythonNode rev = kwargs.get("reverse");
        if (rev == null) return sorted;
        if (rev instanceof PythonNode.BoolLit b) return b.value() ? sorted + ".reverse()" : sorted;
        // non-literal reverse flag → decide at runtime
        return "((function (__a) { if (" + emitExpr(rev) + ") __a.reverse(); return __a; })(" + sorted + "))";
    }

    private void breakKeyword(String name, int argc) {
        throw err("python " + name + "() unsupported with " + argc + " positional args");
    }

    /** {@code hex/oct/bin}: {@code "-0x" + abs.toString(base)} for negatives, {@code "0x" + ...} otherwise. */
    private static String signPrefix(String numExpr, String prefix, int base) {
        return "(((" + numExpr + ") < 0) ? \"-" + prefix + "\" : \"" + prefix + "\") + Math.abs(Math.trunc("
                + numExpr + ")).toString(" + base + ")";
    }

    private String emitRange(List<PythonNode> args) {
        // The Array.from callback parameter must NOT shadow variables in the start/step expressions
        // (e.g. range(i + 1, n) inside a for-loop), so use a reserved param name.
        final String idx = "__nekoRangeIdx";
        if (args.isEmpty()) throw err("python range() needs at least 1 arg");
        if (args.size() == 1) {
            String stop = emitExpr(args.get(0));
            return "Array.from({length: " + stop + "}, function (_, " + idx + ") { return " + idx + "; })";
        }
        if (args.size() == 2) {
            String start = emitExpr(args.get(0));
            String stop = emitExpr(args.get(1));
            return "Array.from({length: (" + stop + " - " + start + ")}, function (_, " + idx + ") { return " + idx + " + " + start + "; })";
        }
        String start = emitExpr(args.get(0));
        String stop = emitExpr(args.get(1));
        String step = emitExpr(args.get(2));
        return "Array.from({length: Math.ceil((" + stop + " - " + start + ") / " + step + ")}, function (_, " + idx + ") { return "
                + start + " + " + idx + " * " + step + "; })";
    }

    private String emitBinary(PythonNode.Binary b) {
        String op = b.op();
        if (op.equals("//")) {
            return "(Math.floor(" + emitExpr(b.left()) + " / " + emitExpr(b.right()) + "))";
        }
        if (op.equals("@")) {
            throw err("python matmul '@' is not supported");
        }
        if (op.equals("%")) {
            // Python 取模符号跟随除数（JS % 跟随被除数：-7 % 2 得 -1，Python 是 1）
            return "(__nekoMod(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("*")) {
            // 序列重复（[0] * 4 → 数组重复；'ab' * 3 → 字符串重复）或数值乘法 → 经助手
            return "(__nekoMul(" + emitExpr(b.left()) + ", " + emitExpr(b.right()) + "))";
        }
        if (op.equals("and")) {
            // 值语义 + Python 真值 + 短路：右操作数包成 thunk 保持惰性，左操作数作为实参只求值一次
            return "__nekoAnd(" + emitExpr(b.left()) + ", () => (" + emitExpr(b.right()) + "))";
        }
        if (op.equals("or")) {
            return "__nekoOr(" + emitExpr(b.left()) + ", () => (" + emitExpr(b.right()) + "))";
        }
        return "(" + emitExpr(b.left()) + " " + op + " " + emitExpr(b.right()) + ")";
    }

    private String emitCompare(PythonNode.Compare c) {
        // Chained comparison: a<b<c parses to Compare(Compare(a,<,b),<,c) → emit ((a<b) && (b<c)).
        // (Python evaluates each operand once; the JS form may re-evaluate the middle operand —
        // acceptable for side-effect-free comparisons, the common case.)
        if (c.left() instanceof PythonNode.Compare lc) {
            String mid = emitExpr(lc.right());
            String right = emitExpr(c.right());
            // 链式 in / not in（a in b in c → (a in b) && (b in c)）的第二段同样必须经 __nekoIn：
            // 旧实现发射裸 JS `in` —— 对 dict/Map/数组/字符串右操作数是运行时 TypeError 或错误语义
            String second = switch (c.op()) {
                case "in" -> "__nekoIn(" + mid + ", " + right + ")";
                case "not in" -> "(!__nekoIn(" + mid + ", " + right + "))";
                default -> "(" + mid + " " + jsCompareOp(c.op()) + " " + right + ")";
            };
            return "(" + emitCompare(lc) + " && " + second + ")";
        }
        String left = emitExpr(c.left());
        String right = emitExpr(c.right());
        return switch (c.op()) {
            // in / not in 经助手：dict(对象)按自有键、Map/Set 按 .has、数组/字符串按 .includes
            // （旧实现一律 .includes —— 对 dict/set 是运行时 TypeError）
            case "in" -> "__nekoIn(" + left + ", " + right + ")";
            case "not in" -> "(!__nekoIn(" + left + ", " + right + "))";
            case "is" -> "(" + left + " === " + right + ")";
            case "is not" -> "(" + left + " !== " + right + ")";
            // == / != → 严格相等：JS 宽松 == 做类型强制（"1" == 1 为 true），会静默产生错误结果
            default -> "(" + left + " " + jsCompareOp(c.op()) + " " + right + ")";
        };
    }

    /** Python {@code ==/!=} 无类型强制 → JS {@code ===/!==}；其余比较运算符字面直传。 */
    private static String jsCompareOp(String op) {
        return switch (op) {
            case "==" -> "===";
            case "!=" -> "!==";
            default -> op;
        };
    }

    /**
     * Lowers a comprehension's clause list. A single {@code for} (with any trailing {@code if}
     * guards) emits the idiomatic {@code (iter).filter(...).map(...)} chain; multiple {@code for}
     * clauses emit nested {@code flatMap} calls (innermost wraps the element in an array), which is
     * the only way to express nested loops as a single expression. Guards on a for-level become a
     * conjunction inside that level's arrow ({@code guard ? <rest> : []}).
     *
     * @param elementExpr the per-iteration value — a plain element for list/set, or {@code [k, v]} for dict
     */
    private String compChain(List<PythonNode.CompClause> clauses, String elementExpr) {
        // Group clauses: each ForComp starts a group that absorbs its trailing IfComp guards.
        List<String> targets = new ArrayList<>();
        List<String> iters = new ArrayList<>();
        List<String> guards = new ArrayList<>();
        for (PythonNode.CompClause c : clauses) {
            if (c instanceof PythonNode.ForComp fc) {
                targets.add(emitTarget(fc.target()));
                iters.add("__nekoIter(" + emitExpr(fc.iter()) + ")");   // dict/Map → 键迭代
                guards.add("");
            } else if (c instanceof PythonNode.IfComp ic) {
                int last = guards.size() - 1;
                String g = guards.get(last);
                g = g.isEmpty() ? "__nekoTruthy(" + emitExpr(ic.cond()) + ")"
                        : g + " && (__nekoTruthy(" + emitExpr(ic.cond()) + "))";
                guards.set(last, g);
            }
        }
        if (targets.size() == 1) {
            String base = "(" + iters.get(0) + ")";
            String chained = guards.get(0).isEmpty() ? base
                    : base + ".filter((" + targets.get(0) + ") => " + guards.get(0) + ")";
            return chained + ".map((" + targets.get(0) + ") => " + elementExpr + ")";
        }
        // multiple for-clauses → nested flatMap; innermost wraps the element in an array
        String body = "[" + elementExpr + "]";
        for (int gi = targets.size() - 1; gi >= 0; gi--) {
            String guard = guards.get(gi);
            String arrowBody = guard.isEmpty() ? body : ("(" + guard + " ? " + body + " : [])");
            body = "((" + iters.get(gi) + ").flatMap((" + targets.get(gi) + ") => " + arrowBody + "))";
        }
        return body;
    }

    private String emitDict(PythonNode.DictLit d) {
        StringBuilder sb = new StringBuilder("({");
        for (int i = 0; i < d.keys().size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode k = d.keys().get(i);
            if (k instanceof PythonNode.Starred sk) sb.append("...").append(emitExpr(sk.value()));   // {**spread}
            else sb.append("[").append(emitExpr(k)).append("]: ").append(emitExpr(d.values().get(i)));
        }
        return sb.append("})").toString();
    }

    private String emitElements(List<PythonNode> elems) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elems.size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode e = elems.get(i);
            if (e instanceof PythonNode.Starred s) sb.append("...").append(emitExpr(s.value()));   // *spread
            else sb.append(emitExpr(e));
        }
        return sb.append("]").toString();
    }

    private String emitArgs(List<PythonNode> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            PythonNode a = args.get(i);
            if (a instanceof PythonNode.Starred s) sb.append("...").append(emitExpr(s.value()));   // f(*args)
            else sb.append(emitExpr(a));
        }
        return sb.toString();
    }

    private String emitParams(List<Param> params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            Param p = params.get(i);
            if (p.starArg()) sb.append("...");
            sb.append(p.name());
            if (p.defaultValue() != null) sb.append(" = ").append(emitExpr(p.defaultValue()));
        }
        return sb.toString();
    }

    /** A target pattern (for-of / comprehension param): name or {@code [a, b]} destructuring. */
    private String emitTarget(PythonNode target) {
        return switch (target) {
            case PythonNode.Name n -> n.id();
            case PythonNode.TupleLit t -> {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < t.elements().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(emitTarget(t.elements().get(i)));
                }
                yield sb.append("]").toString();
            }
            default -> emitExpr(target);
        };
    }

    // ---- string helpers ----

    private static String jsString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private String jsTemplate(PythonNode.FString f) {
        StringBuilder sb = new StringBuilder().append('`');
        for (PythonNode part : f.parts()) {
            if (part instanceof PythonNode.StrLit lit) {
                for (int i = 0; i < lit.value().length(); i++) {
                    char c = lit.value().charAt(i);
                    switch (c) {
                        case '`' -> sb.append("\\`");
                        case '\\' -> sb.append("\\\\");
                        case '\n' -> sb.append("\\n");
                        case '\r' -> sb.append("\\r");
                        case '$' -> {
                            // escape ${ to avoid spurious interpolation
                            if (i + 1 < lit.value().length() && lit.value().charAt(i + 1) == '{') sb.append("\\$");
                            else sb.append('$');
                        }
                        default -> sb.append(c);
                    }
                }
            } else if (part instanceof PythonNode.Formatted fm) {
                sb.append("${__nekoFmt(").append(emitExpr(fm.expr())).append(", ")
                        .append(fm.spec() != null ? jsString(fm.spec()) : "null").append(", ")
                        .append(fm.conv() != null ? jsString(fm.conv()) : "null").append(")}");
            } else {
                sb.append("${").append(emitExpr(part)).append('}');
            }
        }
        return sb.append('`').toString();
    }

    // ---- indent / block helpers ----

    private void block(List<PythonNode> stmts) {
        indent++;
        for (PythonNode s : stmts) emitStmt(s);
        indent--;
    }

    private void line(String s) {
        out.append(ind()).append(s);
        // s 可能内嵌多行表达式串（步进切片 IIFE / 生成器表达式）：其中的换行同样推进生成行
        // 计数，否则后续语句的 source map 映射会整体偏移（br() 只 +1 不够）。
        jsLine += countNewlines(s);
        br();
    }

    /** 无缩进发射一行（运行时助手 / prelude 用；无 source map 行）。 */
    private void line0(String s) {
        out.append(s);
        br();
    }

    /** Appends a line terminator and advances the generated-line counter (single point of truth for jsLine). */
    private void br() {
        out.append('\n');
        jsLine++;
    }

    private static int countNewlines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    /**
     * 发射期报错的统一出口：附上当前语句的 Python 源码行（{@link #curLine}，由 emitStmt 从
     * srcLines 维护——表达式级报错报告其所属语句的行，可接受）。无语句上下文时原样返回。
     */
    private IllegalArgumentException err(String msg) {
        return curLine > 0
                ? new IllegalArgumentException(msg + " (python source line " + curLine + ")")
                : new IllegalArgumentException(msg);
    }

    private String ind() {
        return "  ".repeat(indent);
    }
}
