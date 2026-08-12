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
     * or conversion (detected via {@link #containsFormatted}). Handles: {@code .Nf}, width, alignment
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
        // and collect **kwargs-aware functions/methods/classes so call sites can route keyword args.
        for (PythonNode stmt : module.body()) {
            collectDefinitions(stmt);
            collectKwAware(stmt);
            if (containsFormatted(stmt)) needsFmt = true;
        }
        // Pass 2: ESM import declarations must precede all other statements; emit them first, each
        // mapped back to its Python source line. Module specifiers are relative to this file
        // (foo → ./foo, a.b.c → ./a/b/c); NekoModuleResolver probes .py / .js / index.* automatically.
        for (PythonNode stmt : module.body()) {
            if (stmt instanceof PythonNode.Import imp) {
                recordMapping(stmt);
                for (PythonNode.Spec s : imp.specs()) line(esmNamespaceImport(s));
            } else if (stmt instanceof PythonNode.ImportFrom impf) {
                // 魔法 import：`from nekojs import *`（或具名）是给 IDE/pyright 看的类型桩入口，
                // 运行时无意义——剥离之（不 recordMapping、不 line，source map 零影响）。
                if ("nekojs".equals(impf.module())) continue;
                if (impf.star()) throw new IllegalArgumentException("python 'from X import *' is not supported");
                recordMapping(stmt);
                line(esmNamedImport(impf));
            }
        }
        // Pass 3: the remaining statements.
        if (needsFmt) {
            for (String l : FMT_HELPER) { out.append(l); br(); }   // runtime format helper (no source mapping)
        }
        for (PythonNode stmt : module.body()) {
            if (stmt instanceof PythonNode.Import || stmt instanceof PythonNode.ImportFrom) continue;
            emitStmt(stmt);
        }
        // Pass 4: emit the export block. Has no Python source line → no source-map entry. Skipped when
        // the module defines no names (e.g. a bare expression script stays plain JS, not ESM).
        if (!definedNames.isEmpty()) emitExportBlock();
        return out.toString();
    }

    /** (generatedJsLine, originalPythonLine0Based) pairs, one per statement's first emitted line. */
    public List<int[]> mappings() {
        return mappings;
    }

    // ---- statements ----

    private void emitStmt(PythonNode node) {
        // Record a statement-level mapping (the line about to be emitted ← its Python source line).
        if (!(node instanceof PythonNode.Pass)) recordMapping(node);
        switch (node) {
            case PythonNode.Module m -> throw new IllegalArgumentException("nested module");
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
                line("for (var " + emitTarget(f.target()) + " of " + emitExpr(f.iter()) + ") {");
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
                line("while (" + emitExpr(w.cond()) + ") {");
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
                        throw new IllegalArgumentException(
                                "python bare 'raise' is only valid inside an except clause");
                    }
                    line("throw " + errStack.peek() + ";");   // re-raise the current exception
                } else {
                    line("throw " + emitExpr(r.exc()) + ";");
                }
            }
            case PythonNode.Assert a -> {
                String thrown = a.msg() != null ? emitExpr(a.msg()) : "\"AssertionError\"";
                line("if (!(" + emitExpr(a.cond()) + ")) throw new Error(" + thrown + ");");
            }
            case PythonNode.Del d -> {
                for (PythonNode t : d.targets()) line("delete " + emitExpr(t) + ";");
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
            case PythonNode.Import imp -> throw new IllegalArgumentException(
                    "python 'import' is only supported at module top level");
            case PythonNode.ImportFrom imp -> throw new IllegalArgumentException(
                    "python 'from ... import' is only supported at module top level");
            default -> throw new IllegalArgumentException("unsupported statement: " + node.getClass().getSimpleName());
        }
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

    /** Recursively scans a node (and any nested records) for a {@code Formatted} f-string field. */
    private static boolean containsFormatted(Object o) {
        if (o == null) return false;
        if (o instanceof PythonNode.Formatted) return true;
        // format(x, spec) reuses the same helper → trigger its emission when used.
        if (o instanceof PythonNode.Call call && call.func() instanceof PythonNode.Name fn
                && "format".equals(fn.id()) && call.args().size() == 2) return true;
        Class<?> c = o.getClass();
        if (c.isRecord()) {
            for (var rc : c.getRecordComponents()) {
                try {
                    if (containsFormatted(rc.getAccessor().invoke(o))) return true;
                } catch (ReflectiveOperationException ignored) { }
            }
            return false;
        }
        if (o instanceof java.util.List<?> list) {
            for (var e : list) if (containsFormatted(e)) return true;
        }
        return false;
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
        } else if (op.equals("@=")) {
            throw new IllegalArgumentException("python matmul '@=' is not supported");
        } else {
            // += -= *= /= %= **= &= |= ^= <<= >>=  — all valid JS augmented operators
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
            else emitStmt(member);
        }
        indent--;
        line("}");
        applyDecorators(c.name(), c.decorators());
    }

    private void emitMethod(PythonNode.FunctionDef m) {
        List<String> decos = m.decorators();
        boolean isStatic = decos.contains("staticmethod");
        boolean isClass = decos.contains("classmethod");
        boolean isProp = decos.contains("property");
        for (String d : decos) {
            if (!d.equals("staticmethod") && !d.equals("classmethod") && !d.equals("property")) {
                throw new IllegalArgumentException(
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
     * Python builtin exception names → JS {@code Error}, the closest available base class.
     * {@code class MyErr(Exception):} therefore emits {@code class MyErr extends Error}, making
     * {@code except MyErr} instanceof-checks match {@code raise MyErr()} end to end.
     */
    private static final java.util.Set<String> BUILTIN_EXCEPTIONS = java.util.Set.of(
            "Exception", "ValueError", "TypeError", "KeyError", "IndexError", "RuntimeError",
            "AttributeError", "NameError", "ZeroDivisionError", "ArithmeticError", "LookupError",
            "AssertionError", "OverflowError", "NotImplementedError", "StopIteration", "ImportError",
            "OSError", "EOFError", "MemoryError", "RecursionError");

    /** {@code e instanceof T0 || e instanceof T1 ...}; builtin exception names map to Error. */
    private String instanceOfCond(List<PythonNode> types, String varName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(" || ");
            sb.append("(").append(varName).append(" instanceof ").append(exceptionTypeExpr(types.get(i))).append(")");
        }
        return sb.toString();
    }

    private String exceptionTypeExpr(PythonNode type) {
        if (type instanceof PythonNode.Name n && BUILTIN_EXCEPTIONS.contains(n.id())) return "Error";
        return emitExpr(type);
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
     * methods so they fall through to a verbatim {@code obj.method(args)} call.
     */
    private String emitMethodCall(PythonNode.Attribute attr, List<PythonNode> args) {
        String obj = emitExpr(attr.obj());
        String m = attr.attr();
        String a = emitArgs(args);
        String e0 = args.isEmpty() ? "" : emitExpr(args.get(0));
        String e1 = args.size() > 1 ? emitExpr(args.get(1)) : null;
        return switch (m) {
            // str
            case "upper" -> obj + ".toUpperCase()";
            case "lower" -> obj + ".toLowerCase()";
            case "strip" -> obj + ".trim()";
            case "lstrip" -> obj + ".trimStart()";
            case "rstrip" -> obj + ".trimEnd()";
            case "find" -> obj + ".indexOf(" + a + ")";
            case "rfind" -> obj + ".lastIndexOf(" + a + ")";
            case "index" -> obj + ".indexOf(" + a + ")";
            case "ljust" -> obj + ".padEnd(" + a + ")";
            case "rjust" -> obj + ".padStart(" + a + ")";
            case "zfill" -> obj + ".padStart(" + a + ", \"0\")";
            case "replace" -> obj + ".replaceAll(" + a + ")";
            case "startswith" -> obj + ".startsWith(" + a + ")";
            case "endswith" -> obj + ".endsWith(" + a + ")";
            case "count" -> obj + ".split(" + e0 + ").length - 1";
            case "split" -> args.isEmpty()
                    ? obj + ".trim().split(/\\s+/).filter(function (x) { return x !== \"\"; })"
                    : obj + ".split(" + a + ")";
            case "join" -> obj + ".join(" + a + ")";
            // list
            case "append" -> obj + ".push(" + a + ")";
            case "copy" -> obj + ".slice()";
            case "reverse" -> obj + ".reverse()";
            case "insert" -> (e1 != null ? obj + ".splice(" + e0 + ", 0, " + e1 + ")" : null);
            case "remove" -> "((function (arr, v) { var i = arr.indexOf(v); if (i >= 0) arr.splice(i, 1); })(" + obj + ", " + e0 + "))";
            case "pop" -> args.isEmpty() ? obj + ".pop()" : obj + ".splice(" + e0 + ", 1)[0]";
            // dict (obj is a plain JS object)
            case "keys" -> "Object.keys(" + obj + ")";
            case "values" -> "Object.values(" + obj + ")";
            case "items" -> "Object.entries(" + obj + ")";
            case "update" -> "Object.assign(" + obj + ", " + a + ")";
            case "get" -> (e1 != null
                    ? "(" + obj + "[" + e0 + "] !== undefined ? " + obj + "[" + e0 + "] : " + e1 + ")"
                    : obj + "[" + e0 + "]");
            // set
            case "discard" -> obj + ".delete(" + a + ")";
            default -> null;
        };
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
                sb.append(pad).append("for (var ").append(emitTarget(fc.target())).append(" of (")
                        .append(emitExpr(fc.iter())).append(")) {\n");
            } else if (c instanceof PythonNode.IfComp ic) {
                sb.append(pad).append("if (").append(emitExpr(ic.cond())).append(") {\n");
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

    private void writeIf(PythonNode.If i, boolean leadIndent) {
        if (leadIndent) out.append(ind());
        out.append("if (").append(emitExpr(i.cond())).append(") {");
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
            case PythonNode.Unary u -> "(" + jsUnary(u.op()) + emitExpr(u.operand()) + ")";
            case PythonNode.Binary b -> emitBinary(b);
            case PythonNode.Compare c -> emitCompare(c);
            case PythonNode.Ternary t -> "(" + emitExpr(t.cond()) + " ? " + emitExpr(t.ifTrue()) + " : " + emitExpr(t.ifFalse()) + ")";
            case PythonNode.Walrus w -> "(" + w.name() + " = " + emitExpr(w.value()) + ")";
            case PythonNode.GenExp g -> emitGenExp(g);
            case PythonNode.Starred s -> "..." + emitExpr(s.value());   // standalone (rare); spreads normally apply at emitArgs/emitElements
            case PythonNode.ListLit l -> emitElements(l.elements());
            case PythonNode.TupleLit l -> emitElements(l.elements());
            case PythonNode.DictLit d -> emitDict(d);
            case PythonNode.SetLit l -> "new Set(" + emitElements(l.elements()) + ")";
            case PythonNode.Lambda lam -> {
                if (hasKwargs(lam.params())) {
                    throw new IllegalArgumentException("python **kwargs is not supported in a lambda (use a def)");
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
            default -> throw new IllegalArgumentException("unsupported expression: " + node.getClass().getSimpleName());
        };
    }

    private String emitCall(PythonNode.Call c) {
        // super().__init__(args) → super(args);  super().method(args) → super.method(args)
        if (c.func() instanceof PythonNode.Attribute attr
                && attr.obj() instanceof PythonNode.Call sup
                && sup.func() instanceof PythonNode.Name sn && "super".equals(sn.id()) && sup.args().isEmpty()) {
            String args = emitArgs(c.args().stream().filter(a -> !(a instanceof PythonNode.Kwarg)).toList());
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
                case "len" -> { if (positional.size() == 1) return "(" + e0 + ").length"; }
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
                case "bool" -> { if (positional.size() == 1) return "Boolean(" + e0 + ")"; }
                case "list" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "dict" -> { return positional.isEmpty() ? "({})" : "Object.fromEntries(" + e0 + ")"; }
                case "sorted" -> { return emitSorted(positional, kwargs); }
                case "enumerate" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).map((v, i) => [i, v])"; }
                case "set" -> { return positional.isEmpty() ? "new Set()" : "new Set(" + e0 + ")"; }
                case "tuple" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "any" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).some((x) => x)"; }
                case "all" -> { if (positional.size() == 1) return "([...(" + e0 + ")]).every((x) => x)"; }
                case "ord" -> { if (positional.size() == 1) return "(" + e0 + ").codePointAt(0)"; }
                case "chr" -> { if (positional.size() == 1) return "String.fromCodePoint(" + e0 + ")"; }
                case "pow" -> { if (positional.size() == 2) return "Math.pow(" + emitArgs(positional) + ")"; }
                case "callable" -> { if (positional.size() == 1) return "(typeof " + e0 + " === \"function\")"; }
                case "isinstance" -> {
                    if (positional.size() == 2) {
                        PythonNode t = positional.get(1);
                        if (t instanceof PythonNode.TupleLit tl) {
                            StringBuilder sb = new StringBuilder("(");
                            for (int k = 0; k < tl.elements().size(); k++) {
                                if (k > 0) sb.append(" || ");
                                sb.append("(").append(e0).append(" instanceof ").append(emitExpr(tl.elements().get(k))).append(")");
                            }
                            return sb.append(")").toString();
                        }
                        return "(" + e0 + " instanceof " + emitExpr(t) + ")";
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
                        return "[Math.floor(" + e0 + " / " + p1 + "), " + e0 + " % " + p1 + "]";
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
            if (classNames.contains(fn.id())) {
                if (hasKw) {
                    if (!kwClassNames.contains(fn.id())) {
                        throw new IllegalArgumentException("python keyword arguments to '" + fn.id()
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
                throw new IllegalArgumentException("python keyword arguments require the target to declare **kwargs"
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

    private static void breakKeyword(String name, int argc) {
        throw new IllegalArgumentException("python " + name + "() unsupported with " + argc + " positional args");
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
        if (args.isEmpty()) throw new IllegalArgumentException("python range() needs at least 1 arg");
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
            throw new IllegalArgumentException("python matmul '@' is not supported");
        }
        String jsOp = switch (op) {
            case "and" -> "&&";
            case "or" -> "||";
            default -> op;
        };
        return "(" + emitExpr(b.left()) + " " + jsOp + " " + emitExpr(b.right()) + ")";
    }

    private String emitCompare(PythonNode.Compare c) {
        // Chained comparison: a<b<c parses to Compare(Compare(a,<,b),<,c) → emit ((a<b) && (b<c)).
        // (Python evaluates each operand once; the JS form may re-evaluate the middle operand —
        // acceptable for side-effect-free comparisons, the common case.)
        if (c.left() instanceof PythonNode.Compare lc) {
            return "(" + emitCompare(lc) + " && (" + emitExpr(lc.right()) + " " + c.op() + " "
                    + emitExpr(c.right()) + "))";
        }
        String left = emitExpr(c.left());
        String right = emitExpr(c.right());
        return switch (c.op()) {
            case "in" -> "(" + right + ".includes(" + left + "))";
            case "not in" -> "(!" + right + ".includes(" + left + "))";
            case "is" -> "(" + left + " === " + right + ")";
            case "is not" -> "(" + left + " !== " + right + ")";
            default -> "(" + left + " " + c.op() + " " + right + ")";
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
                iters.add(emitExpr(fc.iter()));
                guards.add("");
            } else if (c instanceof PythonNode.IfComp ic) {
                int last = guards.size() - 1;
                String g = guards.get(last);
                g = g.isEmpty() ? "(" + emitExpr(ic.cond()) + ")" : g + " && (" + emitExpr(ic.cond()) + ")";
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

    private static String jsUnary(String op) {
        return switch (op) {
            case "not" -> "!";
            default -> op;   // - + ~
        };
    }

    // ---- indent / block helpers ----

    private void block(List<PythonNode> stmts) {
        indent++;
        for (PythonNode s : stmts) emitStmt(s);
        indent--;
    }

    private void line(String s) {
        out.append(ind()).append(s);
        br();
    }

    /** Appends a line terminator and advances the generated-line counter (single point of truth for jsLine). */
    private void br() {
        out.append('\n');
        jsLine++;
    }

    private String ind() {
        return "  ".repeat(indent);
    }
}
