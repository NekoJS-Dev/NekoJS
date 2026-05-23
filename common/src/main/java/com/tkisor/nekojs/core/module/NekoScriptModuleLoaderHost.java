package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.error.SourceMapRegistry;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkMetadata;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NekoScriptModuleLoaderHost {
    private static final Pattern STACK_LOCATION = Pattern.compile("(\\()([^()\\s]+):(\\d+)(?::(\\d+))?(\\))");

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

    public void captureEsmNamespace(String moduleId, Value namespace) {
        NekoEsmModuleRecord record = esmModuleCache.get(moduleId);
        if (record != null) {
            record.namespace(namespace);
        }
    }

    public void completeEsmNamespace(String moduleId, Value namespace) {
        NekoEsmModuleRecord record = esmModuleCache.get(moduleId);
        if (record != null) {
            record.evaluated(namespace);
        }
    }

    public void failEsmNamespace(String moduleId, Object failure) {
        NekoEsmModuleRecord record = esmModuleCache.get(moduleId);
        if (record != null) {
            record.failure(toThrowable(failure));
        }
    }

    public Object loadEntry(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolver.resolveEntry(entryPath);
        dependencyGraph.markEntry(resolved.id(), resolved.id());
        return loadResolved(resolved);
    }

    public CompletableFuture<?> loadEntryAsync(String entryPath) throws IOException {
        NekoResolvedModule resolved = resolver.resolveEntry(entryPath);
        dependencyGraph.markEntry(resolved.id(), resolved.id());
        return loadResolvedAsync(resolved);
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

    public String resolveImportMeta(String parentPath, String specifier) throws IOException {
        return resolveNativeImport(parentPath, specifier);
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
        invalidateModules(dependencyGraph.affectedModules(resolved.id()));
    }

    public void invalidateModuleTree(String modulePath) throws IOException {
        NekoResolvedModule resolved = resolver.resolveEntry(modulePath);
        invalidateModules(dependencyGraph.dependencyModules(resolved.id()));
    }

    public Object nativeImport(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolved(resolved);
    }

    public CompletableFuture<?> nativeImportAsync(String parentPath, String specifier) throws IOException {
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        recordDependency(parentPath, resolved);
        return loadResolvedAsync(resolved);
    }

    public String resolveNativeImport(String parentPath, String specifier) throws IOException {
        if (isResolvedNativeModuleUri(specifier)) {
            return specifier;
        }
        NekoResolvedModule resolved = resolver.resolve(parentPath, specifier);
        recordDependency(parentPath, resolved);
        if (resolved.special()) {
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

    private void invalidateModules(List<String> moduleIds) {
        for (String moduleId : moduleIds) {
            moduleCache.remove(moduleId);
            esmModuleCache.remove(moduleId);
            dependencyGraph.removeModule(moduleId);
            NekoEsmVirtualModuleRegistry.invalidate(moduleId);
            NekoModulePreparationCache.invalidate(NekoJSPaths.ROOT.resolve(moduleId));
        }
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
        moduleCache.put(resolved.id(), module);
        try {
            module.exports(parseJson(Files.readString(resolved.path())));
            module.loaded(true);
            return module.exports();
        } catch (IOException | RuntimeException | Error e) {
            moduleCache.remove(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            moduleCache.remove(resolved.id());
            throw new IOException("Failed to load JSON module: " + resolved.id(), throwable);
        }
    }

    private Object loadScriptResolved(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        ModuleState cached = moduleCache.get(resolved.id());
        if (cached != null) {
            return cached.exports();
        }

        ModuleState module = newModuleState(resolved.id());
        moduleCache.put(resolved.id(), module);
        try {
            executeScriptModule(resolved, module, prepared);
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
        NekoEsmModuleRecord record = beginEsmModule(resolved, prepared);
        if (record.evaluated() && record.namespace() != null) {
            return record.namespace();
        }
        if (record.asyncEvaluation()) {
            throw asyncEsmRequireError(resolved);
        }
        return captureNamespaceSync(record, esmRewriter.registerModule(resolved.path(), resolved.id(), prepared));
    }

    private CompletableFuture<Value> loadEsmModuleAsync(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleRecord record = beginEsmModule(resolved, prepared);
        if (record.evaluation() != null) {
            return record.evaluation();
        }
        java.net.URI sourceUri = esmRewriter.registerModule(resolved.path(), resolved.id(), prepared);
        return captureNamespaceAsync(record, sourceUri);
    }

    private NekoEsmModuleRecord beginEsmModule(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleRecord cached = esmModuleCache.get(resolved.id());
        if (cached != null) {
            if (cached.state() == NekoEsmModuleState.FAILED) {
                throw new IOException("Native ESM module failed earlier: " + resolved.id(), cached.failure());
            }
            if (cached.linkMetadata() != null) {
                return cached;
            }
        }

        NekoEsmModuleRecord record = cached == null ? new NekoEsmModuleRecord(resolved.id(), resolved.path(), prepared) : cached;
        esmModuleCache.put(resolved.id(), record);
        try {
            record.beginLinking();
            record.linked(esmLinker.link(resolved.id(), resolved.path(), prepared));
            recordStaticDependencies(resolved.id(), record.linkMetadata());
            markAsyncDescendant(record, new HashSet<>());
            return record;
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmModuleCache.remove(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmModuleCache.remove(resolved.id());
            throw new IOException("Failed to link native ESM module: " + resolved.id(), throwable);
        }
    }

    private Value captureNamespaceSync(NekoEsmModuleRecord record, java.net.URI sourceUri) throws IOException {
        CompletableFuture<Value> evaluation = new CompletableFuture<>();
        record.beginEvaluation(evaluation, false);
        String source = "import * as __neko_namespace from " + jsString(sourceUri.toString()) + ";\n"
                + "globalThis.__nekoScriptModuleLoaderHost.completeEsmNamespace(" + jsString(record.id()) + ", __neko_namespace);\n";
        java.net.URI captureUri = NekoEsmVirtualModuleRegistry.register(record.id() + "#namespace-capture:" + sourceUri, source);
        try {
            context.eval(Source.newBuilder("js", source, captureUri.toString())
                    .mimeType("application/javascript+module")
                    .build());
            if (record.namespace() == null) {
                throw new IOException("Failed to capture native ESM namespace: " + record.id());
            }
            return record.namespace();
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmModuleCache.remove(record.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmModuleCache.remove(record.id());
            throw new IOException("Failed to evaluate native ESM module: " + record.id(), throwable);
        }
    }

    private CompletableFuture<Value> captureNamespaceAsync(NekoEsmModuleRecord record, java.net.URI sourceUri) throws IOException {
        CompletableFuture<Value> evaluation = new CompletableFuture<>();
        record.beginEvaluation(evaluation, true);
        String source = "const __neko_error_text = __neko_error => {\n"
                + "  if (__neko_error && __neko_error.stack) return String(__neko_error.stack);\n"
                + "  if (__neko_error && __neko_error.message) return `${__neko_error.name || 'Error'}: ${__neko_error.message}`;\n"
                + "  return String(__neko_error);\n"
                + "};\n"
                + "import(" + jsString(sourceUri.toString()) + ")\n"
                + "  .then(__neko_namespace => globalThis.__nekoScriptModuleLoaderHost.completeEsmNamespace(" + jsString(record.id()) + ", __neko_namespace))\n"
                + "  .catch(__neko_error => globalThis.__nekoScriptModuleLoaderHost.failEsmNamespace(" + jsString(record.id()) + ", __neko_error_text(__neko_error)));\n";
        java.net.URI captureUri = NekoEsmVirtualModuleRegistry.register(record.id() + "#namespace-capture-async:" + sourceUri, source);
        try {
            context.eval(Source.newBuilder("js", source, captureUri.toString()).build());
            return evaluation;
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmModuleCache.remove(record.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmModuleCache.remove(record.id());
            throw new IOException("Failed to evaluate native ESM module: " + record.id(), throwable);
        }
    }

    private NekoPreparedModule prepare(NekoResolvedModule resolved) throws IOException {
        return NekoModulePreparationCache.prepare(resolved.path());
    }

    private void markAsyncDescendant(NekoEsmModuleRecord record, Set<String> visiting) throws IOException {
        if (!visiting.add(record.id())) {
            return;
        }
        try {
            if (record.topLevelAwait()) {
                record.markAsyncEvaluation();
                return;
            }
            NekoEsmLinkMetadata metadata = record.linkMetadata();
            if (metadata == null) {
                return;
            }
            for (var dependency : metadata.dependencies()) {
                NekoResolvedModule resolved = dependency.resolved();
                if (resolved == null || resolved.special() || resolved.json()) {
                    continue;
                }
                NekoPreparedModule prepared = prepare(resolved);
                if (prepared.mode() != NekoModuleMode.ESM) {
                    continue;
                }
                NekoEsmModuleRecord child = linkedEsmRecord(resolved, prepared);
                markAsyncDescendant(child, visiting);
                if (child.asyncEvaluation()) {
                    record.markAsyncEvaluation();
                    return;
                }
            }
        } finally {
            visiting.remove(record.id());
        }
    }

    private NekoEsmModuleRecord linkedEsmRecord(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleRecord cached = esmModuleCache.get(resolved.id());
        if (cached != null) {
            if (cached.state() == NekoEsmModuleState.FAILED) {
                throw new IOException("Native ESM module failed earlier: " + resolved.id(), cached.failure());
            }
            if (cached.linkMetadata() != null) {
                return cached;
            }
        }
        NekoEsmModuleRecord record = cached == null ? new NekoEsmModuleRecord(resolved.id(), resolved.path(), prepared) : cached;
        esmModuleCache.put(resolved.id(), record);
        record.beginLinking();
        record.linked(esmLinker.link(resolved.id(), resolved.path(), prepared));
        recordStaticDependencies(resolved.id(), record.linkMetadata());
        return record;
    }

    private IOException asyncEsmRequireError(NekoResolvedModule resolved) {
        return new IOException("Cannot require async ESM module with top-level await: " + resolved.id() + ". Use import() instead.");
    }

    private Throwable toThrowable(Object failure) {
        if (failure instanceof Throwable throwable) {
            return throwable;
        }
        if (failure instanceof Value value) {
            return new RuntimeException(errorText(value));
        }
        return new RuntimeException(mapStackText(String.valueOf(failure)));
    }

    private String errorText(Value value) {
        String stack = stringMember(value, "stack");
        if (stack != null && !stack.isBlank()) {
            return mapStackText(stack);
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

    private String mapStackText(String stack) {
        String[] lines = stack.split("\\R", -1);
        StringBuilder mapped = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                mapped.append('\n');
            }
            mapped.append(mapStackLine(lines[i]));
        }
        return mapped.toString();
    }

    private String mapStackLine(String line) {
        Matcher matcher = STACK_LOCATION.matcher(line);
        StringBuilder mapped = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(2);
            String displayPath = NekoEsmVirtualModuleRegistry.displayPath(path);
            if (displayPath == null) {
                displayPath = path;
            }
            int lineNumber = Integer.parseInt(matcher.group(3));
            int columnNumber = matcher.group(4) == null ? 1 : Integer.parseInt(matcher.group(4));
            SourceMapRegistry.OriginalPosition position = SourceMapRegistry.getMappedPosition(displayPath, lineNumber, columnNumber);
            String mappedPath = position.path != null && !position.path.isBlank() ? position.path : displayPath;
            String columnSuffix = matcher.group(4) == null ? "" : ":" + position.column;
            matcher.appendReplacement(mapped, Matcher.quoteReplacement(matcher.group(1) + mappedPath + ":" + position.line + columnSuffix + matcher.group(5)));
        }
        matcher.appendTail(mapped);
        return mapped.toString();
    }

    private void recordDependency(String parentPath, NekoResolvedModule child) {
        if (child == null || child.special()) return;
        dependencyGraph.recordDependency(parentPath, child.id());
    }

    private void recordStaticDependencies(String parentId, NekoEsmLinkMetadata metadata) {
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

    private boolean isResolvedNativeModuleUri(String specifier) {
        return specifier != null && specifier.startsWith("file:") && specifier.contains("/.native_esm_modules/");
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

    private static String jsString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
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
