package com.tkisor.nekojs.api.compiler;

public record ScriptCompileResult(String code, String sourceMap) {
    public ScriptCompileResult {
        if (code == null) {
            code = "";
        }
    }

    public static ScriptCompileResult codeOnly(String code) {
        return new ScriptCompileResult(code, null);
    }
}
