package com.tkisor.nekojs.api.compiler;

public interface NekoParser {
    NekoSourceAst parse(NekoTokenStream tokens) throws Exception;
}
