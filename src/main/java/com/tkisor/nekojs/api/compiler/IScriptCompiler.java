package com.tkisor.nekojs.api.compiler;

import java.nio.file.Path;

/**
 * NekoJS 脚本编译器扩展接口
 */
public interface IScriptCompiler {
    // 匹配文件后缀 (如 ".ts", ".tsx")
    boolean canCompile(String extension);

    // 接收原始文件和源码，返回编译后的 JS 字符串
    String compile(Path file, String sourceCode) throws Exception;
}