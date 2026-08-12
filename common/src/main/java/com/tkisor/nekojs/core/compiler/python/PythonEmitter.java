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

    public PythonEmitter(IdentityHashMap<PythonNode, Integer> srcLines) {
        this.srcLines = srcLines;
    }

    public String emit(PythonNode.Module module) {
        // Pass 1: collect top-level defined names so the module can re-export them (Python modules
        // expose all top-level bindings; this lets sibling .py files `from <this> import <name>`).
        for (PythonNode stmt : module.body()) collectDefinitions(stmt);
        // Pass 2: ESM import declarations must precede all other statements; emit them first, each
        // mapped back to its Python source line. Module specifiers are relative to this file
        // (foo → ./foo, a.b.c → ./a/b/c); NekoModuleResolver probes .py / .js / index.* automatically.
        for (PythonNode stmt : module.body()) {
            if (stmt instanceof PythonNode.Import imp) {
                recordMapping(stmt);
                for (PythonNode.Spec s : imp.specs()) line(esmNamespaceImport(s));
            } else if (stmt instanceof PythonNode.ImportFrom impf) {
                if (impf.star()) throw new IllegalArgumentException("python 'from X import *' is not supported");
                recordMapping(stmt);
                line(esmNamedImport(impf));
            }
        }
        // Pass 3: the remaining statements.
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
                line("function" + (f.isGenerator() ? "* " : " ") + f.name() + "(" + emitParams(f.params()) + ") {");
                block(f.body());
                line("}");
                applyDecorators(f.name(), f.decorators());
            }
            case PythonNode.ClassDef c -> emitClass(c);
            case PythonNode.Try t -> {
                line("try {");
                block(t.body());
                List<PythonNode.ExceptClause> excepts = t.excepts();
                if (!excepts.isEmpty()) {
                    boolean typed = excepts.size() > 1 || !excepts.get(0).types().isEmpty();
                    if (!typed) {
                        // bare single except → plain catch (no type check)
                        PythonNode.ExceptClause only = excepts.get(0);
                        line("} catch (" + (only.name() != null ? only.name() : "__nekoErr") + ") {");
                        block(only.body());
                    } else {
                        // typed excepts → one catch + instanceof chain; unmatched errors rethrow.
                        // Builtin exception names map to Error; user classes (e.g. `class MyErr(Exception):`)
                        // become real JS classes, so `instanceof` matches `raise MyErr()` naturally.
                        line("} catch (__nekoErr) {");
                        indent++;
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
                    }
                }
                if (!t.finallyBody().isEmpty()) {
                    line("} finally {");
                    block(t.finallyBody());
                }
                line("}");
            }
            case PythonNode.If i -> writeIf(i, true);
            case PythonNode.For f -> {
                line("for (var " + emitTarget(f.target()) + " of " + emitExpr(f.iter()) + ") {");
                block(f.body());
                line("}");
            }
            case PythonNode.While w -> {
                line("while (" + emitExpr(w.cond()) + ") {");
                block(w.body());
                line("}");
            }
            case PythonNode.Return r -> line(r.value() == null ? "return;" : "return " + emitExpr(r.value()) + ";");
            case PythonNode.Raise r -> {
                if (r.exc() == null) {
                    throw new IllegalArgumentException(
                            "python bare 'raise' is not supported; raise an exception value");
                }
                line("throw " + emitExpr(r.exc()) + ";");
            }
            case PythonNode.Yield y -> line(y.from()
                    ? "yield* " + emitExpr(y.value()) + ";"
                    : "yield " + (y.value() != null ? emitExpr(y.value()) : "") + ";");
            case PythonNode.Break b -> line("break;");
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
        boolean isStatic = m.decorators().stream().anyMatch("staticmethod"::equals);
        if (m.decorators().stream().anyMatch(d -> !d.equals("staticmethod"))) {
            throw new IllegalArgumentException("python class decorators other than @staticmethod are not supported");
        }
        boolean prev = rewriteSelf;
        rewriteSelf = !isStatic;   // instance methods rewrite self → this; static methods do not
        String params = isStatic ? emitParams(m.params()) : dropFirstParam(m.params());
        String star = m.isGenerator() ? "*" : "";   // generator method: *name(...)
        line((isStatic ? "static " : "") + star + jsMethodName(m.name()) + "(" + params + ") {");
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

    /** Emits a slice subscript; v1 supports [lo:up] and [::-1] (reverse, type-agnostic). */
    private String emitSlice(String obj, PythonNode.Slice s) {
        if (s.step() != null) {
            if (negativeLiteralMagnitude(s.step()) == 1 && s.lower() == null && s.upper() == null) {
                return "((function (__s) { return typeof __s === \"string\" ? __s.split(\"\").reverse().join(\"\") "
                        + ": [...__s].reverse(); })(" + obj + "))";
            }
            throw new IllegalArgumentException("python slice step only supports [::-1] in v1");
        }
        String lo = s.lower() != null ? emitExpr(s.lower()) : "0";
        return s.upper() != null ? obj + ".slice(" + lo + ", " + emitExpr(s.upper()) + ")" : obj + ".slice(" + lo + ")";
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
            case PythonNode.ListLit l -> emitElements(l.elements());
            case PythonNode.TupleLit l -> emitElements(l.elements());
            case PythonNode.DictLit d -> emitDict(d);
            case PythonNode.SetLit l -> "new Set(" + emitElements(l.elements()) + ")";
            case PythonNode.Lambda lam -> "((" + emitParams(lam.params()) + ") => (" + emitExpr(lam.body()) + "))";
            case PythonNode.ListComp lc -> emitListComp(lc);
            case PythonNode.Yield y -> y.from()
                    ? ("(yield* " + emitExpr(y.value()) + ")")
                    : ("(yield" + (y.value() != null ? " " + emitExpr(y.value()) : "") + ")");
            case PythonNode.DictComp dc -> "Object.fromEntries(" + compChain(dc.target(), dc.iter(), dc.cond(),
                    "[" + emitExpr(dc.key()) + ", " + emitExpr(dc.value()) + "]") + ")";
            case PythonNode.SetComp sc -> "new Set(" + compChain(sc.target(), sc.iter(), sc.cond(), emitExpr(sc.element())) + ")";
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
        // method calls: map common str/list/dict/set methods to JS idioms
        if (c.func() instanceof PythonNode.Attribute mem) {
            String mapped = emitMethodCall(mem, c.args());
            if (mapped != null) return mapped;
        }
        // separate positional args from keyword args
        List<PythonNode> positional = new ArrayList<>();
        Map<String, PythonNode> kwargs = new LinkedHashMap<>();
        for (PythonNode a : c.args()) {
            if (a instanceof PythonNode.Kwarg k) kwargs.put(k.name(), k.value());
            else positional.add(a);
        }
        if (c.func() instanceof PythonNode.Name fn) {
            String e0 = positional.isEmpty() ? "" : emitExpr(positional.get(0));
            switch (fn.id()) {
                case "range" -> { return emitRange(positional); }
                case "len" -> { if (positional.size() == 1) return "(" + e0 + ").length"; }
                case "print" -> { return emitPrint(positional, kwargs); }
                case "abs" -> { if (positional.size() == 1) return "Math.abs(" + e0 + ")"; }
                case "min" -> { return positional.size() == 1 ? "Math.min(..." + e0 + ")" : "Math.min(" + emitArgs(positional) + ")"; }
                case "max" -> { return positional.size() == 1 ? "Math.max(..." + e0 + ")" : "Math.max(" + emitArgs(positional) + ")"; }
                case "sum" -> { if (positional.size() == 1) return "(" + e0 + ").reduce((a, b) => (a + b), 0)"; }
                case "str" -> { if (positional.size() == 1) return "String(" + e0 + ")"; }
                case "int" -> { return "parseInt(" + emitArgs(positional) + ")"; }
                case "float" -> { if (positional.size() == 1) return "Number(" + e0 + ")"; }
                case "bool" -> { if (positional.size() == 1) return "Boolean(" + e0 + ")"; }
                case "list" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "dict" -> { return positional.isEmpty() ? "({})" : "Object.fromEntries(" + e0 + ")"; }
                case "sorted" -> { return emitSorted(positional, kwargs); }
                case "enumerate" -> { if (positional.size() == 1) return "(" + e0 + ").map((v, i) => [i, v])"; }
                case "set" -> { return positional.isEmpty() ? "new Set()" : "new Set(" + e0 + ")"; }
                case "tuple" -> { return positional.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "any" -> { if (positional.size() == 1) return "(" + e0 + ").some((x) => x)"; }
                case "all" -> { if (positional.size() == 1) return "(" + e0 + ").every((x) => x)"; }
                case "ord" -> { if (positional.size() == 1) return "(" + e0 + ").codePointAt(0)"; }
                case "chr" -> { if (positional.size() == 1) return "String.fromCodePoint(" + e0 + ")"; }
                case "pow" -> { if (positional.size() == 2) return "Math.pow(" + emitArgs(positional) + ")"; }
                case "callable" -> { if (positional.size() == 1) return "(typeof " + e0 + " === \"function\")"; }
                default -> {}
            }
            if (classNames.contains(fn.id())) {
                return "new " + fn.id() + "(" + emitArgs(positional) + ")";
            }
        }
        if (!kwargs.isEmpty()) {
            throw new IllegalArgumentException("python keyword arguments are only supported for print/sorted");
        }
        return emitExpr(c.func()) + "(" + emitArgs(positional) + ")";
    }

    /** print(args, sep=, end=) → console.log([args].join(sep)); end= ignored (console.log adds newline). */
    private String emitPrint(List<PythonNode> args, Map<String, PythonNode> kwargs) {
        String sep = kwargs.containsKey("sep") ? emitExpr(kwargs.get("sep")) : "\" \"";
        return "console.log([" + emitArgs(args) + "].join(" + sep + "))";
    }

    /** sorted(iter, reverse=) → numeric/string sort, optionally reversed. */
    private String emitSorted(List<PythonNode> args, Map<String, PythonNode> kwargs) {
        if (args.size() != 1) breakKeyword("sorted", args.size());
        String base = "([...(" + emitExpr(args.get(0)) + ")]).sort((a, b) => ((a < b) ? -1 : ((a > b) ? 1 : 0)))";
        return kwargs.containsKey("reverse") ? base + ".reverse()" : base;
    }

    private static void breakKeyword(String name, int argc) {
        throw new IllegalArgumentException("python " + name + "() unsupported with " + argc + " positional args");
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

    private String emitListComp(PythonNode.ListComp lc) {
        return compChain(lc.target(), lc.iter(), lc.cond(), emitExpr(lc.element()));
    }

    /** Shared (iter)[.filter].map(target => body) pipeline for list/dict/set comprehensions. */
    private String compChain(PythonNode target, PythonNode iter, PythonNode cond, String bodyExpr) {
        String t = emitTarget(target);
        String base = "(" + emitExpr(iter) + ")";
        String chained = cond != null ? base + ".filter((" + t + ") => " + emitExpr(cond) + ")" : base;
        return chained + ".map((" + t + ") => " + bodyExpr + ")";
    }

    private String emitDict(PythonNode.DictLit d) {
        StringBuilder sb = new StringBuilder("({");
        for (int i = 0; i < d.keys().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("[").append(emitExpr(d.keys().get(i))).append("]: ").append(emitExpr(d.values().get(i)));
        }
        return sb.append("})").toString();
    }

    private String emitElements(List<PythonNode> elems) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elems.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(emitExpr(elems.get(i)));
        }
        return sb.append("]").toString();
    }

    private String emitArgs(List<PythonNode> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(emitExpr(args.get(i)));
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
