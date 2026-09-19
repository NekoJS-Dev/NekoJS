package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.compiler.NekoCompilationPipeline;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

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
 * {@code NekoModulePipelineCacheStampTest}，trace 见 {@code ModulePipelineIsolationTest}）。
 * root close 时清空本实例（生命周期归属见 {@code NekoRuntimeRoot#closeSilently}）；
 * 直接构造的测试/manager 各自持有隔离实例，互不污染。
 *
 * <p>失效口径：同一路径键下，mtime/size/内容哈希/language id/requested mode 任一变化即
 * 失效（{@link FileStamp} 五元组）——同 stamp 同长度但内容不同的覆盖写入不会返回旧模块；
 * 路径/mode 变化天然落到不同键或不同 stamp。
 */
public final class NekoModulePipelineCache implements AutoCloseable {
    private final NekoModulePipeline pipeline;
    private final Map<Path, PreparedEntry> preparedCache = new ConcurrentHashMap<>();
    private final SourceMapRegistry sourceMaps;
    private final NekoEsmVirtualModuleRegistry virtualModules;
    private final NekoTrustContext trustContext;
    private final CopyOnWriteArrayList<BiConsumer<Path, String>> preparationObservers = new CopyOnWriteArrayList<>();
    private final NekoModulePipelineCache owner;
    private final CopyOnWriteArrayList<NekoModulePipelineCache> sessions = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    public NekoModulePipelineCache(NekoModulePipeline pipeline, SourceMapRegistry sourceMaps,
                                   NekoEsmVirtualModuleRegistry virtualModules,
                                   NekoTrustContext trustContext) {
        this(pipeline, sourceMaps, virtualModules, trustContext, null);
    }

    private NekoModulePipelineCache(NekoModulePipeline pipeline, SourceMapRegistry sourceMaps,
                                    NekoEsmVirtualModuleRegistry virtualModules,
                                    NekoTrustContext trustContext, NekoModulePipelineCache owner) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.sourceMaps = Objects.requireNonNull(sourceMaps, "sourceMaps");
        this.virtualModules = Objects.requireNonNull(virtualModules, "virtualModules");
        this.trustContext = Objects.requireNonNull(trustContext, "trustContext");
        this.owner = owner;
    }

    /**
     * Open a generation-owned module session under this runtime owner.
     *
     * <p>The pipeline, trust context and path roots are shared immutable policy, while prepared
     * entries, source maps, virtual sources and preparation observations are private to the
     * returned session. Calling this on a child still registers the session with the root owner;
     * it never creates a second long-lived runtime owner.
     */
    public NekoModulePipelineCache openSession() {
        NekoModulePipelineCache root = rootOwner();
        if (root.closed) {
            throw new IllegalStateException("NekoModulePipelineCache owner is closed");
        }
        NekoModulePipelineCache session = new NekoModulePipelineCache(
                root.pipeline,
                new SourceMapRegistry(root.sourceMaps.root()),
                new NekoEsmVirtualModuleRegistry(root.virtualModules.root().getParent()),
                root.trustContext,
                root);
        root.sessions.add(session);
        return session;
    }

    /** Close a generation session and discard only its mutable module state. */
    public void closeSession() {
        if (owner == null) {
            clear();
            return;
        }
        clearLocal();
        closed = true;
        owner.sessions.remove(this);
    }

    /** Close the runtime owner and all generation sessions; unlike clear(), this is terminal. */
    public void closeOwner() {
        if (owner != null) {
            throw new IllegalStateException("Only the root NekoModulePipelineCache can close its owner");
        }
        if (closed) {
            return;
        }
        closed = true;
        for (NekoModulePipelineCache session : sessions.toArray(NekoModulePipelineCache[]::new)) {
            session.closeSession();
        }
        clearLocal();
    }

    @Override
    public void close() {
        if (owner == null) {
            closeOwner();
        } else {
            closeSession();
        }
    }

    /** Whether both caches belong to the same root runtime owner. */
    public boolean belongsToSameOwner(NekoModulePipelineCache other) {
        return other != null && rootOwner() == other.rootOwner();
    }

    public NekoPreparedModule prepare(Path path) throws IOException {
        ensureOpen();
        Path key = key(path);
        NekoTrustApprovedSource approval = approvedSource(key);
        try {
            SourceSnapshot source = readSource(key);
            // Atomic check-then-act: previously get→miss→compute→put could let two threads
            // loading the same path both run the full pipeline. compute guarantees
            // a single pipeline run per (key, stamp); the inner stamp check avoids recomputing
            // when the cached entry is still valid for this source stamp.
            PreparedEntry entry = preparedCache.compute(key, (k, existing) -> {
                if (existing != null && existing.stamp().equals(source.stamp())) {
                    return existing;
                }
                try {
                    NekoPreparedModule prepared = prepareSource(k, source, approval);
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
            for (BiConsumer<Path, String> observer : preparationObservers) {
                observer.accept(key, entry.prepared().cacheKey());
            }
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
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + NekoModuleError.rootMessage(cause), cause);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to prepare NekoJS module: " + key + ": " + NekoModuleError.rootMessage(e), e);
        }
    }

    /**
     * Prepare a JSON source through the same trust boundary as executable modules.
     * JSON has no language compiler, but it is still a resolved module source and must
     * not be read by an execution-side bypass.
     */
    public String prepareJson(Path path) throws IOException {
        ensureOpen();
        Path key = key(path);
        approvedSource(key);
        try {
            String source = Files.readString(key);
            String executionKey = jsonExecutionKey(key, source);
            for (BiConsumer<Path, String> observer : preparationObservers) {
                observer.accept(key, executionKey);
            }
            return source;
        } catch (IOException failure) {
            throw NekoModuleError.cache(NekoModuleError.displayPath(key),
                    "Cannot read JSON module source: " + failure.getMessage(), failure);
        }
    }

    /** Internal carrier to tunnel checked exceptions out of the ConcurrentHashMap compute lambda. */
    private static final class PipelineException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        PipelineException(Throwable cause) { super(cause); }
    }

    /** 清空本实例的全部 prepared 条目与对应 source map（runtime owner 释放语义）。 */
    public void clear() {
        if (owner == null) {
            for (NekoModulePipelineCache session : sessions.toArray(NekoModulePipelineCache[]::new)) {
                session.closeSession();
            }
        }
        clearLocal();
    }

    private void clearLocal() {
        preparedCache.clear();
        sourceMaps.clear();
        virtualModules.clear();
        preparationObservers.clear();
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
        if (owner == null) {
            for (NekoModulePipelineCache session : sessions) {
                session.clear(type);
            }
        }
        preparedCache.entrySet().removeIf(entry -> entry.getValue().type() == type);
        sourceMaps.clearByScriptType(type);
        virtualModules.clear(type);
    }

    public void invalidate(Path path) {
        ensureOpen();
        if (path == null) {
            return;
        }
        Path key = key(path);
        preparedCache.remove(key);
        relativePath(key).ifPresent(sourceMaps::clear);
    }

    /** Controlled read-only diagnostic: number of prepared entries owned by this runtime. */
    public int preparedEntryCount() {
        int count = preparedCache.size();
        if (owner == null) {
            for (NekoModulePipelineCache session : sessions) {
                count += session.preparedEntryCount();
            }
        }
        return count;
    }

    SourceMapRegistry sourceMaps() {
        return sourceMaps;
    }

    NekoEsmVirtualModuleRegistry virtualModules() {
        return virtualModules;
    }

    /** Host-owned observation seam for direct linker/rewriter preparation calls. */
    BiConsumer<Path, String> registerPreparationObserver(BiConsumer<Path, String> observer) {
        if (observer != null) {
            preparationObservers.add(observer);
        }
        return observer;
    }

    /** Narrow unregister seam used by a host/context close path. */
    void unregisterPreparationObserver(BiConsumer<Path, String> observer) {
        if (observer != null) {
            preparationObservers.remove(observer);
        }
    }

    /** Single owner for JSON execution identity used by preparation and host cache checks. */
    String jsonExecutionKey(Path path, String source) {
        Path key = key(path);
        String moduleId = relativePath(key).orElse(key.toString().replace('\\', '/'));
        return NekoModuleHash.jsonExecutionKey(moduleId, source);
    }

    /**
     * Compose the map for a virtual rewritten module. Native unchanged ESM keeps the prepared
     * compiler/identity map; a length-changing rewrite gets a conservative generated-line map
     * whose columns intentionally resolve to the authored line start.
     */
    String composeRewrittenSourceMap(NekoPreparedModule prepared, String generatedSource) {
        if (prepared == null || prepared.sourceMap() == null || prepared.sourceMap().isBlank()) {
            return null;
        }
        if (Objects.equals(prepared.code(), generatedSource)) {
            return prepared.sourceMap();
        }
        String authoredSource = prepared.code();
        try {
            var root = JsonParser.parseString(prepared.sourceMap()).getAsJsonObject();
            var contents = root.getAsJsonArray("sourcesContent");
            if (contents != null && !contents.isEmpty() && !contents.get(0).isJsonNull()) {
                authoredSource = contents.get(0).getAsString();
            }
        } catch (RuntimeException ignored) {
            // The prepared map was already accepted; fall back to a legal conservative map.
        }
        return NekoSourceMapBuilder.identity(Path.of(prepared.sourcePath()), authoredSource, generatedSource);
    }

    /** Controlled read-only view for execution-side filesystem integration. */
    public NekoVirtualModuleView virtualModuleView() {
        return virtualModules;
    }

    /** Controlled read-only view for execution-side diagnostics. */
    public com.tkisor.nekojs.core.error.NekoSourceMapView sourceMapView() {
        return sourceMaps;
    }

    NekoTrustApprovedSource approvedSource(Path path) throws IOException {
        ensureOpen();
        NekoTrustApprovedSource approval = trustContext.approvalFor(path);
        if (approval == null || !approval.covers(path)) {
            NekoModuleIdentity identity;
            try {
                identity = pipeline.identify(path);
            } catch (RuntimeException unsupportedExtension) {
                // JSON is prepared as data rather than as a language module. Trust must still
                // reject it at the authorization boundary before compiler discovery runs.
                if (!isJson(path)) {
                    throw NekoModuleError.denied(NekoModuleError.displayPath(path), "unknown",
                            NekoModuleMode.AUTO, approval, "module dependency has no valid trust context");
                }
                identity = new NekoModuleIdentity("json", NekoModuleMode.AUTO);
            }
            throw NekoModuleError.denied(NekoModuleError.displayPath(path), identity.languageId(), identity.requestedMode(),
                    approval, "module dependency has no valid trust context");
        }
        return approval;
    }

    private SourceSnapshot readSource(Path path) throws IOException {
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException failure) {
            throw NekoModuleError.cache(NekoModuleError.displayPath(path), "Cannot read module source: " + failure.getMessage(), failure);
        }
        NekoModulePipeline.LanguageBinding binding;
        try {
            binding = pipeline.captureBinding(path);
        } catch (Exception failure) {
            throw NekoModuleError.prepare(NekoModuleError.displayPath(path), "unknown", NekoModuleMode.AUTO,
                    NekoModuleError.rootMessage(failure), failure);
        }
        FileStamp stamp;
        try {
            stamp = FileStamp.read(path, source, binding.identity());
        } catch (IOException failure) {
            throw NekoModuleError.cache(NekoModuleError.displayPath(path), "Cannot stamp module source: " + failure.getMessage(), failure);
        }
        return new SourceSnapshot(stamp, source, binding);
    }

    private NekoPreparedModule prepareSource(Path path, SourceSnapshot source,
                                              NekoTrustApprovedSource approval) throws Exception {
        return pipeline.prepareCaptured(path, source.source(), approval, source.binding());
    }

    private void publishSourceMap(Path path, NekoPreparedModule prepared) {
        if (prepared.sourceMap() == null || prepared.sourceMap().isBlank()) {
            return;
        }
        relativePath(path).ifPresent(relativePath -> sourceMaps.register(relativePath,
                prepared.sourceMap(), prepared.prependedLineCount()));
    }

    private static Path key(Path path) {
        return Path.of(NekoCanonicalPath.of(path));
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("NekoModulePipelineCache session is closed");
        }
    }

    private NekoModulePipelineCache rootOwner() {
        return owner == null ? this : owner;
    }

    private Optional<String> relativePath(Path path) {
        try {
            return Optional.of(sourceMaps.root().relativize(path).toString().replace('\\', '/'));
        } catch (Exception ignored) { // relative path computation fails → cache miss
            return Optional.empty();
        }
    }

    private static boolean isJson(Path path) {
        Path fileName = path == null ? null : path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".json");
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
            Path root = sourceMaps.root().normalize().toAbsolutePath();
            if (!key.startsWith(root)) {
                return null;
            }
            Path relative = root.relativize(key);
            if (relative.getNameCount() < 1) {
                return null;
            }
            String first = relative.getName(0).toString();
            for (ScriptType type : ScriptType.all()) {
                String dirName = type.name + "_scripts";
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

    private record SourceSnapshot(FileStamp stamp, String source,
                                  NekoModulePipeline.LanguageBinding binding) {}

    private record PreparedEntry(FileStamp stamp, NekoPreparedModule prepared, ScriptType type) {}

    /**
     * 模块文件的内容指纹（mtime/size/contentHash + ModuleIdentity(languageId/requestedMode)）。
     *
     * <p>历史缺陷：仅 (modifiedMillis, size) 无法区分“同一时间戳刻度内对等长文件的覆盖写入”，
     * 粗粒度时间戳文件系统（如部分 Windows / FAT / 容器挂载）会因此误判未变化，继续返回旧编译模块。
     * contentHash（SHA-256）修复该缺陷；languageId/requestedMode 由 {@link NekoModuleIdentity}
     * 组成：同一路径在语言插件替换（同扩展名改注册）或 requested mode 变化时也必须失效。
     */
    private record FileStamp(long modifiedMillis, long size, String contentHash,
                             NekoModuleIdentity identity) {
        private static FileStamp read(Path path, String source, NekoModuleIdentity identity) throws IOException {
            return new FileStamp(Files.getLastModifiedTime(path).toMillis(), Files.size(path),
                    contentHash(source), identity);
        }

        private static String contentHash(String source) {
            return NekoModuleHash.sha256(source);
        }
    }
}
