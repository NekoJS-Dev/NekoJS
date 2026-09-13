package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoModuleMode;

import java.nio.file.Path;

/**
 * prepared module 的<strong>稳定 cache key</strong>（工单 11 / 规格 06：prepared module 必须携带
 * language id、module mode、code/IR、source map、诊断位置和稳定 cache key）。
 *
 * <p>key 由四个正交维度组成，任一变化都产生不同的 key：
 * <ol>
 *   <li>{@code languageId} —— 语言身份（编译器注册表解析结果）；</li>
 *   <li>{@code mode} —— 扩展名请求的模块模式（AUTO / COMMONJS / ESM）；</li>
 *   <li>{@code sourcePath} —— 源文件路径（规范化绝对路径，'/' 分隔）；</li>
 *   <li>{@code contentHash} —— 原始源内容指纹（SHA-256，见 {@link NekoContentHash}）。</li>
 * </ol>
 *
 * <p>注意 key 记录的是<strong>请求</strong>模式而非解析后模式：{@code .js} 的 AUTO 要等管线跑完
 * 才知道最终是 CJS 还是 ESM，而 cache 命中判定必须在编译之前完成（否则缓存就没有意义）。
 * 解析后的模式由 {@link NekoPreparedModule#mode()} 单独暴露。
 *
 * <p>不可变 record；{@link #value()} 是跨调用稳定的字符串形式，只用于日志/诊断展示。
 */
public record NekoModuleCacheKey(
        String languageId,
        NekoModuleMode mode,
        String sourcePath,
        String contentHash
) {
    /** 无源文件的内存模块（测试/合成输入）的 sourcePath 占位符。 */
    public static final String INLINE_SOURCE_PATH = "<inline>";

    public NekoModuleCacheKey {
        languageId = languageId == null || languageId.isBlank() ? "unknown" : languageId;
        mode = mode == null ? NekoModuleMode.AUTO : mode;
        sourcePath = sourcePath == null ? "" : sourcePath;
        contentHash = contentHash == null ? "" : contentHash;
    }

    public static NekoModuleCacheKey of(String languageId, NekoModuleMode mode, Path sourcePath, String rawSource) {
        String normalizedPath = sourcePath == null
                ? ""
                : sourcePath.normalize().toAbsolutePath().toString().replace('\\', '/');
        return new NekoModuleCacheKey(languageId, mode, normalizedPath, NekoContentHash.sha256(rawSource));
    }

    /** 无源文件输入（合成/测试）的 key：路径固定为 {@link #INLINE_SOURCE_PATH}，内容指纹取 code。 */
    public static NekoModuleCacheKey ofInline(String languageId, NekoModuleMode mode, String code) {
        return new NekoModuleCacheKey(languageId, mode, INLINE_SOURCE_PATH, NekoContentHash.sha256(code));
    }

    /** 语言身份部分（用于缓存命中时判断「同一文件是否已换了语言/模式」）。 */
    public NekoModuleIdentity identity() {
        return new NekoModuleIdentity(languageId, mode);
    }

    public boolean identityMatches(NekoModuleIdentity expected) {
        return expected != null && identity().equals(expected);
    }

    /** 稳定字符串形式（诊断/日志）。内容指纹取前 16 hex，完整指纹不用于展示。 */
    public String value() {
        String shortHash = contentHash.length() >= 16 ? contentHash.substring(0, 16) : contentHash;
        return languageId + "|" + mode.name() + "|" + sourcePath + "|" + shortHash;
    }

    @Override
    public String toString() {
        return value();
    }
}
