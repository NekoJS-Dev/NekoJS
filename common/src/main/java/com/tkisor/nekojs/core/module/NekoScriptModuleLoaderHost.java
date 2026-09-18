package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.annotation.CalledByDynamicCode;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.compiler.ScriptCompilerRegistry;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmDiagnostic;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkException;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinker;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleState;
import com.tkisor.nekojs.core.module.esm.NekoEsmSpan;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.esm.NekoNativeEsmSourceRewriter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.SourceSection;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

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
    private final ModuleReloadCoordinator reloadCoordinator;
    private final EsmModuleLifecycle esmLifecycle;
    private Value executor;
    private Value specialResolver;
    private Value moduleFactory;
    private Value jsonParser;

    public NekoScriptModuleLoaderHost(Context context) {
        this(context, new NekoModuleResolver(), NekoJSPaths.get(), defaultPreparationCache());
    }

    public NekoScriptModuleLoaderHost(Context context, NekoModuleResolver resolver, NekoJSPaths paths) {
        this(context, resolver, paths, defaultPreparationCache());
    }

    /**
     * 生产装配入口：与 runtime owner 共享 prepared 缓存实例。
     *
     * @param preparationCache {@code NekoRuntimeRoot} 持有的缓存（模块 session 生命周期归属）
     */
    public NekoScriptModuleLoaderHost(Context context, NekoModuleResolver resolver, NekoJSPaths paths,
                                       NekoModulePipelineCache preparationCache) {
        this.context = context;
        this.resolver = resolver;
        this.preparationCache = preparationCache;
        this.virtualModules = preparationCache.virtualModules();
        this.stackTraceMapper = new StackTraceMapper(preparationCache.sourceMaps(), virtualModules);
        this.esmLinker = new NekoEsmLinker(resolver, preparationCache);
        this.esmLinkCache = new NekoEsmLinkCache(esmLinker);
        this.esmRecordCache = new NekoEsmModuleRecordCache();
        this.esmRewriter = new NekoNativeEsmSourceRewriter(resolver, preparationCache, virtualModules);
        this.dependencyGraph = new NekoModuleDependencyGraph();
        this.moduleCache = new ConcurrentHashMap<>();
        this.moduleRevisions = new ConcurrentHashMap<>();
        this.reloadCoordinator = new ModuleReloadCoordinator(moduleCache, esmRecordCache, esmLinkCache, moduleRevisions, dependencyGraph, preparationCache);
        this.esmLifecycle = new EsmModuleLifecycle(esmRecordCache, esmLinkCache, dependencyGraph, esmRewriter,
                virtualModules, context, reloadCoordinator::revision, this::prepare);
    }

    private static NekoModulePipelineCache defaultPreparationCache() {
        return new NekoModulePipelineCache(
                ScriptCompilerRegistry.current(), SandboxConfig.defaultConfig());
    }

    // ======== Graal interop shell：@CalledByDynamicCode 方法供 internal/script-loader.js 通过 GraalVM interop 调用 ========
    // 这些是 interop shell 入口，内部委托给协作者（EsmModuleLifecycle / CjsModuleLoader）。

    @CalledByDynamicCode
    public void configure(Value executor, Value moduleFactory, Value specialResolver, Value jsonParser) {
        this.executor = executor;
        this.specialResolver = specialResolver;
        this.moduleFactory = moduleFactory;
        this.jsonParser = jsonParser;
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
        esmRecordCache.failNamespace(moduleId, revision, toThrowable(failure));
    }

    // ---- 入口加载: JS bridge 调用，也由 ScriptManager 通过 Java 间接调用 ----

    @CalledByDynamicCode
    public Object loadEntry(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(entryPath);
        dependencyGraph.markEntry(resolved.id());
        return loadResolved(resolved);
    }

    public CompletableFuture<?> loadEntryAsync(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolveEntryPath(entryPath);
        dependencyGraph.markEntry(resolved.id());
        return loadResolvedAsync(resolved);
    }

    public com.tkisor.nekojs.core.error.SourceMapRegistry sourceMaps() {
        return preparationCache.sourceMaps();
    }

    public NekoEsmVirtualModuleRegistry virtualModules() {
        return virtualModules;
    }

    public Object requireFrom(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolveChild(parentPath, specifier);
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
        try {
            return resolver.resolveEntry(entryPath);
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (IOException failure) {
            throw NekoModuleError.resolve(null, entryPath, failure);
        }
    }

    private NekoResolvedModule resolveChild(String parentPath, String specifier) throws IOException {
        try {
            return resolver.resolve(parentPath, specifier);
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (IOException failure) {
            throw NekoModuleError.resolve(parentPath, specifier, failure);
        }
    }

    private NekoResolvedModule resolveChildForRequire(String parentPath, String specifier) throws IOException {
        try {
            return resolver.resolveForRequire(parentPath, specifier);
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

    public String resolveNativeImport(String parentPath, String specifier) throws IOException {
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
            return esmRewriter.syntheticJsonModuleUri(resolved.path()).toString();
        }
        NekoPreparedModule prepared = prepare(resolved);
        if (prepared.mode() == NekoModuleMode.ESM) {
            return esmRewriter.registerModule(resolved.path(), resolved.id(), prepared).toString();
        }
        return esmRewriter.syntheticCjsModuleUri(resolved.id(), parentPath, specifier).toString();
    }

    // ======== 以下为私有实现 ========

    private void invalidateModules(List<String> moduleIds, boolean removeGraphNodes) {
        reloadCoordinator.invalidateModules(moduleIds, removeGraphNodes);
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
        ModuleState cached = moduleCache.get(resolved.id());
        if (cached != null) {
            return cached.exports();
        }
        ModuleState module = newModuleState(resolved.id());
        try {
            module.exports(parseJson(resolved.id(), Files.readString(resolved.path())));
            module.loaded(true);
            moduleCache.put(resolved.id(), module);
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
            throw NekoModuleError.execute(resolved.id(), "Failed to load JSON module: " + resolved.id(), throwable);
        }
    }

    private Object loadScriptResolved(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        ModuleState cached = moduleCache.get(resolved.id());
        if (cached != null) {
            return cached.exports();
        }
        ModuleState module = newModuleState(resolved.id());
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
        if (moduleFactory == null || !moduleFactory.canExecute()) {
            throw NekoModuleError.execute(filename, "NekoJS script module factory is unavailable.", null);
        }
        try {
            return new ModuleState(filename, moduleFactory.execute(filename));
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
        try {
            executor.execute(module.value(), require, resolve, resolved.id(), resolved.dirname(), prepared.code());
        } catch (RuntimeException failure) {
            IOException enriched = withSyntaxLocation(resolved, prepared, failure);
            if (enriched instanceof NekoEsmLinkException syntaxDiagnostic) {
                throw NekoModuleError.prepare(prepared.sourcePath(), prepared.languageId(), prepared.mode(),
                        syntaxDiagnostic.getMessage(), syntaxDiagnostic);
            }
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        }
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
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        } catch (RuntimeException failure) {
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        }
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
                if (cause instanceof NekoModuleError staged) {
                    throw new CompletionException(staged);
                }
                throw new CompletionException(NekoModuleError.execute(resolved.id(), cause.getMessage(), cause));
            });
        } catch (NekoModuleError staged) {
            throw staged;
        } catch (NekoEsmLinkException linkFailure) {
            throw NekoModuleError.link(linkFailure);
        } catch (IOException failure) {
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        } catch (RuntimeException failure) {
            throw NekoModuleError.execute(resolved.id(), failure.getMessage(), failure);
        }
    }

    private NekoPreparedModule prepare(NekoResolvedModule resolved) throws IOException {
        return preparationCache.prepare(resolved.path());
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
            throw new RuntimeException(e);
        }
    }

    private Object resolveToStringUnchecked(String parentPath, String specifier) {
        try {
            return resolveToString(parentPath, specifier);
        } catch (IOException e) {
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
