package com.tkisor.nekojs.core.compiler.python;

import com.tkisor.nekojs.core.compiler.python.ast.PythonNode;
import com.tkisor.nekojs.core.compiler.python.ast.PythonNode.Param;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

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

    public PythonEmitter(IdentityHashMap<PythonNode, Integer> srcLines) {
        this.srcLines = srcLines;
    }

    public String emit(PythonNode.Module module) {
        for (PythonNode stmt : module.body()) emitStmt(stmt);
        return out.toString();
    }

    /** (generatedJsLine, originalPythonLine0Based) pairs, one per statement's first emitted line. */
    public List<int[]> mappings() {
        return mappings;
    }

    // ---- statements ----

    private void emitStmt(PythonNode node) {
        // Record a statement-level mapping (the line about to be emitted ← its Python source line).
        if (!(node instanceof PythonNode.Pass)) {
            Integer py = srcLines.get(node);
            if (py != null) mappings.add(new int[]{jsLine, py - 1});
        }
        switch (node) {
            case PythonNode.Module m -> throw new IllegalArgumentException("nested module");
            case PythonNode.FunctionDef f -> {
                line("function " + f.name() + "(" + emitParams(f.params()) + ") {");
                block(f.body());
                line("}");
            }
            case PythonNode.ClassDef c -> emitClass(c);
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
            case PythonNode.Break b -> line("break;");
            case PythonNode.Continue c -> line("continue;");
            case PythonNode.Pass p -> { /* emit nothing */ }
            case PythonNode.Assign a -> emitAssign(a);
            case PythonNode.AugAssign a -> emitAugAssign(a);
            case PythonNode.ExprStmt e -> line(emitExpr(e.expr()) + ";");
            case PythonNode.Import imp -> {
                for (PythonNode.Spec s : imp.specs()) {
                    String local = s.alias() != null ? s.alias() : lastSegment(s.name());
                    line("var " + local + " = globalThis." + s.name() + ";");
                }
            }
            case PythonNode.ImportFrom imp -> {
                if (imp.star()) {
                    throw new IllegalArgumentException("python 'from X import *' is not supported");
                }
                for (PythonNode.Spec s : imp.specs()) {
                    String local = s.alias() != null ? s.alias() : s.name();
                    line("var " + local + " = globalThis." + imp.module() + "." + s.name() + ";");
                }
            }
            default -> throw new IllegalArgumentException("unsupported statement: " + node.getClass().getSimpleName());
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
    }

    private void emitMethod(PythonNode.FunctionDef m) {
        boolean isStatic = m.decorators().stream().anyMatch("staticmethod"::equals);
        if (m.decorators().stream().anyMatch(d -> !d.equals("staticmethod"))) {
            throw new IllegalArgumentException("python class decorators other than @staticmethod are not supported");
        }
        boolean prev = rewriteSelf;
        rewriteSelf = !isStatic;   // instance methods rewrite self → this; static methods do not
        String params = isStatic ? emitParams(m.params()) : dropFirstParam(m.params());
        line((isStatic ? "static " : "") + jsMethodName(m.name()) + "(" + params + ") {");
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

    private static String lastSegment(String dotted) {
        int idx = dotted.lastIndexOf('.');
        return idx < 0 ? dotted : dotted.substring(idx + 1);
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
            case PythonNode.Index ix -> emitExpr(ix.obj()) + "[" + emitExpr(ix.index()) + "]";
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
            default -> throw new IllegalArgumentException("unsupported expression: " + node.getClass().getSimpleName());
        };
    }

    private String emitCall(PythonNode.Call c) {
        // super().__init__(args) → super(args);  super().method(args) → super.method(args)
        if (c.func() instanceof PythonNode.Attribute attr
                && attr.obj() instanceof PythonNode.Call sup
                && sup.func() instanceof PythonNode.Name sn && "super".equals(sn.id()) && sup.args().isEmpty()) {
            String args = emitArgs(c.args());
            if ("__init__".equals(attr.attr())) return "super(" + args + ")";
            return "super." + attr.attr() + "(" + args + ")";
        }
        if (c.func() instanceof PythonNode.Name fn) {
            List<PythonNode> args = c.args();
            String e0 = args.isEmpty() ? "" : emitExpr(args.get(0));
            switch (fn.id()) {
                case "range" -> { return emitRange(args); }
                case "len" -> { if (args.size() == 1) return "(" + e0 + ").length"; }
                case "print" -> { return "console.log(" + emitArgs(args) + ")"; }
                case "abs" -> { if (args.size() == 1) return "Math.abs(" + e0 + ")"; }
                case "min" -> { return args.size() == 1 ? "Math.min(..." + e0 + ")" : "Math.min(" + emitArgs(args) + ")"; }
                case "max" -> { return args.size() == 1 ? "Math.max(..." + e0 + ")" : "Math.max(" + emitArgs(args) + ")"; }
                case "sum" -> { if (args.size() == 1) return "(" + e0 + ").reduce((a, b) => (a + b), 0)"; }
                case "str" -> { if (args.size() == 1) return "String(" + e0 + ")"; }
                case "int" -> { if (args.size() == 1) return "parseInt(" + e0 + ", 10)"; }
                case "float" -> { if (args.size() == 1) return "Number(" + e0 + ")"; }
                case "bool" -> { if (args.size() == 1) return "Boolean(" + e0 + ")"; }
                case "list" -> { return args.isEmpty() ? "[]" : "[..." + e0 + "]"; }
                case "dict" -> { return args.isEmpty() ? "({})" : "Object.fromEntries(" + e0 + ")"; }
                case "sorted" -> {
                    if (args.size() == 1) return "([...(" + e0 + ")]).sort((a, b) => ((a < b) ? -1 : ((a > b) ? 1 : 0)))";
                }
                case "enumerate" -> { if (args.size() == 1) return "(" + e0 + ").map((v, i) => [i, v])"; }
                default -> {}
            }
            if (classNames.contains(fn.id())) {
                return "new " + fn.id() + "(" + emitArgs(args) + ")";
            }
        }
        return emitExpr(c.func()) + "(" + emitArgs(c.args()) + ")";
    }

    private String emitRange(List<PythonNode> args) {
        if (args.isEmpty()) throw new IllegalArgumentException("python range() needs at least 1 arg");
        if (args.size() == 1) {
            String stop = emitExpr(args.get(0));
            return "Array.from({length: " + stop + "}, function (_, i) { return i; })";
        }
        if (args.size() == 2) {
            String start = emitExpr(args.get(0));
            String stop = emitExpr(args.get(1));
            return "Array.from({length: (" + stop + " - " + start + ")}, function (_, i) { return i + " + start + "; })";
        }
        String start = emitExpr(args.get(0));
        String stop = emitExpr(args.get(1));
        String step = emitExpr(args.get(2));
        return "Array.from({length: Math.ceil((" + stop + " - " + start + ") / " + step + ")}, function (_, i) { return "
                + start + " + i * " + step + "; })";
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
        String target = emitTarget(lc.target());
        String iter = emitExpr(lc.iter());
        String elem = emitExpr(lc.element());
        if (lc.cond() != null) {
            return "(" + iter + ").filter((" + target + ") => " + emitExpr(lc.cond()) + ").map((" + target + ") => " + elem + ")";
        }
        return "(" + iter + ").map((" + target + ") => " + elem + ")";
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
