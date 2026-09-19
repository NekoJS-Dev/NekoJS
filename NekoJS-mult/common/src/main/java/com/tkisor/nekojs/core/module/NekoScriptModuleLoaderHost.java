package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.error.NekoSourceMapView;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinker;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleState;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.ScriptPathProvider;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CJS/ESM 模块装载宿主（Module Resolution/Cache 的执行委托面）。
 *
 * <p>W3 显式注入：prepared 缓存经构造器传入（生产与 {@link NekoRuntimeRoot} 持有的
 * {@link NekoModulePipelineCache} 为同一实例；旧构造器自建隔离实例，仅供测试/工具）。
 * 路径解析失败包成 {@link NekoModuleError}（RESOLVE/Module Resolution-Cache），
 * ESM link 失败统一包成 LINK（原始诊断保留为 cause），宿主与 guest 装载失败包成
 * EXECUTE（原始异常保留为 cause）。平台 callback 不进入本层（见
 * {@code ModulePipelineIsolationTest}）；Graal Context 由执行环境构造并传入，
 * 本层不创建 Context、不决定 HostAccess。
 */
public final class NekoScriptModuleLoaderHost {
    private static final Pattern STACK_LOCATION = Pattern.compile(
            "(?m)^\\s*at\\s+(.+?):(\\d+):(\\d+)\\s*(?:\\)|$)");

    private final Context context;
    private final NekoModuleResolver resolver;
    private final NekoModulePipelineCache preparationCache;
    private final NekoEsmVirtualModuleRegistry virtualModules;
    private final StackTraceMapper stackTraceMapper;
    private final NekoEsmLinker esmLinker;
    private final NekoEsmLinkCache esmLinkCache;
    private final NekoEsmModuleRecordCache esmRecordCache;
    private final NekoNativeEsmSourceRewriter esmRewriter;
    private final NekoModuleDependencyGraph dependencyGraph;
    private final Map<String, ModuleState> moduleCache;
    private final Map<String, Long> moduleRevisions;
    private final Map<String, String> modulePreparedKeys;
    private final Map<String, java.nio.file.Path> modulePaths;
    private final Set<String> registeredSpecialModules = ConcurrentHashMap.newKeySet();
    private final BiConsumer<java.nio.file.Path, String> preparationObserver;
    private final ModuleReloadCoordinator reloadCoordinator;
    private final EsmModuleLifecycle esmLifecycle;
    /** Graal proxy calls cannot reliably preserve checked Java causes; retain staged nested failures per host thread. */
    private final ThreadLocal<NekoModuleError> boundaryFailure = new ThreadLocal<>();
    private Value executor;
    private Value specialResolver;
    private Value moduleFactory;
    private Value jsonParser;
    /** Synthetic header lines the CommonJS guest wrapper adds; {@code 0} until the loader measures it. */
    private int cjsBodyLineOffset;

    /**
     * 生产装配入口：与 runtime owner 共享 prepared 缓存实例。
     *
     * @param preparationCache {@code NekoRuntimeRoot} 持有的缓存（模块 session 生命周期归属）
     */
    public NekoScriptModuleLoaderHost(Context context, NekoModuleResolver resolver,
                                       NekoModulePipelineCache preparationCache) {
        this.context = context;
        this.resolver = resolver;
        this.preparationCache = preparationCache;
        this.virtualModules = preparationCache.virtualModules();
        this.stackTraceMapper = new StackTraceMapper(preparationCache.sourceMaps(), virtualModules);
        this.esmLinker = new NekoEsmLinker(resolver, preparationCache);
        this.esmLinkCache = new NekoEsmLinkCache(esmLinker);
        this.esmRecordCache = new NekoEsmModuleRecordCache();
        this.esmRewriter = new NekoNativeEsmSourceRewriter(resolver, preparationCache, virtualModules,
                preparationCache.sourceMaps());
        this.dependencyGraph = new NekoModuleDependencyGraph();
        this.moduleCache = new ConcurrentHashMap<>();
        this.moduleRevisions = new ConcurrentHashMap<>();
        this.modulePreparedKeys = new ConcurrentHashMap<>();
        this.modulePaths = new ConcurrentHashMap<>();
        this.preparationObserver = this::observePreparedCacheEntry;
        this.reloadCoordinator = new ModuleReloadCoordinator(moduleCache, esmRecordCache, esmLinkCache, moduleRevisions, dependencyGraph, preparationCache);
        this.esmLifecycle = new EsmModuleLifecycle(esmRecordCache, esmLinkCache, dependencyGraph, esmRewriter,
                virtualModules, context, reloadCoordinator::revision, this::prepare);
        // Register only after every constructor-owned collaborator is initialized. If one of
        // those constructors fails, no partial host has published an observer to the session.
        preparationCache.registerPreparationObserver(preparationObserver);
    }

    /** Release this host's cache observer without owning or clearing the shared cache. */
    public void close() {
        preparationCache.unregisterPreparationObserver(preparationObserver);
    }

    // ======== Graal interop shell：@CalledByDynamicCode 方法供 internal/script-loader.js 通过 GraalVM interop 调用 ========
    // 这些是 interop shell 入口，内部委托给协作者（EsmModuleLifecycle / CjsModuleLoader）。

