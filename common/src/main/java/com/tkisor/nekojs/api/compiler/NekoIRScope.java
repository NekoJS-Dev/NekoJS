package com.tkisor.nekojs.api.compiler;

public record NekoIRScope(int id, int parentId, Object nativeScope) implements NekoIRNode {
}
