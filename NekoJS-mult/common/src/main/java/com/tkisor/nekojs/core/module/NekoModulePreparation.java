package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.ScriptTypeEnv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <strong>Module Resolution/Cache</strong> 的 prepared module cache（工单 11）：持有实例级
 * prepared module cache 与 source map 发布，prepare 逻辑委托给注入的 {@link NekoModulePipeline}。
 *
 * <p><b>所有权（工单 11 核心）：</b>本对象<strong>没有</strong> process-wide static 字段，
 * 也<strong>没有</strong> legacy 静态门面回退。它由 runtime owner（{@code NekoRuntimeRoot}）
 * 创建并持有，随 root close 释放；测试各自构造独立实例，互不污染。
 *
 * <p><b>命中判定（AC1/AC4）：</b>cache 命中要求文件 stamp（mtime + size + SHA-256 内容指纹）
 * <em>与</em> {@link NekoModuleIdentity}（language id + 请求 mode）都相等。只比 stamp 会在
 * 插件换语言后返回旧语言编译的模块——「内容、路径、mode 或 language identity 变化会失效对应
 * cache」中的 mode/language 维度靠 identity 保证。
 *
 * <p><b>按类型清理（AC5）：</b>{@link #clear(ScriptType)} 只清目标类型子树；不在任何类型脚本
 * 目录下的 key（如 {@code node_modules}，跨类型共享）不受影响。
 */
public final class NekoModulePreparation {

    private final NekoModulePipeline pipeline;
    private final NekoJSPaths paths;
    private final Map<Path, PreparedEntry> preparedCache = new ConcurrentHashMap<>();

    public NekoModulePreparation(NekoModulePipeline pipeline, NekoJSPaths paths) {
        this.pipeline = pipeline;
        this.paths = paths;
    }

    public NekoModulePipeline pipeline() {
        return pipeline;
    }

    /**
     * 准备（或取回已缓存的）模块。
     *
     * <p>Atomic check-then-act：{@code compute} 保证同一 (key, stamp, identity) 只跑一次管线；
     * 内层 stamp + identity 检查避免缓存仍有效时重复计算，也保证旧语言/旧内容的条目被替换。
     */
    public NekoPreparedModule prepare(Path path) throws IOException {
        Path key = key(path);
        try {
            SourceSnapshot source = readSource(key);
            NekoModuleIdentity identity = pipeline.identityOf(key);
            PreparedEntry entry = preparedCache.compute(key, (k, existing) -> {
                if (existing != null && existing.stamp().equals(source.stamp()) && existing.identity().equals(identity)) {
                    return existing;
                }
                try {
                    NekoPreparedModule prepared = pipeline.prepare(k, source.source());
                    return new PreparedEntry(source.stamp(), identity, prepared, scriptTypeOf(k));
                } catch (IOException | RuntimeException ex) {
                    throw new PipelineException(ex);
                } catch (Exception ex) {
                    // pipeline.prepare declares 'throws Exception'; tunnel other checked
                    // exceptions out of the compute lambda the same way.
                    throw new PipelineException(ex);
                }
            });
            publishSourceMap(key, entry.prepared());
            return entry.prepared();
        } catch (PipelineException wrapper) {
            Throwable cause = wrapper.getCause();
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

    /** 清空本实例全部 prepared 条目与对应 source map（全清语义）。 */
    public void clear() {
        preparedCache.clear();
        SourceMapRegistry.clear();
    }

    /**
     * 仅清空指定 {@link ScriptType} 的 prepared 模块缓存与对应 source map。
     *
     * <p>各类型脚本根目录为 {@code nekojs/<name>_scripts} 下的互不相交子树，故按 key 推导所属
     * 类型；不在任何类型脚本目录下的 key（如 node_modules，跨类型共享）不受影响。
     */
    public void clear(ScriptType type) {
        if (type == null) {
            clear();
            return;
        }
        preparedCache.entrySet().removeIf(entry -> entry.getValue().type() == type);
        SourceMapRegistry.clearByScriptType(type);
    }

    /** 失效单个路径的 prepared 条目与 source map。 */
    public void invalidate(Path path) {
        if (path == null) {
            return;
        }
        Path key = key(path);
        preparedCache.remove(key);
        relativePath(key).ifPresent(SourceMapRegistry::clear);
    }

    /** 当前缓存的条目数（测试/诊断观察面，不暴露内部容器）。 */
    public int size() {
        return preparedCache.size();
    }

    /** 某路径当前是否已缓存（测试/诊断观察面）。 */
    public boolean isCached(Path path) {
        return path != null && preparedCache.containsKey(key(path));
    }

    private SourceSnapshot readSource(Path path) throws IOException {
        String source = Files.readString(path);
        return new SourceSnapshot(FileStamp.read(path, source), source);
    }

    private void publishSourceMap(Path path, NekoPreparedModule prepared) {
        relativePath(path).ifPresent(relative -> SourceMapRegistry.register(
                relative, prepared.sourceMap(), prepared.prependedLineCount()));
    }

    private Path key(Path path) {
        return path.normalize().toAbsolutePath();
    }

    private Optional<String> relativePath(Path path) {
        try {
            return Optional.of(paths.root().relativize(path).toString().replace('\\', '/'));
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
    private ScriptType scriptTypeOf(Path key) {
        if (key == null) {
            return null;
        }
        try {
            Path root = paths.root().normalize().toAbsolutePath();
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

    private record PreparedEntry(FileStamp stamp, NekoModuleIdentity identity, NekoPreparedModule prepared, ScriptType type) {}

    /**
     * 模块文件的内容指纹。
     *
     * <p>历史缺陷：仅 (modifiedMillis, size) 无法区分"同一时间戳刻度内对等长文件的覆盖写入"，
     * 粗粒度时间戳文件系统（如部分 Windows / FAT / 容器挂载）会因此误判未变化，继续返回旧编译模块。
     * 修复：增加 {@code contentHash}（见 {@link NekoContentHash}）。
     */
    private record FileStamp(long modifiedMillis, long size, String contentHash) {
        private static FileStamp read(Path path, String source) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path), NekoContentHash.sha256(source));
        }
    }
}
