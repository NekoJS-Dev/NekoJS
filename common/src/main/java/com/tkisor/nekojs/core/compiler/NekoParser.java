package com.tkisor.nekojs.core.compiler;

public interface NekoParser {
    NekoSourceAst parse(NekoTokenStream tokens) throws Exception;
}