    @CalledByDynamicCode
    public void configure(Value executor, Value moduleFactory, Value specialResolver, Value jsonParser) {
        configure(executor, moduleFactory, specialResolver, jsonParser, 0);
    }

    /**
     * Install the loader bridge. {@code cjsBodyLineOffset} is the number of synthetic lines the guest
     * compiler prepends to a CommonJS module body; the loader measures it so the host can translate a
     * reported guest line back onto the prepared module before resolving the authored position.
     */
    @CalledByDynamicCode
    public void configure(Value executor, Value moduleFactory, Value specialResolver, Value jsonParser,
                          int cjsBodyLineOffset) {
        this.executor = executor;
        this.specialResolver = specialResolver;
        this.moduleFactory = moduleFactory;
        this.jsonParser = jsonParser;
        this.cjsBodyLineOffset = Math.max(0, cjsBodyLineOffset);
    }

    /** Install the Java-side allow-list for plugin-defined special module ids. */
    public void registerSpecialModules(java.util.Collection<String> moduleIds) {
        if (moduleIds != null) {
            registeredSpecialModules.addAll(moduleIds);
        }
    }

    // ---- ESM namespace 回调: 由 captureNamespaceSync/Async 中动态拼接的 JS 字符串调用 ----
    // 调用代码如: "globalThis.__nekoScriptModuleLoaderHost.completeEsmNamespace(...)"
    // 见 captureNamespaceSync/Async 方法中的 context.eval() 字符串拼接。

    @CalledByDynamicCode
    public void captureEsmNamespace(String moduleId, Value namespace) {
        captureEsmNamespace(moduleId, revision(moduleId), namespace);
    }

    @CalledByDynamicCode
    public void captureEsmNamespace(String moduleId, long revision, Value namespace) {
        esmRecordCache.captureNamespace(moduleId, revision, namespace);
    }

    @CalledByDynamicCode
    public void completeEsmNamespace(String moduleId, Value namespace) {
        completeEsmNamespace(moduleId, revision(moduleId), namespace);
    }

    @CalledByDynamicCode
    public void completeEsmNamespace(String moduleId, long revision, Value namespace) {
        esmRecordCache.completeNamespace(moduleId, revision, namespace);
    }

    @CalledByDynamicCode
    public void failEsmNamespace(String moduleId, Object failure) {
        failEsmNamespace(moduleId, revision(moduleId), failure);
    }

    @CalledByDynamicCode
    public void failEsmNamespace(String moduleId, long revision, Object failure) {
        NekoModuleError staged = boundaryFailure.get();
        boundaryFailure.remove();
        esmRecordCache.failNamespace(moduleId, revision, staged == null ? toThrowable(failure) : staged);
    }

    // ---- 入口加载: JS bridge 调用，也由 ScriptManager 通过 Java 间接调用 ----

    @CalledByDynamicCode
    public Object loadEntry(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(entryPath);
        dependencyGraph.markEntry(resolved.id());
        refreshPreparedExecutionTree(resolved.id());
        return loadResolved(resolved);
    }

    public CompletableFuture<?> loadEntryAsync(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(entryPath);
        dependencyGraph.markEntry(resolved.id());
        refreshPreparedExecutionTree(resolved.id());
        return loadResolvedAsync(resolved);
    }

    public NekoSourceMapView sourceMaps() {
        return preparationCache.sourceMapView();
    }

    public NekoVirtualModuleView virtualModules() {
        return preparationCache.virtualModuleView();
    }

