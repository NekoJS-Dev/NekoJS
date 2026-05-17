package com.tkisor.nekojs.api.compiler;

public record NekoIRExport(String specifier, Object nativeExport) implements NekoIRNode {
}
