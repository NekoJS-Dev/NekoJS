package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块准备缓存（Script Preparation + Module Resolution/Cache 的 prepared 层）：
 * 持有 prepared module cache 并发布 source map；prepare 逻辑委托给构造器注入的
 * {@link NekoModulePipeline} 实例。
 *
 * <p>票据 11（W3）显式注入形态：实例由 runtime owner（{@code NekoRuntimeRoot}）持有，
 * 经构造器装配进 ScriptManager / module host / ESM linker / source rewriter /
 * reload coordinator / sandbox filesystem——生产代码不再调用任何 process-wide static
 * cache。历史 static {@code PREPARED_CACHE / prepare / clear / invalidate} 已随本票删除
 * （05 总账 A7/A8 的删除条件即“W3 显式注入后删除”；替代 behavior 见
 * {@code NekoModuleCacheInvalidationTest}，trace 见 {@code ModulePipelineIsolationTest}）。
 * root close 时清空本实例（生命周期归属见 {@code NekoRuntimeRoot#closeSilently}）；
 * 直接构造的测试/manager 各自持有隔离实例，互不污染。
 *
 * <p>失效口径：同一路径键下，mtime/size/内容哈希/language id/requested mode 任一变化即
 * 失效（{@link FileStamp} 五元组）——同 stamp 同长度但内容不同的覆盖写入不会返回旧模块；
 * 路径/mode 变化天然落到不同键或不同 stamp。
 */
public final class NekoModulePipelineCache {
    private final NekoModulePipeline pipeline;
    private final Map<Path, PreparedEntry> preparedCache = new ConcurrentHashMap<>();

    public NekoModulePipelineCache(NekoModulePipeline pipeline) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
    }

    /**
     * 兼容装配入口：用显式 registry + config 构造直连实例（旧 static 调用点的替换目标；
     * 每个调用方持有独立实例——生产装配请共享同一个 root 拥有的实例）。
     */
    public static NekoModulePipelineCache withExplicitPipeline(ScriptCompilerRegistry compilers,
                                                               SandboxConfig config) {
        return new NekoModulePipelineCache(
                new NekoModulePipeline(new NekoCompilationPipeline(), compilers, config));
    }

    public NekoPreparedModule prepare(Path path) throws IOException {
        Path key = key(path);
        try {
            SourceSnapshot source = readSource(key);
            // Atomic check-then-act: previously get→miss→compute→put could let two threads
            // loading the same path both run the full pipeline. computeIfAbsent guarantees
            // a single pipeline run per (key, stamp); the inner stamp check avoids recomputing
            // when the cached entry is still valid for this source stamp.
            PreparedEntry entry = preparedCache.compute(key, (k, existing) -> {
                if (existing != null && existing.stamp().equals(source.stamp())) {
                    return existing;
                }
                try {
                    NekoPreparedModule prepared = prepareSource(k, source);
                    return new PreparedEntry(source.stamp(), prepared, scriptTypeOf(k));
                } catch (IOException | RuntimeException ex) {
                    throw new PipelineException(ex);
                } catch (Exception ex) {
                    // prepareSource declares 'throws Exception'; tunnel other checked
                    // exceptions out of the compute lambda the same way.
                    throw new PipelineException(ex);
                }
            });
            publishSourceMap(key, entry.prepared());
            return entry.prepared();
        } catch (PipelineException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof NekoModuleError staged) {
                // 管线阶段错误原样透传：PREPARE 不重标为 CACHE，语言位置不丢失。
                if (cause instanceof IOException io) {
                    throw io;
                }
                throw new IOException(staged.detail(), staged);
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + rootMessage(cause), cause);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + rootMessage(e), e);
        }
    }

    /** Internal carrier to tunnel checked exceptions out of the ConcurrentHashMap compute lambda. */
    private static final class PipelineException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        PipelineException(Throwable cause) { super(cause); }
    }

    /** 清空本实例的全部 prepared 条目与对应 source map（runtime owner 释放语义）。 */
    public void clear() {
        preparedCache.clear();
        SourceMapRegistry.clear();
    }

    /**
     * 仅清空本实例中指定 {@link ScriptType} 的 prepared 模块缓存与对应 source map。
     * 各类型脚本根目录为 {@code nekojs/<name>_scripts} 下的互不相交子树，故按 key 推导所属类型；
     * 不在任何类型脚本目录下的 key（如 node_modules，跨类型共享）不受影响。无参 {@link #clear()}
     * 保持全清语义，供 ModuleReloadCoordinator 等显式全清路径使用。
     */
    public void clear(ScriptType type) {
        if (type == null) {
            clear();
            return;
        }
        preparedCache.entrySet().removeIf(entry -> entry.getValue().type() == type);
        SourceMapRegistry.clearByScriptType(type);
    }

    public void invalidate(Path path) {
        if (path == null) {
            return;
        }
        Path key = key(path);
        preparedCache.remove(key);
        relativePath(key).ifPresent(SourceMapRegistry::clear);
    }

    /** 测试/诊断观察面：本实例当前缓存条目数。 */
    int size() {
        return preparedCache.size();
    }

    private SourceSnapshot readSource(Path path) throws IOException {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException failure) {
            throw NekoModuleError.cache(displayPath(path), "Cannot read module source: " + failure.getMessage(), failure);
        }
        NekoModulePipeline.ModuleDescriptor descriptor;
        try {
            descriptor = pipeline.describe(path);
        } catch (Exception failure) {
            throw NekoModuleError.prepare(displayPath(path), "unknown", NekoModuleMode.AUTO,
                    rootMessage(failure), failure);
        }
        FileStamp stamp;
        try {
            stamp = FileStamp.read(path, source, descriptor.languageId(), descriptor.requestedMode());
        } catch (IOException failure) {
            throw NekoModuleError.cache(displayPath(path), "Cannot stamp module source: " + failure.getMessage(), failure);
        }
        return new SourceSnapshot(stamp, source);
    }

    private NekoPreparedModule prepareSource(Path path, SourceSnapshot source) throws Exception {
        return pipeline.prepare(path, source.source());
    }

    private static void publishSourceMap(Path path, NekoPreparedModule prepared) {
        relativePath(path).ifPresent(relativePath -> SourceMapRegistry.register(relativePath, prepared.sourceMap(), prepared.prependedLineCount()));
    }

    private static Path key(Path path) {
        return path.normalize().toAbsolutePath();
    }

    private static String displayPath(Path path) {
        return path == null ? "<unknown>" : path.toString().replace('\\', '/');
    }

    private static Optional<String> relativePath(Path path) {
        try {
            return Optional.of(NekoJSPaths.get().root().relativize(path).toString().replace('\\', '/'));
        } catch (Exception ignored) { // relative path computation fails → cache miss
            return Optional.empty();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.toString() : message;
    }

    /**
     * 从缓存 key（模块文件绝对路径）推导所属 {@link ScriptType}：脚本根目录遵循
     * {@code <name>_scripts} 命名约定（与 {@link SourceMapRegistry#clearByScriptType} 的
     * root-relative 前缀约定一致），key 相对 nekojs root 的第一个路径段即类型目录名。
     * 不在任何类型目录下（如 node_modules）的 key 是跨类型共享缓存，返回 null。
     */
    private static ScriptType scriptTypeOf(Path key) {
        if (key == null) {
            return null;
        }
        try {
            Path root = NekoJSPaths.get().root().normalize().toAbsolutePath();
            if (!key.startsWith(root)) {
                return null;
            }
            Path relative = root.relativize(key);
            if (relative.getNameCount() < 1) {
                return null;
            }
            String first = relative.getName(0).toString();
            for (ScriptType type : ScriptType.all()) {
                Path typePath = ScriptTypeEnv.scriptsDir(type);
                String dirName = typePath == null ? type.name + "_scripts" : typePath.getFileName().toString();
                // Windows 文件系统大小写不敏感：手建的 Server_scripts 目录同样落在 SERVER
                // 类型子树内（Path.startsWith 在 Windows 上本就忽略大小写），这里必须同等
                // 忽略大小写匹配，否则该目录下的模块会被误标为跨类型共享缓存、逃脱按类型清理
                if (first.equalsIgnoreCase(dirName)) {
                    return type;
                }
            }
        } catch (Exception ignored) { // 路径解析失败 → 视为共享缓存
        }
        return null;
    }

    private record SourceSnapshot(FileStamp stamp, String source) {}

    private record PreparedEntry(FileStamp stamp, NekoPreparedModule prepared, ScriptType type) {}

    /**
     * 模块文件的内容指纹（五元组：mtime/size/contentHash/languageId/requestedMode）。
     *
     * <p>历史缺陷：仅 (modifiedMillis, size) 无法区分“同一时间戳刻度内对等长文件的覆盖写入”，
     * 粗粒度时间戳文件系统（如部分 Windows / FAT / 容器挂载）会因此误判未变化，继续返回旧编译模块。
     * contentHash（SHA-256）修复该缺陷；W3 追加 languageId/requestedMode：同一路径在语言插件
     * 替换（同扩展名改注册）或 requested mode 变化时也必须失效，否则缓存返回旧语言/旧模式模块。
     */
    private record FileStamp(long modifiedMillis, long size, String contentHash,
                             String languageId, NekoModuleMode requestedMode) {
        private static FileStamp read(Path path, String source, String languageId,
                                      NekoModuleMode requestedMode) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path),
                    contentHash(source), languageId, requestedMode);
        }

        private static String contentHash(String source) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                // JDK 规范要求 SHA-256 算法必须存在；这里作为环境缺陷快速失败。
                throw new IllegalStateException("SHA-256 digest is not available on this JVM", e);
            }
        }
    }
}
