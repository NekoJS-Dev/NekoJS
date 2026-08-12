package com.tkisor.nekojs.core.compiler.python.ast;

import java.util.List;

/**
 * Python AST node hierarchy for the {@code python} → JS transpiler
 * ({@link com.tkisor.nekojs.core.compiler.python.PythonToJsCompiler}).
 *
 * <p>A deliberately compact sealed-union of records covering the supported Python subset
 * (see {@code ai_arch/} Python design notes). Statement and expression nodes share one root
 * interface; the parser/emitter distinguish by record type.
 *
 * <p>Not a faithful Python grammar — only what v1 transpiles.
 */
public sealed interface PythonNode {

    // ---- module ----
    record Module(List<PythonNode> body) implements PythonNode {}

    // ---- statements ----
    record FunctionDef(String name, List<Param> params, List<PythonNode> body, List<String> decorators,
                       boolean isGenerator)
            implements PythonNode {}                                   // isGenerator → emit function*
    record ClassDef(String name, PythonNode base, List<PythonNode> body, List<String> decorators)
            implements PythonNode {}                                   // base == null → no extends
    record If(PythonNode cond, List<PythonNode> thenBody, List<PythonNode> elseBody) implements PythonNode {}
    record For(PythonNode target, PythonNode iter, List<PythonNode> body) implements PythonNode {}
    record While(PythonNode cond, List<PythonNode> body) implements PythonNode {}
    record Return(PythonNode value) implements PythonNode {}          // value == null → bare return
    record Break() implements PythonNode {}
    record Continue() implements PythonNode {}
    record Pass() implements PythonNode {}
    record Assign(List<PythonNode> targets, PythonNode value) implements PythonNode {}
    record AugAssign(PythonNode target, String op, PythonNode value) implements PythonNode {}
    record ExprStmt(PythonNode expr) implements PythonNode {}
    record Raise(PythonNode exc, PythonNode from) implements PythonNode {}   // exc == null → bare raise; from ignored
    record Yield(PythonNode value, boolean from) implements PythonNode {}    // value null → bare yield; from → yield from
    record Assert(PythonNode cond, PythonNode msg) implements PythonNode {}  // msg == null → bare assert
    record Del(List<PythonNode> targets) implements PythonNode {}
    record Import(List<Spec> specs) implements PythonNode {}                 // import m [as a], ...
    record ImportFrom(String module, List<Spec> specs, boolean star) implements PythonNode {}   // from m import ...

    // ---- expressions ----
    record IntLit(long value) implements PythonNode {}
    record FloatLit(double value) implements PythonNode {}
    record StrLit(String value) implements PythonNode {}
    record FString(List<PythonNode> parts) implements PythonNode {}   // part: StrLit (literal) | expression | Formatted
    record Formatted(PythonNode expr, String spec, String conv) implements PythonNode {}  // f-string {expr[:spec][!conv]}
    record BoolLit(boolean value) implements PythonNode {}
    record NoneLit() implements PythonNode {}
    record Name(String id) implements PythonNode {}
    record Attribute(PythonNode obj, String attr) implements PythonNode {}
    record Index(PythonNode obj, PythonNode index) implements PythonNode {}     // index may be a Slice
    record Slice(PythonNode lower, PythonNode upper, PythonNode step) implements PythonNode {}   // any nullable
    record Call(PythonNode func, List<PythonNode> args) implements PythonNode {}     // an arg may be a Kwarg
    record Kwarg(String name, PythonNode value) implements PythonNode {}              // name=value at a call site
    record Unary(String op, PythonNode operand) implements PythonNode {}
    record Binary(String op, PythonNode left, PythonNode right) implements PythonNode {}
    record Compare(PythonNode left, String op, PythonNode right) implements PythonNode {}
    record Ternary(PythonNode cond, PythonNode ifTrue, PythonNode ifFalse) implements PythonNode {}
    record Walrus(String name, PythonNode value) implements PythonNode {}  // named expression (:=) → (name = value)
    record ListLit(List<PythonNode> elements) implements PythonNode {}
    record TupleLit(List<PythonNode> elements) implements PythonNode {}
    record DictLit(List<PythonNode> keys, List<PythonNode> values) implements PythonNode {}
    record SetLit(List<PythonNode> elements) implements PythonNode {}
    record Lambda(List<Param> params, PythonNode body) implements PythonNode {}
    record ListComp(PythonNode element, List<CompClause> clauses) implements PythonNode {}
    record DictComp(PythonNode key, PythonNode value, List<CompClause> clauses) implements PythonNode {}
    record SetComp(PythonNode element, List<CompClause> clauses) implements PythonNode {}
    record Try(List<PythonNode> body, List<ExceptClause> excepts, List<PythonNode> elseBody,
               List<PythonNode> finallyBody)
            implements PythonNode {}                                    // else/finally empty → absent
    record With(List<WithItem> items, List<PythonNode> body) implements PythonNode {}   // 1+ context-manager items

    /** Function/lambda parameter (helper, not itself a node).
     *  starArg → {@code *name} (varargs); kwDict → {@code **name} (keyword dict). */
    record Param(String name, PythonNode defaultValue, boolean starArg, boolean kwDict) {}

    /** Import spec (helper, not itself a node). name = module (import) or imported name (from-import); alias null = none. */
    record Spec(String name, String alias) {}

    /** {@code except} clause (helper, not itself a node). types empty → bare except; name null → no binding. */
    record ExceptClause(List<PythonNode> types, String name, List<PythonNode> body) {}

    /** {@code with} item (helper, not itself a node). target == null → no {@code as} binding. */
    record WithItem(PythonNode context, PythonNode target) {}

    /** Comprehension clause (helper, not itself a node): a {@code for} loop or an {@code if} guard. */
    sealed interface CompClause permits ForComp, IfComp {}
    record ForComp(PythonNode target, PythonNode iter) implements CompClause {}
    record IfComp(PythonNode cond) implements CompClause {}
}
