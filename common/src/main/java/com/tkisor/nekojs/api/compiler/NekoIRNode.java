package com.tkisor.nekojs.api.compiler;

public sealed interface NekoIRNode permits NekoIRProgram, NekoIRImport, NekoIRExport, NekoIRBinding, NekoIRScope {
}
