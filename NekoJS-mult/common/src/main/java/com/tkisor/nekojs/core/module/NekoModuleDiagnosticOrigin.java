package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

/**
 * prepared module 的<strong>原始诊断位置</strong>（工单 11 / 规格 06）：执行期错误要能回到
 * 原始文件与行列，需要"这份可执行代码来自哪个源文件"以及"生成代码相对原始源偏移了几行"。
 *
 * <p>{@code prependedLineCount} 是语言 frontend 在产物前插入的无源对应行数（如自动注入的
 * helper 头），source map 查表前先减去它才能得到原始行号。
 *
 * @param sourcePath         原始源文件路径（合成/内存输入为 null）
 * @param prependedLineCount 产物前置的无源对应行数（>= 0）
 */
public record NekoModuleDiagnosticOrigin(Path sourcePath, int prependedLineCount) {

    public NekoModuleDiagnosticOrigin {
        prependedLineCount = Math.max(0, prependedLineCount);
    }
}
