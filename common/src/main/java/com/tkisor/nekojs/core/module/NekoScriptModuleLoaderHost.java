package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.module.esm.NekoEsmLinker;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleState;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.esm.NekoNativeEsmSourceRewriter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NekoScriptModuleLoaderHost {
    private final Context context;
    private final NekoModuleResolver resolver = new NekoModuleResolver();
    private final NekoEsmLinker esmLinker = new NekoEsmLinker(resolver);
    private final NekoNativeEsmSourceRewriter esmRewriter = new NekoNativeEsmSourceRewriter(resolver);
    private final NekoModuleDependencyGraph dependencyGraph = new NekoModuleDependencyGraph();
    private final Map<String, ModuleState> moduleCache = new ConcurrentHashMap<>();
    private final Map<String, NekoEsmModuleRecord> esmModuleCache = new ConcurrentHashMap<>();
    private Value executor;
    private Value moduleFactory;
    private Value specialResolver;
    private Value jsonParser;

    public NekoScriptModuleLoaderHost(Context context) {
        this.context = context;
    }

    public void configure(Value executor, Value moduleFactory, Value specialResolver, Value jsonParser) {
        this.executor = executor;
        this.moduleFactory = moduleFactory;
        this.specialResolver = specialResolver;
        this.jsonParser = jsonParser;
    }

    public Object loadEntry(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolver.resolveEntry(entryPath);
        dependencyGraph.markEntry(resolved.id(), resolved.id());
        return loadResolved(resolved);
    }

    public Object requireFrom(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolved(resolved);
    }

    public String resolveToString(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        return resolved.special() ? resolved.specifier() : resolved.id();
    }

    public void clearCache() {
        moduleCache.clear();
        esmModuleCache.clear();
        dependencyGraph.clear();
        NekoModulePreparationCache.clear();
        NekoEsmVirtualModuleRegistry.clear();
    }

    public void clearRuntimeCache() {
        moduleCache.clear();
        esmModuleCache.clear();
        NekoModulePreparationCache.clear();
        NekoEsmVirtualModuleRegistry.clear();
    }

    public java.util.List<String> affectedEntries(String modulePath) throws IOException {
        NekoResolvedModule resolved = resolver.resolveEntry(modulePath);
        return dependencyGraph.affectedEntries(resolved.id());
    }

    public void invalidateAffectedModules(String modulePath) throws IOException {
        NekoResolvedModule resolved = resolver.resolveEntry(modulePath);
        for (String moduleId : dependencyGraph.affectedModules(resolved.id())) {
            moduleCache.remove(moduleId);
            esmModuleCache.remove(moduleId);
            dependencyGraph.clearDependencies(moduleId);
            NekoEsmVirtualModuleRegistry.invalidate(moduleId);
        }
        if (!resolved.special()) {
            NekoModulePreparationCache.invalidate(resolved.path());
        }
    }

    public Object nativeImport(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolved(resolved);
    }

    public String resolveNativeImport(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        recordDependency(parentPath, resolved);
        if (resolved.special()) {
            return esmRewriter.syntheticObjectModuleUri(resolved.specifier()).toString();
        }
        NekoPreparedModule prepared = prepare(resolved);
        if (resolved.json()) {
            return esmRewriter.syntheticJsonModuleUri(resolved.path()).toString();
        }
        if (prepared.mode() == NekoModuleMode.ESM) {
            return esmRewriter.registerModule(resolved.path(), resolved.id(), prepared).toString();
        }
        return esmRewriter.syntheticCjsModuleUri(resolved.id(), parentPath, specifier).toString();
    }

    private Object loadResolved(NekoResolvedModule resolved) throws IOException {
        if (resolved.special()) {
            return resolveSpecial(resolved.specifier());
        }

        NekoPreparedModule prepared = prepare(resolved);
        if (prepared.mode() == NekoModuleMode.ESM) {
            return loadEsmModule(resolved, prepared);
        }

        ModuleState cached = moduleCache.get(resolved.id());
        if (cached != null) {
            return cached.exports();
        }

        ModuleState module = newModuleState(resolved.id());
        moduleCache.put(resolved.id(), module);
        try {
            if (resolved.json()) {
                module.exports(parseJson(Files.readString(resolved.path())));
            } else {
                executeScriptModule(resolved, module, prepared);
            }
            module.loaded(true);
            return module.exports();
        } catch (IOException | RuntimeException | Error e) {
            moduleCache.remove(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            moduleCache.remove(resolved.id());
            throw new IOException("Failed to load module: " + resolved.id(), throwable);
        }
    }

    private ModuleState newModuleState(String filename) throws IOException {
        if (moduleFactory == null || !moduleFactory.canExecute()) {
            throw new IOException("NekoJS script module factory is unavailable.");
        }
        return new ModuleState(filename, moduleFactory.execute(filename));
    }

    private void executeScriptModule(NekoResolvedModule resolved, ModuleState module, NekoPreparedModule prepared) throws IOException {
        if (executor == null || !executor.canExecute()) {
            throw new IOException("NekoJS script module executor is unavailable.");
        }
        ProxyExecutable require = args -> {
            String specifier = args.length == 0 ? "" : args[0].asString();
            return requireUnchecked(resolved.id(), specifier);
        };
        ProxyExecutable resolve = args -> {
            String specifier = args.length == 0 ? "" : args[0].asString();
            return resolveToStringUnchecked(resolved.id(), specifier);
        };
        executor.execute(module.value(), require, resolve, resolved.id(), resolved.dirname(), prepared.code());
    }

    private Object loadEsmModule(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleRecord cached = esmModuleCache.get(resolved.id());
        if (cached != null) {
            if (cached.state() == NekoEsmModuleState.FAILED) {
                throw new IOException("Native ESM module failed earlier: " + resolved.id(), cached.failure());
            }
            if (cached.namespace() != null) {
                return cached.namespace();
            }
        }

        NekoEsmModuleRecord record = cached == null ? new NekoEsmModuleRecord(resolved.id(), resolved.path(), prepared) : cached;
        esmModuleCache.put(resolved.id(), record);
        try {
            record.state(NekoEsmModuleState.LINKING);
            record.linkMetadata(esmLinker.link(resolved.id(), resolved.path(), prepared));
            recordStaticDependencies(resolved.id(), record.linkMetadata());
            var sourceUri = esmRewriter.registerModule(resolved.path(), resolved.id(), prepared);
            record.state(NekoEsmModuleState.LINKED);
            record.state(NekoEsmModuleState.EVALUATING);
            Value module = context.eval(Source.newBuilder("js", NekoEsmVirtualModuleRegistry.source(java.nio.file.Path.of(sourceUri)), sourceUri.toString())
                    .mimeType("application/javascript+module")
                    .build());
            record.namespace(module);
            record.state(NekoEsmModuleState.EVALUATED);
            return module;
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmModuleCache.remove(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmModuleCache.remove(resolved.id());
            throw new IOException("Failed to evaluate native ESM module: " + resolved.id(), throwable);
        }
    }

    private NekoPreparedModule prepare(NekoResolvedModule resolved) throws IOException {
        return NekoModulePreparationCache.prepare(resolved.path());
    }

    private void recordDependency(String parentPath, NekoResolvedModule child) {
        if (child == null || child.special()) return;
        dependencyGraph.recordDependency(parentPath, child.id());
    }

    private void recordStaticDependencies(String parentId, com.tkisor.nekojs.core.module.esm.NekoEsmLinkMetadata metadata) {
        if (metadata == null) return;
        for (var dependency : metadata.dependencies()) {
            recordDependency(parentId, dependency.resolved());
        }
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

    private Object resolveSpecial(String specifier) throws IOException {
        if (specialResolver == null || !specialResolver.canExecute()) {
            throw new IOException("NekoJS special module resolver is unavailable: " + specifier);
        }
        return specialResolver.execute(specifier).as(Object.class);
    }

    private Object parseJson(String rawJson) throws IOException {
        if (jsonParser == null || !jsonParser.canExecute()) {
            throw new IOException("JSON parser is unavailable for NekoJS script loader.");
        }
        return jsonParser.execute(rawJson);
    }

    private record ModuleState(String filename, Value value) {
        Object exports() {
            return value.getMember("exports");
        }

        void exports(Object exports) {
            value.putMember("exports", exports);
        }

        void loaded(boolean loaded) {
            value.putMember("loaded", loaded);
        }
    }
}
