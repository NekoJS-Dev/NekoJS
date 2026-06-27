package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.module.cache.NekoModulePipelineCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmLinkCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmVirtualModuleRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块 reload 失效顺序协调器：统一 prepared cache、ESM record cache、link cache、
 * CJS runtime cache、virtual module registry、source map、dependency graph 的失效顺序。
 *
 * <p>从 {@link NekoScriptModuleLoaderHost} 的 {@code clearCache} / {@code clearRuntimeCache} /
 * {@code invalidateModules} / {@code hotReloadModule} 失效逻辑下沉而来。
 *
 * <p>失效顺序（统一规则）：
 * <ol>
 *   <li>bump revision（标记新版本，cache key 隔离）</li>
 *   <li>moduleCache.remove（CJS exports cache）</li>
 *   <li>esmRecordCache.removeAll（ESM record + evaluation state）</li>
 *   <li>esmLinkCache.removeAll（link metadata）</li>
 *   <li>dependencyGraph.removeModule / clearDependencies（依赖图节点/边）</li>
 *   <li>NekoEsmVirtualModuleRegistry.invalidate / clear（virtual URI generation）</li>
 *   <li>NekoModulePreparationCache.invalidate / clear（prepared module + source map）</li>
 * </ol>
 *
 * <p>明确失败语义：entry 重新执行失败后错误状态指向新源码；event listener/timer 不自动恢复旧版本；
 * cache 不混用旧/新 revision。targeted reload 和 full reload 复用同一 coordinator。
 */
public final class ModuleReloadCoordinator {
    private final Map<String, ModuleState> moduleCache;
    private final NekoEsmModuleRecordCache esmRecordCache;
    private final NekoEsmLinkCache esmLinkCache;
    private final Map<String, Long> moduleRevisions;
    private final NekoModuleDependencyGraph dependencyGraph;
    private final NekoJSPaths paths;

    public ModuleReloadCoordinator(
            Map<String, ModuleState> moduleCache,
            NekoEsmModuleRecordCache esmRecordCache,
            NekoEsmLinkCache esmLinkCache,
            Map<String, Long> moduleRevisions,
            NekoModuleDependencyGraph dependencyGraph
    ) {
        this.moduleCache = moduleCache;
        this.esmRecordCache = esmRecordCache;
        this.esmLinkCache = esmLinkCache;
        this.moduleRevisions = moduleRevisions;
        this.dependencyGraph = dependencyGraph;
        this.paths = NekoJSPaths.get();
    }

    public void clearAll() {
        moduleCache.clear();
        esmRecordCache.clear();
        esmLinkCache.clear();
        moduleRevisions.clear();
        dependencyGraph.clear();
        NekoModulePipelineCache.clear();
        NekoEsmVirtualModuleRegistry.clear();
    }

    public void clearRuntimeCache() {
        moduleCache.clear();
        esmRecordCache.clear();
        esmLinkCache.clear();
        moduleRevisions.clear();
        NekoModulePipelineCache.clear();
        NekoEsmVirtualModuleRegistry.clear();
    }

    public void invalidateModules(List<String> moduleIds, boolean removeGraphNodes) {
        for (String moduleId : moduleIds) {
            bumpRevision(moduleId);
            moduleCache.remove(moduleId);
            esmRecordCache.removeAll(moduleId);
            esmLinkCache.removeAll(moduleId);
            if (removeGraphNodes) {
                dependencyGraph.removeModule(moduleId);
            }
            NekoEsmVirtualModuleRegistry.invalidate(moduleId);
            NekoModulePipelineCache.invalidate(paths.root().resolve(moduleId));
        }
    }

    public long bumpRevision(String moduleId) {
        return moduleRevisions.merge(moduleId, 1L, Long::sum);
    }

    public long revision(String moduleId) {
        return moduleRevisions.getOrDefault(moduleId, 0L);
    }

    public void invalidateSingleModule(String moduleId, Path path) {
        bumpRevision(moduleId);
        moduleCache.remove(moduleId);
        esmRecordCache.removeAll(moduleId);
        esmLinkCache.remove(moduleId, revision(moduleId));
        NekoModulePipelineCache.invalidate(path);
    }
}