    public Object requireFrom(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolveChildForRequire(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolved(resolved);
    }

    public String resolveToString(String parentPath, String specifier) throws IOException {
        // require.resolve 语义：bare 未命中直接报错（见 NekoModuleResolver.resolveForRequire）
        NekoResolvedModule resolved = resolveChildForRequire(parentPath, specifier);
        return resolved.special() ? resolved.specifier() : resolved.id();
    }

    // ---- import.meta 解析: NekoNativeEsmSourceRewriter 生成替换代码时引用 ----

    @CalledByDynamicCode
    public String resolveImportMeta(String parentPath, String specifier) throws IOException {
        return resolveNativeImport(parentPath, specifier);
    }

    // ---- 缓存与依赖管理: JS bridge + Java 调用 ----

    @CalledByDynamicCode
    public void clearCache() {
        reloadCoordinator.clearAll();
        clearSharedCachesForOwnType();
    }

    @CalledByDynamicCode
    public void clearRuntimeCache() {
        reloadCoordinator.clearRuntimeCache();
        clearSharedCachesForOwnType();
    }

    /** Clear this runtime owner's prepared/source-map/virtual-module entries for this context type. */
    private void clearSharedCachesForOwnType() {
        com.tkisor.nekojs.api.ScriptType type =
                com.tkisor.nekojs.script.ScriptContextRegistry.scriptTypeOf(context);
        preparationCache.clear(type);
    }

    /**
     * 路径解析统一收口：resolver 的原始错误包成 RESOLVE 阶段错误（owner Module
     * Resolution/Cache），消息文本保持不变；已是阶段错误的透传。
     */
    private NekoResolvedModule resolveEntryPath(String entryPath) throws IOException {
        return resolveWithStage(null, entryPath, () -> resolver.resolveEntry(entryPath));
    }

    private NekoResolvedModule resolveChild(String parentPath, String specifier) throws IOException {
        return resolveWithStage(parentPath, specifier, () -> resolver.resolve(parentPath, specifier));
    }

    private NekoResolvedModule resolveChildForRequire(String parentPath, String specifier) throws IOException {
        return resolveWithStage(parentPath, specifier,
                () -> resolver.resolveForRequire(parentPath, specifier, registeredSpecialModules));
    }

    @FunctionalInterface
    private interface ResolutionCall {
        NekoResolvedModule resolve() throws IOException;
    }

    private NekoResolvedModule resolveWithStage(String parentPath, String specifier,
                                                 ResolutionCall resolution) throws IOException {
        try {
            return resolution.resolve();
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (IOException failure) {
            throw NekoModuleError.resolve(parentPath, specifier, failure);
        }
    }

    @CalledByDynamicCode
    public java.util.List<String> affectedEntries(String modulePath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(modulePath);
        return dependencyGraph.affectedEntries(resolved.id());
    }

    @CalledByDynamicCode
    public void invalidateAffectedModules(String modulePath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(modulePath);
        invalidateModules(dependencyGraph.affectedModules(resolved.id()), false);
    }

    @CalledByDynamicCode
    public void invalidateModuleTree(String modulePath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(modulePath);
        invalidateModules(dependencyGraph.dependencyModules(resolved.id()), true);
    }

    // ---- Native ESM import/export: JS bridge __nekoNativeImport 和 NekoNativeEsmSourceRewriter 生成代码调用 ----

    @CalledByDynamicCode
    public Object nativeImport(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolveChild(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolved(resolved);
    }

    @CalledByDynamicCode
    public CompletableFuture<?> nativeImportAsync(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolveChild(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolvedAsync(resolved);
    }

    @CalledByDynamicCode
    public String resolveNativeImport(String parentPath, String specifier) throws IOException {
        try {
            if (isResolvedNativeModuleUri(specifier)) {
                return specifier;
            }
            NekoResolvedModule resolved = resolveChild(parentPath, specifier);
            recordDependency(parentPath, resolved);
            if (resolved.special()) {
                if ("nekojs/jsx-runtime".equals(resolved.specifier())) {
                    // automatic JSX runtime 的命名导入（jsx/jsxs/Fragment）需要静态导出名
                    return esmRewriter.syntheticNamedModuleUri(resolved.specifier(), "jsx", "jsxs", "Fragment").toString();
                }
                return esmRewriter.syntheticObjectModuleUri(resolved.specifier()).toString();
            }
            if (resolved.json()) {
                String json = preparationCache.prepareJson(resolved.path());
                observeExecutionKey(resolved.id(), resolved.path(), preparationCache.jsonExecutionKey(resolved.path(), json));
                return esmRewriter.syntheticJsonModuleUri(resolved.path()).toString();
            }
            NekoPreparedModule prepared = prepare(resolved);
            if (prepared.mode() == NekoModuleMode.ESM) {
                // Validate the dynamic child's host-side link now so LINK diagnostics retain
                // their NekoModuleError stage before native import() evaluates the URI.
                esmLifecycle.linkedEsmRecord(resolved, prepared);
                return esmRewriter.registerModule(resolved.path(), resolved.id(), prepared).toString();
            }
            return esmRewriter.syntheticCjsModuleUri(resolved.id(), parentPath, specifier).toString();
        } catch (NekoModuleError staged) {
            boundaryFailure.set(staged);
            throw staged;
        } catch (IOException failure) {
            NekoModuleError staged = NekoModuleError.resolve(parentPath, specifier, failure);
            boundaryFailure.set(staged);
            throw staged;
        }
    }

    // ======== 以下为私有实现 ========

    private void invalidateModules(List<String> moduleIds, boolean removeGraphNodes) {
        reloadCoordinator.invalidateModules(moduleIds, removeGraphNodes);
        for (String moduleId : moduleIds) {
            modulePreparedKeys.remove(moduleId);
            java.nio.file.Path path = modulePaths.get(moduleId);
            if (path != null) {
                preparationCache.invalidate(path);
            }
        }
    }

    private long revision(String moduleId) {
        return reloadCoordinator.revision(moduleId);
    }

    private Object loadResolved(NekoResolvedModule resolved) throws IOException {
        return loadResolvedSync(resolved, true);
    }

    private CompletableFuture<?> loadResolvedAsync(NekoResolvedModule resolved) throws IOException {
        if (resolved.special()) {
            return CompletableFuture.completedFuture(resolveSpecial(resolved.specifier()));
        }
        if (resolved.json()) {
            return CompletableFuture.completedFuture(loadJsonResolved(resolved));
        }

        NekoPreparedModule prepared = prepare(resolved);
        if (prepared.mode() == NekoModuleMode.ESM) {
            return loadEsmModuleAsync(resolved, prepared);
        }
        return CompletableFuture.completedFuture(loadScriptResolved(resolved, prepared));
    }

    private Object loadResolvedSync(NekoResolvedModule resolved, boolean rejectAsyncEsm) throws IOException {
        if (resolved.special()) {
            return resolveSpecial(resolved.specifier());
        }
        if (resolved.json()) {
            return loadJsonResolved(resolved);
        }

        NekoPreparedModule prepared = prepare(resolved);
        if (prepared.mode() == NekoModuleMode.ESM) {
            if (rejectAsyncEsm && prepared.esmAst() != null && prepared.esmAst().topLevelAwait()) {
                throw asyncEsmRequireError(resolved);
            }
            return loadEsmModule(resolved, prepared);
        }
        return loadScriptResolved(resolved, prepared);
    }

    private Object loadJsonResolved(NekoResolvedModule resolved) throws IOException {
        String rawJson = preparationCache.prepareJson(resolved.path());
        String preparedKey = preparationCache.jsonExecutionKey(resolved.path(), rawJson);
        observeExecutionKey(resolved.id(), resolved.path(), preparedKey);
        ModuleState cached = moduleCache.get(resolved.id());
        if (cached != null && preparedKey.equals(cached.preparedKey())) {
            return cached.exports();
        }
        if (cached != null) moduleCache.remove(resolved.id());
        ModuleState module = newModuleState(resolved.id(), preparedKey);
        try {
            module.exports(parseJson(resolved.id(), rawJson));
            module.loaded(true);
            moduleCache.put(resolved.id(), new ModuleState(module.filename(), preparedKey, module.value()));
            return module.exports();
        } catch (IOException e) {
            moduleCache.remove(resolved.id());
            if (e instanceof NekoModuleError) {
                throw e;
            }
            throw NekoModuleError.cache(NekoModuleError.displayPath(resolved.path()),
                    "Failed to read JSON module: " + resolved.id(), e);
        } catch (RuntimeException failure) {
            moduleCache.remove(resolved.id());
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        } catch (Error e) {
            moduleCache.remove(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            moduleCache.remove(resolved.id());
            throw NekoModuleError.execute(resolved.id(), "Failed to load JSON module: " + resolved.id(), throwable);
        }
    }

    private Object loadScriptResolved(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        ModuleState cached = moduleCache.get(resolved.id());
        if (cached != null && prepared.cacheKey().equals(cached.preparedKey())) {
            return cached.exports();
        }
        if (cached != null) moduleCache.remove(resolved.id());
        ModuleState module = newModuleState(resolved.id(), prepared.cacheKey());
        // Node 循环 require 语义：执行前先入缓存，循环方拿到的是执行中模块的「部分 exports」；
        // 若执行后才入缓存，A↔B 互引会无限重入直至 StackOverflowError。失败时移除以免半初始化模块驻留。
        moduleCache.put(resolved.id(), module);
        try {
            executeScriptModule(resolved, module, prepared);
            module.loaded(true);
            return module.exports();
        } catch (IOException e) {
            moduleCache.remove(resolved.id());
            throw e;
        } catch (RuntimeException failure) {
            moduleCache.remove(resolved.id());
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        } catch (Error e) {
            moduleCache.remove(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            moduleCache.remove(resolved.id());
            throw NekoModuleError.execute(resolved.id(), "Failed to load module: " + resolved.id(), throwable);
        }
    }

    private ModuleState newModuleState(String filename) throws IOException {
        return newModuleState(filename, "");
    }

    private ModuleState newModuleState(String filename, String preparedKey) throws IOException {
        if (moduleFactory == null || !moduleFactory.canExecute()) {
            throw NekoModuleError.execute(filename, "NekoJS script module factory is unavailable.", null);
        }
        try {
            return new ModuleState(filename, preparedKey, moduleFactory.execute(filename));
        } catch (RuntimeException failure) {
            throw NekoModuleError.execute(filename, failure.getMessage(), failure);
        }
    }

    private Object parseJson(String moduleId, String rawJson) throws IOException {
        if (jsonParser == null || !jsonParser.canExecute()) {
            throw NekoModuleError.execute(moduleId, "JSON parser is unavailable for NekoJS script loader.", null);
        }
        return jsonParser.execute(rawJson);
    }

    private void executeScriptModule(NekoResolvedModule resolved, ModuleState module, NekoPreparedModule prepared) throws IOException {
        if (executor == null || !executor.canExecute()) {
            throw NekoModuleError.execute(resolved.id(), "NekoJS script module executor is unavailable.", null);
        }
        ProxyExecutable require = args -> {
            String specifier = args.length == 0 ? "" : args[0].asString();
            return requireUnchecked(resolved.id(), specifier);
        };
        ProxyExecutable resolve = args -> {
            String specifier = args.length == 0 ? "" : args[0].asString();
            return resolveToStringUnchecked(resolved.id(), specifier);
        };
        boundaryFailure.remove();
        try {
            executor.execute(module.value(), require, resolve, resolved.id(), resolved.dirname(), prepared.code());
        } catch (RuntimeException failure) {
            NekoModuleError nested = boundaryFailure.get();
            boundaryFailure.remove();
            if (nested == null) {
                nested = findStagedError(failure);
            }
            if (nested != null) {
                throw nested;
            }
            IOException enriched = withSyntaxLocation(resolved, prepared, failure);
            if (enriched instanceof NekoEsmLinkException syntaxDiagnostic) {
                // The diagnostic position is generated-code; publish the authored position the
                // prepared source map resolves it to so lowered TS/JSX/TSX never reports the
                // generated line as if it were authored.
                int[] authored = authoredPosition(prepared, syntaxDiagnostic.diagnostic().line(),
                        syntaxDiagnostic.diagnostic().column());
                throw NekoModuleError.prepare(prepared.sourcePath(), prepared.languageId(), prepared.mode(),
                        authored[0], authored[1], syntaxDiagnostic.getMessage(), syntaxDiagnostic);
            }
            throw executionError(resolved, prepared, failure, cjsBodyLineOffset);
        }
    }

    /**
     * Translate a generated line/column into the authored line/column of the prepared module.
     * Returns {@code {-1, -1}} when the map has no entry, so callers keep an honest "unknown".
     */
    private int[] authoredPosition(NekoPreparedModule prepared, int generatedLine, int generatedColumn) {
        if (prepared == null || prepared.sourcePath() == null || generatedLine <= 0) {
            return new int[]{-1, -1};
        }
        SourceMapRegistry.OriginalPosition mapped = preparationCache.sourceMaps()
                .getMappedPosition(prepared.sourcePath(), generatedLine, generatedColumn);
        if (mapped.path == null || mapped.line <= 0) {
            return new int[]{-1, -1};
        }
        return new int[]{mapped.line, Math.max(1, mapped.column)};
    }

    /** Convert the guest boundary failure into one diagnostic carrying authored location and identity. */
    private NekoModuleError executionError(NekoResolvedModule resolved, NekoPreparedModule prepared,
                                            RuntimeException failure) {
        return executionError(resolved, prepared, failure, 0);
    }

    /**
     * @param generatedLineOffset synthetic lines the execution wrapper added ahead of the prepared
     *                            module body (CommonJS {@code new Function} wrapper); {@code 0} when
     *                            the reported location already indexes the prepared module directly.
     */
    private NekoModuleError executionError(NekoResolvedModule resolved, NekoPreparedModule prepared,
                                            RuntimeException failure, int generatedLineOffset) {
        NekoModuleError staged = findStagedError(failure);
        if (staged != null) {
            // A nested module failure already carries its own stage, module identity and authored
            // location. Re-wrapping it as EXECUTE would hide the failing child behind the synthetic
            // interop module that called into it.
            return staged;
        }
        String sourcePath = prepared.sourcePath();
        String moduleId = resolved.id();
        int line = -1;
        int column = -1;
        PolyglotException guest = findPolyglotException(failure);
        SourceSection location = guest == null ? null
                : sourceLocation(guest, resolved.id(), prepared, resolved, generatedLineOffset);
        if (location != null) {
            line = Math.max(-1, location.getStartLine() - generatedLineOffset);
            column = location.getStartColumn();
            SourceMapRegistry.OriginalPosition mapped = mappedPosition(
                    location.getSource().getPath(), prepared, resolved, line, column);
            if (mapped != null) {
                sourcePath = authoredPath(mapped.path);
                moduleId = sourcePath;
                line = mapped.line;
                column = mapped.column;
            } else {
                String displayPath = displayNameOf(location.getSource().getPath());
                if (displayPath != null && !displayPath.isBlank() && !displayPath.equals(resolved.id())) {
                    sourcePath = authoredPath(displayPath);
                    moduleId = sourcePath;
                } else if (sourcePath == null || sourcePath.isBlank()) {
                    sourcePath = displayPath;
                }
            }
        }
        if (line < 0) {
            StackLocation stackLocation = stackLocation(failure.getMessage());
            if (stackLocation != null) {
                line = stackLocation.line() - generatedLineOffset;
                column = stackLocation.column();
                if (line <= 0) {
                    line = -1;
                }
                SourceMapRegistry.OriginalPosition mapped = line < 0 ? null : mappedPosition(
                        stackLocation.path(), prepared, resolved, line, column);
                if (mapped != null) {
                    sourcePath = authoredPath(mapped.path);
                    moduleId = sourcePath;
                    line = mapped.line;
                    column = mapped.column;
                } else if (generatedLineOffset > 0) {
                    // The generated code was a wrapped module body: the adjusted line indexes the
                    // prepared module, not authored source. Without a map entry the authored
                    // position is genuinely unknown, and reporting the generated line as authored
                    // would be a lie.
                    line = -1;
                    column = -1;
                } else {
                    String displayPath = displayNameOf(stackLocation.path());
                    if (displayPath != null && !displayPath.isBlank()) {
                        sourcePath = authoredPath(displayPath);
                        moduleId = sourcePath;
                    }
                }
            }
        }
        sourcePath = authoredModuleId(sourcePath);
        if (moduleId != null) {
            moduleId = authoredModuleId(moduleId);
        }
        return NekoModuleError.execute(moduleId, sourcePath, line, column,
                failure.getMessage(), failure);
    }

    /**
     * Resolve a generated position onto authored coordinates. The generated path may be a virtual
     * module path, a {@code <function>} placeholder for a wrapped CommonJS body, or the authored path
     * itself; the prepared module is the authoritative fallback because the map was published for it.
     */
    private SourceMapRegistry.OriginalPosition mappedPosition(String generatedPath, NekoPreparedModule prepared,
                                                              NekoResolvedModule resolved, int line, int column) {
        if (line <= 0) {
            return null;
        }
        SourceMapRegistry maps = preparationCache.sourceMaps();
        // The generated path owns the authoritative map for that generated code. A rewritten ESM
        // module has a different map from its authored-path entry, so the virtual path must be tried
        // before any authored-path fallback. The authored path is only a fallback for generated
        // positions that carry no usable virtual identity (e.g. a wrapped CommonJS body).
        List<String> candidates = new ArrayList<>();
        if (isScriptLocation(generatedPath)) {
            candidates.add(generatedPath);
            String displayPath = virtualModules.displayPath(generatedPath);
            if (displayPath != null && !displayPath.isBlank()) {
                candidates.add(displayPath);
            }
        }
        if (candidates.isEmpty()) {
            // The guest reported a placeholder path (<function>, <builtin>, Unnamed): the position
            // indexes the prepared module body, so its own published map is the authority.
            if (prepared != null && prepared.sourcePath() != null) {
                candidates.add(prepared.sourcePath());
            }
            if (resolved != null && resolved.id() != null) {
                candidates.add(resolved.id());
            }
        }
        for (String candidate : candidates) {
            SourceMapRegistry.OriginalPosition mapped = maps.getMappedPosition(candidate, line, column);
            if (mapped.path != null && !mapped.path.isBlank()) {
                return mapped;
            }
        }
        return null;
    }

    /**
     * Whether a reported path can own a source map: a real file path carries an extension, while
     * Graal placeholders ({@code <function>}, {@code <builtin>}, {@code Unnamed}) do not.
     */
    private static boolean isScriptLocation(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String fileName = slash < 0 ? normalized : normalized.substring(slash + 1);
        return fileName.indexOf('.') > 0;
    }

    private String displayNameOf(String generatedPath) {
        if (generatedPath == null || generatedPath.isBlank()) {
            return generatedPath;
        }
        String displayPath = virtualModules.displayPath(generatedPath);
        return displayPath == null || displayPath.isBlank() ? generatedPath : displayPath;
    }

    private StackLocation stackLocation(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        Matcher matcher = STACK_LOCATION.matcher(message);
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.indexOf(' ') >= 0 || path.indexOf('(') >= 0 || path.endsWith(".java") || path.endsWith(".class")) {
                // Java-style frames (including host frames embedded in a guest message) carry a
                // declaring method, never a script location.
                continue;
            }
            if (path.startsWith("file:")) {
                try {
                    path = Path.of(new URI(path)).toString();
                } catch (URISyntaxException | IllegalArgumentException ignored) {
                    // Keep the original stack spelling for the virtual registry filename index.
                }
            }
            return new StackLocation(path, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        }
        return null;
    }

    private record StackLocation(String path, int line, int column) {}

    /**
     * Authored module identity: strips the synthetic virtual-module suffixes the ESM rewriter adds
     * ({@code #cjs-interop…}, {@code #dynamic}) so a failure inside an imported CJS/JSON child is
     * attributed to the real authored module instead of the generated interop id.
     */
    private static String authoredModuleId(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        int hash = text.indexOf('#');
        return authoredPath(hash < 0 ? text : text.substring(0, hash));
    }

    private static String authoredPath(String text) {
        if (text == null || text.isBlank()) return text;
        String normalized = text.replace('\\', '/');
        try {
            String authored = ScriptPathProvider.authoredPathText(normalized, Path.of("").getFileSystem());
            return authored == null ? normalized : authored;
        } catch (RuntimeException ignored) {
            return normalized;
        }
    }

    /**
     * Choose the guest frame that owns the failure.
     *
     * <p>Precedence: a frame that resolves onto a real authored position <em>and</em> belongs to a
     * different module (cross-import/dynamic failure), then any frame that resolves onto a real
     * authored position, then — when nothing maps — the reported source location unchanged, so an
     * unmapped failure keeps its honest "unknown" position instead of inventing one.
     *
     * <p>The middle tier is what makes transpiled languages accurate. A thrown instance of a
     * generated language runtime class (the Python exception prelude declares {@code class ValueError
     * extends Error}) puts the class-declaration line at the top of the stack, so the first guest frame
     * points at generated prelude code. Those frames carry no mapping, while the deeper frame that
     * actually raised does — picking the first <em>mapped</em> frame reports the authored line
     * instead of a generated helper line.
     */
    private SourceSection sourceLocation(PolyglotException failure, String moduleId,
                                          NekoPreparedModule prepared, NekoResolvedModule resolved,
                                          int generatedLineOffset) {
        SourceSection fallback = failure.getSourceLocation();
        SourceSection firstMapped = null;
        for (PolyglotException.StackFrame frame : failure.getPolyglotStackTrace()) {
            if (!frame.isGuestFrame() || frame.getSourceLocation() == null) {
                continue;
            }
            SourceSection candidate = frame.getSourceLocation();
            if (fallback == null) {
                fallback = candidate;
            }
            if (!mapsToAuthoredSource(candidate, prepared, resolved, generatedLineOffset)) {
                continue;
            }
            String generatedPath = candidate.getSource().getPath();
            String displayPath = virtualModules.displayPath(generatedPath);
            if (displayPath != null && !displayPath.equals(moduleId)) {
                return candidate;
            }
            if (firstMapped == null) {
                firstMapped = candidate;
            }
        }
        return firstMapped == null ? fallback : firstMapped;
    }

    /** Whether this guest frame resolves onto an authored position for the prepared module. */
    private boolean mapsToAuthoredSource(SourceSection candidate, NekoPreparedModule prepared,
                                          NekoResolvedModule resolved, int generatedLineOffset) {
        int line = candidate.getStartLine() - generatedLineOffset;
        if (line <= 0) {
            return false;
        }
        SourceMapRegistry.OriginalPosition mapped = mappedPosition(
                candidate.getSource().getPath(), prepared, resolved, line, candidate.getStartColumn());
        return mapped != null && mapped.path != null && !mapped.path.isBlank();
    }

    private static PolyglotException findPolyglotException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof PolyglotException polyglot) {
                return polyglot;
            }
        }
        return null;
    }

    private static NekoModuleError findStagedError(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof NekoModuleError staged) {
                return staged;
            }
            if (current instanceof PolyglotException polyglot && polyglot.isHostException()
                    && polyglot.asHostException() instanceof NekoModuleError stagedHost) {
                // A nested host-thread failure that crossed the guest boundary keeps its stage and
                // authored location; re-classifying it would lose the failing child's identity.
                return stagedHost;
            }
        }
        return null;
    }

