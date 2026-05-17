package com.tkisor.nekojs.api.compiler;

public record NekoIRImport(String specifier, Object nativeImport) implements NekoIRNode {
}
