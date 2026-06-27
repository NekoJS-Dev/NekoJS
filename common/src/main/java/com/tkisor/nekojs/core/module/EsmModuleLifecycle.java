package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.api.compiler.NekoModuleMode;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkMetadata;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleState;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;
import com.tkisor.nekojs.core.module.esm.NekoNativeEsmSourceRewriter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Source;
import graal.graalvm.polyglot.Value;

import java.io.IOException;

/**
 * ESM 模块生命周期管理：负责 ESM 模块的加载、链接、命名空间捕获和 async descendant 标记。
 *
 * <p>从 {@link NekoScriptModuleLoaderHost} 提取，处理 ESM 特有的 record 创建、
 * 依赖链接、TLA 传播等逻辑。
 */
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * ESM 模块生命周期：record 创建/链接/求值、命名空间捕获（sync/async）、
 * async 后代标记和静态依赖图记录。
 *
 * <p>从 {@link NekoScriptModuleLoaderHost} 的 ESM 相关方法下沉而来。
 * 构造器接收具体依赖（record cache / link cache / dependency graph / rewriter /
 * Context / revision supplier / prepare function），不注入完整 LoaderHost。
 */
public final class EsmModuleLifecycle {
    private final NekoEsmModuleRecordCache esmRecordCache;
    private final NekoEsmLinkCache esmLinkCache;
    private final NekoModuleDependencyGraph dependencyGraph;
    private final NekoNativeEsmSourceRewriter esmRewriter;
    private final Context context;
    private final Function<String, Long> revision;
    private final ModulePreparation prepare;

    @FunctionalInterface
    public interface ModulePreparation {
        NekoPreparedModule prepare(NekoResolvedModule resolved) throws IOException;
    }

    public EsmModuleLifecycle(
            NekoEsmModuleRecordCache esmRecordCache,
            NekoEsmLinkCache esmLinkCache,
            NekoModuleDependencyGraph dependencyGraph,
            NekoNativeEsmSourceRewriter esmRewriter,
            Context context,
            Function<String, Long> revision,
            ModulePreparation prepare
    ) {
        this.esmRecordCache = esmRecordCache;
        this.esmLinkCache = esmLinkCache;
        this.dependencyGraph = dependencyGraph;
        this.esmRewriter = esmRewriter;
        this.context = context;
        this.revision = revision;
        this.prepare = prepare;
    }

    public Object loadEsmModule(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleRecord record = beginEsmModule(resolved, prepared);
        if (record.evaluated() && record.namespace() != null) {
            return record.namespace();
        }
        if (record.asyncEvaluation()) {
            throw asyncEsmRequireError(resolved);
        }
        return captureNamespaceSync(record, esmRewriter.registerModule(resolved.path(), resolved.id(), prepared));
    }

    public CompletableFuture<Value> loadEsmModuleAsync(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        NekoEsmModuleRecord record = beginEsmModule(resolved, prepared);
        if (record.evaluation() != null) {
            return record.evaluation();
        }
        java.net.URI sourceUri = esmRewriter.registerModule(resolved.path(), resolved.id(), prepared);
        return captureNamespaceAsync(record, sourceUri);
    }