    /**
     * CJS 模块由 script-loader.js 的 {@code new Function} 编译：语法错误的 SourceSection
     * 指向编译调用点（internal/script-loader.js 内部帧），用户文件内的真实行列丢失。
     * 用 {@link Context#parse} 以用户文件名重解析同一份代码取回位置，包成
     * {@link NekoEsmLinkException} 诊断（ScriptError 对该异常渲染 位置 path:line:col 与代码片段）。
     * 返回 null 表示保持原异常：链上已有 ESM 诊断（嵌套 require 已在内层定位）、
     * 无语法错误、或重解析取不到位置。位置基于 prepared.code()：纯 JS 与磁盘文件一致；
     * 经变换的语言（JSX 等）行列对应编译产物。
     */
    private IOException withSyntaxLocation(NekoResolvedModule resolved, NekoPreparedModule prepared, RuntimeException failure) {
        if (resolved.special() || hasEsmDiagnostic(failure)) {
            return null;
        }
        PolyglotException syntaxError = findSyntaxError(failure);
        if (syntaxError == null) {
            return null;
        }
        Source source;
        try {
            source = Source.newBuilder("js", prepared.code(), resolved.id())
                    .uri(resolved.path().toAbsolutePath().normalize().toUri())
                    .build();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        try {
            context.parse(source);
        } catch (PolyglotException parseError) {
            SourceSection location = parseError.getSourceLocation();
            if (location == null) {
                return null;
            }
            NekoEsmLinkException enriched = new NekoEsmLinkException(new NekoEsmDiagnostic(
                    resolved.path(),
                    new NekoEsmSpan(location.getCharIndex(), location.getCharIndex() + location.getCharLength()),
                    location.getStartLine(), location.getStartColumn(),
                    syntaxError.getMessage()));
            enriched.initCause(failure);
            return enriched;
        } catch (RuntimeException ignored) {
            return null;
        }
        // 重解析通过：new Function 体与顶层 script 解析语义差异（如顶层 return），保留原异常
        return null;
    }

    private static boolean hasEsmDiagnostic(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof NekoEsmLinkException) {
                return true;
            }
        }
        return false;
    }

    private static PolyglotException findSyntaxError(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof PolyglotException polyglotException && polyglotException.isSyntaxError()) {
                return polyglotException;
            }
        }
        return null;
    }

    // ======== 以下 ESM/CJS 加载 + 栈映射委托给 EsmModuleLifecycle / 内部逻辑 ========

    private Object loadEsmModule(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        try {
            return esmLifecycle.loadEsmModule(resolved, prepared);
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (NekoEsmLinkException linkFailure) {
            throw NekoModuleError.link(linkFailure);
        } catch (IOException failure) {
            throw nestedOrExecute(resolved, prepared, failure);
        } catch (RuntimeException failure) {
            throw nestedOrExecute(resolved, prepared, failure);
        }
    }

    /**
     * Prefer the staged failure of the module that actually failed. A wrapper module (native ESM
     * entry, synthetic interop, dynamic-import bridge) must not claim a child's failure as its own.
     */
    private NekoModuleError nestedOrExecute(NekoResolvedModule resolved, NekoPreparedModule prepared,
                                             Throwable failure) {
        NekoModuleError nested = differentModuleFailure(failure, resolved.id());
        if (nested != null) {
            return nested;
        }
        if (failure instanceof RuntimeException runtime) {
            return executionError(resolved, prepared, runtime);
        }
        return NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
    }

    /** Staged failure of a different module, or {@code null} when there is none or it is unattributed. */
    private static NekoModuleError differentModuleFailure(Throwable failure, String moduleId) {
        NekoModuleError staged = findStagedError(failure);
        return staged != null && staged.moduleId() != null && !staged.moduleId().equals(moduleId)
                ? staged : null;
    }

    private CompletableFuture<Value> loadEsmModuleAsync(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        try {
            return esmLifecycle.loadEsmModuleAsync(resolved, prepared).handle((namespace, failure) -> {
                if (failure == null) {
                    return namespace;
                }
                Throwable cause = failure;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                // A nested failure belonging to another module (dynamic import / rewritten child)
                // keeps that child's stage, identity and location.
                NekoModuleError nested = differentModuleFailure(cause, resolved.id());
                if (nested != null) {
                    throw new CompletionException(nested);
                }
                if (cause instanceof NekoEsmLinkException linkFailure) {
                    throw new CompletionException(NekoModuleError.link(linkFailure));
                }
                RuntimeException runtimeFailure = cause instanceof RuntimeException runtime
                        ? runtime : new RuntimeException(cause);
                throw new CompletionException(executionError(resolved, prepared, runtimeFailure));
            });
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (NekoEsmLinkException linkFailure) {
            throw NekoModuleError.link(linkFailure);
        } catch (IOException failure) {
            throw nestedOrExecute(resolved, prepared, failure);
        } catch (RuntimeException failure) {
            throw nestedOrExecute(resolved, prepared, failure);
        }
    }

    private NekoPreparedModule prepare(NekoResolvedModule resolved) throws IOException {
        NekoPreparedModule prepared = preparationCache.prepare(resolved.path());
        observePrepared(resolved.id(), resolved.path(), prepared);
        return prepared;
    }

    private void observePrepared(String moduleId, java.nio.file.Path path, NekoPreparedModule prepared) {
        observeExecutionKey(moduleId, path, prepared.cacheKey());
    }

    private void observePreparedCacheEntry(java.nio.file.Path path, String executionKey) {
        String moduleId = moduleIdForPath(path);
        java.nio.file.Path knownPath = modulePaths.putIfAbsent(moduleId, path);
        observeExecutionKey(moduleId, knownPath == null ? path : knownPath, executionKey);
    }

    private String moduleIdForPath(java.nio.file.Path path) {
        try {
            return preparationCache.sourceMaps().root().relativize(path)
                    .toString().replace('\\', '/');
        } catch (Exception ignored) {
            return path.toAbsolutePath().normalize().toString().replace('\\', '/');
        }
    }

    private void observeExecutionKey(String moduleId, java.nio.file.Path path, String executionKey) {
        modulePaths.put(moduleId, path);
        String previous = modulePreparedKeys.put(moduleId, executionKey);
        if (previous != null && !previous.equals(executionKey)) {
            invalidateModules(dependencyGraph.affectedModules(moduleId), false);
        }
    }

    /** Refresh known dependencies before an entry cache hit can hide a changed child module. */
    private void refreshPreparedExecutionTree(String entryId) throws IOException {
        for (String moduleId : dependencyGraph.dependencyModules(entryId)) {
            java.nio.file.Path path = modulePaths.get(moduleId);
            if (path != null) {
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
                if (fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                    observeExecutionKey(moduleId, path,
                            preparationCache.jsonExecutionKey(path, preparationCache.prepareJson(path)));
                } else {
                    NekoPreparedModule prepared = preparationCache.prepare(path);
                    observePrepared(moduleId, path, prepared);
                }
            }
        }
    }

    private IOException asyncEsmRequireError(NekoResolvedModule resolved) {
        return NekoModuleError.execute(resolved.id(),
                "Cannot require async ESM module with top-level await: " + resolved.id() + ". Use import() instead.", null);
    }

    private Throwable toThrowable(Object failure) {
        if (failure instanceof Throwable throwable) {
            return throwable;
        }
        if (failure instanceof Value value) {
            return new RuntimeException(errorText(value));
        }
        return new RuntimeException(stackTraceMapper.mapStackText(String.valueOf(failure)));
    }

    private String errorText(Value value) {
        String stack = stringMember(value, "stack");
        if (stack != null && !stack.isBlank()) {
            return stackTraceMapper.mapStackText(stack);
        }
        String message = stringMember(value, "message");
        if (message != null && !message.isBlank()) {
            String name = stringMember(value, "name");
            return name == null || name.isBlank() || message.startsWith(name + ":") ? message : name + ": " + message;
        }
        return value.toString();
    }

    private String stringMember(Value value, String member) {
        if (value == null || !value.hasMembers() || !value.hasMember(member)) {
            return null;
        }
        try {
            Value memberValue = value.getMember(member);
            if (memberValue == null || memberValue.isNull()) {
                return null;
            }
            return memberValue.isString() ? memberValue.asString() : memberValue.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void recordDependency(String parentPath, NekoResolvedModule child) {
        if (child == null || child.special()) return;
        dependencyGraph.recordDependency(parentPath, child.id());
    }

    private Object requireUnchecked(String parentPath, String specifier) {
        try {
            return requireFrom(parentPath, specifier);
        } catch (IOException e) {
            if (e instanceof NekoModuleError staged) {
                boundaryFailure.set(staged);
            }
            throw new RuntimeException(e);
        }
    }

    private Object resolveToStringUnchecked(String parentPath, String specifier) {
        try {
            return resolveToString(parentPath, specifier);
        } catch (IOException e) {
            if (e instanceof NekoModuleError staged) {
                boundaryFailure.set(staged);
            }
            throw new RuntimeException(e);
        }
    }

    private boolean isResolvedNativeModuleUri(String specifier) {
        return specifier != null && specifier.startsWith("file:") && specifier.contains("/.native_esm_modules/");
    }

    private Object resolveSpecial(String specifier) throws IOException {
        if (specialResolver == null || !specialResolver.canExecute()) {
            throw new NekoModuleError(NekoModuleError.Stage.RESOLVE, NekoModuleError.OWNER_RESOLUTION_CACHE,
                    null, specifier, "NekoJS special module resolver is unavailable: " + specifier);
        }
        return specialResolver.execute(specifier).as(Object.class);
    }

}
