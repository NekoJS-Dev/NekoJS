package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.module.esm.NekoEsmLinkMetadata;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecord;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleRecordCache;
import com.tkisor.nekojs.core.module.esm.NekoEsmModuleState;

import graal.graalvm.polyglot.Value;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dependency-aware hot-reloader for ESM modules.
 *
 * <p>When a helper module changes, finds all modules transitively depending on it,
 * snapshot their current state, and re-link them in topological order (dependencies first).
 * If any link fails, all previously re-linked modules are rolled back to their snapshots.
 *
 * <p>This avoids re-running entire entry scripts when a dependency file changes.
 */
final class ModuleSliceRelinker {
    private final NekoModuleDependencyGraph dependencyGraph;
    private final NekoEsmModuleRecordCache recordCache;
    private final LinkFunction linker;

    ModuleSliceRelinker(NekoModuleDependencyGraph dependencyGraph,
                        NekoEsmModuleRecordCache recordCache,
                        LinkFunction linker) {
        this.dependencyGraph = dependencyGraph;
        this.recordCache = recordCache;
        this.linker = linker;
    }

    SliceResult tryRelinkSlice(String changedModuleId, long newRevision) throws IOException {
        List<String> affectedModules = dependencyGraph.affectedModules(changedModuleId);
        if (affectedModules.isEmpty()) {
            return new SliceResult(List.of(), List.of(), false);
        }

        // Build topologically sorted list: dependents after dependencies
        List<String> sorted = topologicalSort(affectedModules);

        // Phase 1: Snapshot current state of all affected modules for rollback
        Map<String, ModuleSnapshot> snapshots = new LinkedHashMap<>();
        for (String moduleId : sorted) {
            NekoEsmModuleRecord record = recordCache.get(moduleId, newRevision);
            snapshots.put(moduleId, record != null ? ModuleSnapshot.from(record) : ModuleSnapshot.empty());
        }

        // Phase 2: Relink all affected modules (bottom-up: dependencies first)
        List<String> relinked = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        Map<String, NekoEsmModuleRecord> newRecords = new LinkedHashMap<>();

        for (String moduleId : sorted) {
            try {
                NekoEsmModuleRecord record = recordCache.get(moduleId, newRevision);
                if (record == null || record.preparedModule() == null) {
                    continue;
                }
                NekoEsmLinkMetadata metadata = linker.link(moduleId, newRevision, record.path(), record.preparedModule());
                record.relink(metadata);
                newRecords.put(moduleId, record);
                relinked.add(moduleId);
            } catch (Exception e) {
                failed.add(moduleId);
                // Rollback all previously relinked modules
                for (String relinkedId : relinked) {
                    ModuleSnapshot snapshot = snapshots.get(relinkedId);
                    if (snapshot != null) {
                        snapshot.restore(recordCache.get(relinkedId, newRevision));
                    }
                }
                return new SliceResult(relinked, failed, true);
            }
        }

        // Phase 3: Invalidate old virtual module URIs and register new ones
        // (This is handled by the caller since it requires the rewriter)

        return new SliceResult(relinked, failed, false);
    }

    private List<String> topologicalSort(List<String> modules) {
        List<String> result = new ArrayList<>();
        Map<String, Boolean> visited = new IdentityHashMap<>();
        for (String module : modules) {
            dfs(module, visited, result);
        }
        return result;
    }

    private void dfs(String moduleId, Map<String, Boolean> visited, List<String> result) {
        if (visited.containsKey(moduleId)) return;
        visited.put(moduleId, true);
        for (String child : dependencyGraph.dependencyModules(moduleId)) {
            dfs(child, visited, result);
        }
        result.addLast(moduleId);
    }

    interface LinkFunction {
        NekoEsmLinkMetadata link(String moduleId, long revision, Path path, NekoPreparedModule prepared) throws IOException;
    }

    record SliceResult(List<String> relinked, List<String> failed, boolean rolledBack) {}

    private record ModuleSnapshot(NekoEsmLinkMetadata metadata, Value namespace, NekoEsmModuleState state) {
        static ModuleSnapshot from(NekoEsmModuleRecord record) {
            return new ModuleSnapshot(record.linkMetadata(), record.namespace(), record.state());
        }

        static ModuleSnapshot empty() {
            return new ModuleSnapshot(null, null, NekoEsmModuleState.NEW);
        }

        void restore(NekoEsmModuleRecord record) {
            if (record == null) return;
            if (metadata != null) {
                record.relink(metadata);
            }
            if (namespace != null) {
                record.namespace(namespace);
            }
        }
    }
}