    public NekoEsmModuleRecord beginEsmModule(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        long rev = revision.apply(resolved.id());
        NekoEsmModuleRecord cached = esmRecordCache.get(resolved.id(), rev);
        if (cached != null) {
            if (cached.state() == NekoEsmModuleState.FAILED) {
                throw new IOException("Native ESM module failed earlier: " + resolved.id(), cached.failure());
            }
            if (cached.linkMetadata() != null) {
                return cached;
            }
        }

        NekoEsmModuleRecord record = cached == null ? esmRecordCache.getOrCreate(resolved.id(), rev, resolved.path(), prepared) : cached;
        try {
            record.beginLinking();
            record.linked(esmLinkCache.link(resolved.id(), rev, resolved.path(), prepared));
            recordStaticDependencies(resolved.id(), record.linkMetadata());
            markAsyncDescendant(record, new HashSet<>());
            return record;
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmRecordCache.removeAll(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmRecordCache.removeAll(resolved.id());
            throw new IOException("Failed to link native ESM module: " + resolved.id(), throwable);
        }
    }

    public NekoEsmModuleRecord linkedEsmRecord(NekoResolvedModule resolved, NekoPreparedModule prepared) throws IOException {
        long rev = revision.apply(resolved.id());
        NekoEsmModuleRecord cached = esmRecordCache.get(resolved.id(), rev);
        if (cached != null) {
            if (cached.state() == NekoEsmModuleState.FAILED) {
                throw new IOException("Native ESM module failed earlier: " + resolved.id(), cached.failure());
            }
            if (cached.linkMetadata() != null) {
                return cached;
            }
        }
        NekoEsmModuleRecord record = cached == null ? esmRecordCache.getOrCreate(resolved.id(), rev, resolved.path(), prepared) : cached;
        try {
            record.beginLinking();
            record.linked(esmLinkCache.link(resolved.id(), rev, resolved.path(), prepared));
            recordStaticDependencies(resolved.id(), record.linkMetadata());
            return record;
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmRecordCache.removeAll(resolved.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmRecordCache.removeAll(resolved.id());
            throw new IOException("Failed to link native ESM module: " + resolved.id(), throwable);
        }
    }

    private Value captureNamespaceSync(NekoEsmModuleRecord record, java.net.URI sourceUri) throws IOException {
        CompletableFuture<Value> evaluation = new CompletableFuture<>();
        record.beginEvaluation(evaluation, false);
        long rev = revision.apply(record.id());
        String source = "import * as __neko_namespace from " + jsString(sourceUri.toString()) + ";\n"
                + "globalThis.__nekoScriptModuleLoaderHost.completeEsmNamespace(" + jsString(record.id()) + ", " + rev + ", __neko_namespace);\n";
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
            esmRecordCache.removeAll(record.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmRecordCache.removeAll(record.id());
            throw new IOException("Failed to evaluate native ESM module: " + record.id(), throwable);
        }
    }

    private CompletableFuture<Value> captureNamespaceAsync(NekoEsmModuleRecord record, java.net.URI sourceUri) throws IOException {
        CompletableFuture<Value> evaluation = new CompletableFuture<>();
        record.beginEvaluation(evaluation, true);
        long rev = revision.apply(record.id());
        String source = "const __neko_error_text = __neko_error => {\n"
                + "  if (__neko_error && __neko_error.stack) return String(__neko_error.stack);\n"
                + "  if (__neko_error && __neko_error.message) return `${__neko_error.name || 'Error'}: ${__neko_error.message}`;\n"
                + "  return String(__neko_error);\n"
                + "};\n"
                + "import(" + jsString(sourceUri.toString()) + ")\n"
                + "  .then(__neko_namespace => globalThis.__nekoScriptModuleLoaderHost.completeEsmNamespace(" + jsString(record.id()) + ", " + rev + ", __neko_namespace))\n"
                + "  .catch(__neko_error => globalThis.__nekoScriptModuleLoaderHost.failEsmNamespace(" + jsString(record.id()) + ", " + rev + ", __neko_error_text(__neko_error)));\n";
        java.net.URI captureUri = NekoEsmVirtualModuleRegistry.register(record.id() + "#namespace-capture-async:" + sourceUri, source);
        try {
            context.eval(Source.newBuilder("js", source, captureUri.toString()).build());
            return evaluation;
        } catch (IOException | RuntimeException | Error e) {
            record.failure(e);
            esmRecordCache.removeAll(record.id());
            throw e;
        } catch (Throwable throwable) {
            record.failure(throwable);
            esmRecordCache.removeAll(record.id());
            throw new IOException("Failed to evaluate native ESM module: " + record.id(), throwable);
        }
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
                NekoPreparedModule childPrepared = prepare.prepare(resolved);
                if (childPrepared.mode() != NekoModuleMode.ESM) {
                    continue;
                }
                NekoEsmModuleRecord child = linkedEsmRecord(resolved, childPrepared);
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

    private void recordStaticDependencies(String parentId, NekoEsmLinkMetadata metadata) {
        if (metadata == null) return;
        dependencyGraph.clearDependencies(parentId);
        for (var dependency : metadata.dependencies()) {
            recordDependency(parentId, dependency.resolved());
        }
    }

    private void recordDependency(String parentPath, NekoResolvedModule child) {
        if (child == null || child.special()) return;
        dependencyGraph.recordDependency(parentPath, child.id());
    }

    private static IOException asyncEsmRequireError(NekoResolvedModule resolved) {
        return new IOException("Cannot require async ESM module with top-level await: " + resolved.id() + ". Use import() instead.");
    }

    private static String jsString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r") + "'";
    }
}
