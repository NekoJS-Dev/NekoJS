package com.tkisor.nekojs.api.compiler;

public record NekoIRBinding(String name, String kind, int scopeId, Object nativeBinding) implements NekoIRNode {
}
