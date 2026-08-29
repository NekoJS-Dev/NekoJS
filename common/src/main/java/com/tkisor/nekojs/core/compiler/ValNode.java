package com.tkisor.nekojs.core.compiler;

import java.util.List;
import java.util.Map;

public sealed interface ValNode permits
    ValNode.Identifier,
    ValNode.StringLiteral,
    ValNode.NumberLiteral,
    ValNode.MemberAccess,
    ValNode.ComputedMemberAccess,
    ValNode.VarDecl,
    ValNode.ArrowFunc,
    ValNode.FuncDecl,
    ValNode.CallExpr,
    ValNode.Block
{
    int start();
    int end();

    enum DeclarationKind { CONST, LET, VAR }

    record Identifier(String name, int start, int end) implements ValNode {}
    record StringLiteral(String value, int start, int end) implements ValNode {}
    record NumberLiteral(String raw, int start, int end) implements ValNode {}
    record MemberAccess(ValNode object, String member, boolean bracket, int start, int end) implements ValNode {}
    record ComputedMemberAccess(ValNode object, ValNode key, boolean optional, int start, int end) implements ValNode {}
    record VarDecl(DeclarationKind kind, String name, ValNode init, int start, int end) implements ValNode {}
    record ArrowFunc(List<String> params, List<ValNode> body, int start, int end) implements ValNode {}
    record FuncDecl(String name, List<String> params, List<ValNode> body, int start, int end) implements ValNode {}
    record CallExpr(ValNode callee, List<ValNode> args, int start, int end) implements ValNode {}
    record Block(List<ValNode> stmts, Block parent, Map<String, VarDecl> scope, int start, int end) implements ValNode {}
}
