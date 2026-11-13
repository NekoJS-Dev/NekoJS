package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.module.esm.NekoEsmLinkCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates module reload invalidation to ensure a consistent sequence across
 * ESM record cache, link cache, CJS runtime cache, virtual module registry, and dependency graph.
 * Prepared module paths and source maps are invalidated by the host based on its runtime-owned
 * module path observations.
 *
 * <p>Extracted from {@link NekoScriptModuleLoaderHost}.
 *
 * <p>Invalidation sequence:
 * <ol>
 *   <li>Bump revision (marks new version, isolates cache keys)</li>
 *   <li>{@code moduleCache.remove} (CJS exports cache)</li>
 *   <li>{@code esmRecordCache.removeAll} (ESM record and evaluation state)</li>
 *   <li>{@code esmLinkCache.removeAll} (link metadata)</li>
 *   <li>{@code dependencyGraph.removeModule} or {@code clearDependencies} (graph nodes/edges)</li>
 *   <li>Invalidate runtime-owned virtual-module registry (virtual URI generation)</li>
 *   <li>Host invalidates runtime-owned pipeline cache and source maps by module path</li>
 * </ol>
 *
 * <p>Failure semantics: If an entry re-executes and fails, its error state points to the new source.
 * Event listeners and timers do not automatically restore old versions. Caches do not mix old and new
 * revisions. Targeted reloads and full reloads reuse this coordinator.
 */
public final class ModuleReloadCoordinator {
    private final Map<String, ModuleState> moduleCache;
    private final NekoEsmModuleRecordCache esmRecordCache;
    private final NekoEsmLinkCache esmLinkCache;
    private final Map<String, Long> moduleRevisions;
    private final NekoModuleDependencyGraph dependencyGraph;
    private final NekoEsmVirtualModuleRegistry virtualModules;

    public ModuleReloadCoordinator(
            Map<String, ModuleState> moduleCache,
            NekoEsmModuleRecordCache esmRecordCache,
            NekoEsmLinkCache esmLinkCache,
            Map<String, Long> moduleRevisions,
            NekoModuleDependencyGraph dependencyGraph,
            NekoModulePipelineCache preparationCache
    ) {
        this.moduleCache = moduleCache;
        this.esmRecordCache = esmRecordCache;
        this.esmLinkCache = esmLinkCache;
        this.moduleRevisions = moduleRevisions;
        this.dependencyGraph = dependencyGraph;
        this.virtualModules = preparationCache.virtualModules();
    }

    /**
     * Clears per-Context state for this host. Runtime-owned registries for prepared modules,
     * source maps, and virtual modules are partitioned and cleared by {@link NekoScriptModuleLoaderHost}
     * according to their {@link ScriptType}.
     */
    public void clearAll() {
        moduleCache.clear();
        esmRecordCache.clear();
        esmLinkCache.clear();
        moduleRevisions.clear();
        dependencyGraph.clear();
    }

    /** Clears runtime caches, similar to {@link #clearAll()}, but retains the dependency graph. */
    public void clearRuntimeCache() {
        moduleCache.clear();
        esmRecordCache.clear();
        esmLinkCache.clear();
        moduleRevisions.clear();
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
            virtualModules.invalidate(moduleId);
        }
    }

    public long bumpRevision(String moduleId) {
        return moduleRevisions.merge(moduleId, 1L, Long::sum);
    }

    public long revision(String moduleId) {
        return moduleRevisions.getOrDefault(moduleId, 0L);
    }

}
