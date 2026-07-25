package com.tkisor.nekojs.core.compiler;

import java.util.List;
import java.util.Map;

public sealed interface ValNode permits
    ValNode.Identifier,
    ValNode.MemberAccess,
    ValNode.VarDecl,
    ValNode.ArrowFunc,
    ValNode.FuncDecl,
    ValNode.CallExpr,
    ValNode.Block
{
    int start();
    int end();

    record Identifier(String name, int start, int end) implements ValNode {}
    record MemberAccess(ValNode object, String member, boolean bracket, int start, int end) implements ValNode {}
    record VarDecl(String name, ValNode init, int start, int end) implements ValNode {}
    record ArrowFunc(List<String> params, List<ValNode> body, int start, int end) implements ValNode {}
    record FuncDecl(String name, List<String> params, List<ValNode> body, int start, int end) implements ValNode {}
    record CallExpr(ValNode callee, List<ValNode> args, int start, int end) implements ValNode {}
    record Block(List<ValNode> stmts, Block parent, Map<String, VarDecl> scope, int start, int end) implements ValNode {}
}
