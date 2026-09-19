package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.module.esm.NekoEsmLinkCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.NekoEsmVirtualModuleRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块 reload 失效顺序协调器：统一 ESM record cache、link cache、CJS runtime cache、
 * virtual module registry 与 dependency graph 的失效顺序；prepared/source-map 路径由 host
 * 根据其 runtime-owned module path observation 负责失效。
 *
 * <p>从 {@link NekoScriptModuleLoaderHost} 的 {@code clearCache} / {@code clearRuntimeCache} /
 * {@code invalidateModules} 失效逻辑下沉而来。
 *
 * <p>失效顺序（统一规则）：
 * <ol>
 *   <li>bump revision（标记新版本，cache key 隔离）</li>
 *   <li>moduleCache.remove（CJS exports cache）</li>
 *   <li>esmRecordCache.removeAll（ESM record + evaluation state）</li>
 *   <li>esmLinkCache.removeAll（link metadata）</li>
 *   <li>dependencyGraph.removeModule / clearDependencies（依赖图节点/边）</li>
 *   <li>注入的 runtime-owned virtual-module registry invalidate（virtual URI generation）</li>
 *   <li>host 按 module path 失效 runtime-owned pipeline cache 与 source map（避免隐式 global root）</li>
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
     * 清空本 host 的 per-Context 状态。prepared/source-map/virtual-module 的 runtime-owned
     * 注册表由 {@link NekoScriptModuleLoaderHost} 按自身 ScriptType 分区清理。
     */
    public void clearAll() {
        moduleCache.clear();
        esmRecordCache.clear();
        esmLinkCache.clear();
        moduleRevisions.clear();
        dependencyGraph.clear();
    }

    /** 同 {@link #clearAll()}，但保留依赖图（运行缓存清理，依赖关系仍有效）。 */
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
