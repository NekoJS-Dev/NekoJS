package com.tkisor.nekojs.api.module;

import com.tkisor.nekojs.api.capability.ActiveCapability;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ApiModuleResolver {

    private ApiModuleResolver() {
    }

    public static ModuleResolution resolve(
            EnvironmentKey environmentKey,
            ApiVersion portableApiVersion,
            List<ApiModuleDescriptor> descriptors,
            List<ActiveCapability> capabilities) {

        Objects.requireNonNull(environmentKey, "environmentKey");
        Objects.requireNonNull(descriptors, "descriptors");
        Objects.requireNonNull(capabilities, "capabilities");

        // Index descriptors by moduleId
        Map<String, ApiModuleDescriptor> descriptorMap = new LinkedHashMap<>();
        for (ApiModuleDescriptor desc : descriptors) {
            if (descriptorMap.containsKey(desc.moduleId())) {
                throw new ApiResolutionException("DUPLICATE_MODULE_DESCRIPTOR",
                        "Duplicate module descriptor: " + desc.moduleId(),
                        Map.of("module", desc.moduleId()));
            }
            descriptorMap.put(desc.moduleId(), desc);
        }

        // Index capabilities by name
        Map<String, ActiveCapability> capabilityMap = new HashMap<>();
        for (ActiveCapability cap : capabilities) {
            capabilityMap.put(cap.name(), cap);
        }

        // Step 1: Cycle detection on full descriptor universe (module dependencies only)
        detectCycles(descriptors);

        // Step 2: Build dependency graph and classify dependencies
        Map<String, Set<String>> dependentsOf = new HashMap<>();
        Map<String, Set<String>> dependenciesOf = new HashMap<>();

        for (ApiModuleDescriptor desc : descriptors) {
            dependenciesOf.putIfAbsent(desc.moduleId(), new HashSet<>());
            dependentsOf.putIfAbsent(desc.moduleId(), new HashSet<>());
        }

        Map<String, ModuleStatus> statusMap = new LinkedHashMap<>();

        for (ApiModuleDescriptor desc : descriptors) {
            for (ApiModuleDependency dep : desc.dependencies()) {
                switch (dep.type()) {
                    case CAPABILITY -> {
                        // Capability dependency: check availability
                        ActiveCapability cap = capabilityMap.get(dep.moduleId());
                        if (cap == null) {
                            statusMap.putIfAbsent(desc.moduleId(), new ModuleStatus(
                                    InactiveReason.CAPABILITY_UNAVAILABLE,
                                    "Required capability unavailable: " + dep.moduleId()));
                        } else if (dep.versionRange() != null
                                && !dep.versionRange().matches(portableApiVersion)) {
                            statusMap.putIfAbsent(desc.moduleId(), new ModuleStatus(
                                    InactiveReason.CAPABILITY_UNAVAILABLE,
                                    "Capability version mismatch: " + dep.moduleId()));
                        }
                    }
                    case MODULE -> {
                        // Module dependency: validate target exists, then add to DAG
                        if (!descriptorMap.containsKey(dep.moduleId())) {
                            statusMap.putIfAbsent(desc.moduleId(), new ModuleStatus(
                                    InactiveReason.MISSING_MODULE_DEPENDENCY,
                                    "Missing dependency: " + dep.moduleId()));
                        } else {
                            dependenciesOf.computeIfAbsent(desc.moduleId(), k -> new HashSet<>())
                                    .add(dep.moduleId());
                            dependentsOf.computeIfAbsent(dep.moduleId(), k -> new HashSet<>())
                                    .add(desc.moduleId());
                        }
                    }
                    case PORTABLE_STABLE -> {
                        // Portable-stable dependency: check against portableApiVersion
                        if (dep.versionRange() != null && !dep.versionRange().matches(portableApiVersion)) {
                            statusMap.putIfAbsent(desc.moduleId(), new ModuleStatus(
                                    InactiveReason.MODULE_VERSION_MISMATCH,
                                    "Portable API version " + portableApiVersion
                                            + " not in range " + dep.versionRange()));
                        }
                    }
                }
            }
        }

        // Step 3: Propagate inactive status through DAG (dependency-first)
        List<String> topoOrder = topologicalSort(descriptors, dependenciesOf);

        for (String moduleId : topoOrder) {
            ModuleStatus currentStatus = statusMap.get(moduleId);
            if (currentStatus != null && currentStatus.reason != null) {
                propagateInactive(moduleId, dependentsOf, statusMap);
                continue;
            }

            // Check if all module dependencies are active
            Set<String> deps = dependenciesOf.getOrDefault(moduleId, Set.of());
            for (String depId : deps) {
                ModuleStatus depStatus = statusMap.get(depId);
                if (depStatus != null && depStatus.reason != null) {
                    statusMap.put(moduleId, new ModuleStatus(
                            InactiveReason.DEPENDENCY_INACTIVE,
                            "Dependency '" + depId + "' is inactive: " + depStatus.reason));
                    propagateInactive(moduleId, dependentsOf, statusMap);
                    break;
                }
            }
        }

        // Step 4: Build result with deterministic ordering (topological order, code-point within same level)
        List<ActiveModule> active = new ArrayList<>();
        List<InactiveModule> inactive = new ArrayList<>();

        // Use topological order for active modules (already dependency-first, code-point within same level)
        for (String id : topoOrder) {
            ModuleStatus status = statusMap.get(id);
            if (status == null || status.reason == null) {
                active.add(new ActiveModule(descriptorMap.get(id), null));
            }
        }

        for (ApiModuleDescriptor desc : descriptors) {
            ModuleStatus status = statusMap.get(desc.moduleId());
            if (status != null && status.reason != null) {
                inactive.add(new InactiveModule(desc, status.reason, status.detail));
            }
        }

        return new ModuleResolution(active, inactive);
    }

    private static void detectCycles(List<ApiModuleDescriptor> descriptors) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (ApiModuleDescriptor desc : descriptors) {
            adjacency.putIfAbsent(desc.moduleId(), new HashSet<>());
            for (ApiModuleDependency dep : desc.dependencies()) {
                if (dep.type() == ApiModuleDependency.DependencyType.MODULE) {
                    adjacency.computeIfAbsent(desc.moduleId(), k -> new HashSet<>())
                            .add(dep.moduleId());
                }
            }
        }

        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();

        for (String node : adjacency.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycle(node, adjacency, visited, inStack)) {
                    throw new ApiResolutionException("MODULE_DEPENDENCY_CYCLE",
                            "Module dependency cycle detected",
                            Map.of("cycle", String.join(" -> ", inStack)));
                }
            }
        }
    }

    private static boolean hasCycle(
            String node,
            Map<String, Set<String>> adjacency,
            Set<String> visited,
            Set<String> inStack) {

        visited.add(node);
        inStack.add(node);

        Set<String> neighbors = adjacency.getOrDefault(node, Set.of());
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                if (hasCycle(neighbor, adjacency, visited, inStack)) {
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                return true;
            }
        }

        inStack.remove(node);
        return false;
    }

    private static void propagateInactive(
            String inactiveModuleId,
            Map<String, Set<String>> dependentsOf,
            Map<String, ModuleStatus> statusMap) {

        Set<String> dependents = dependentsOf.getOrDefault(inactiveModuleId, Set.of());
        for (String dependentId : dependents) {
            ModuleStatus currentStatus = statusMap.get(dependentId);
            if (currentStatus == null || currentStatus.reason == null) {
                statusMap.put(dependentId, new ModuleStatus(
                        InactiveReason.DEPENDENCY_INACTIVE,
                        "Dependency '" + inactiveModuleId + "' is inactive"));
                propagateInactive(dependentId, dependentsOf, statusMap);
            }
        }
    }

    private static List<String> topologicalSort(
            List<ApiModuleDescriptor> descriptors,
            Map<String, Set<String>> dependenciesOf) {

        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        List<String> sortedIds = descriptors.stream()
                .map(ApiModuleDescriptor::moduleId)
                .sorted()
                .toList();

        for (String id : sortedIds) {
            if (!visited.contains(id)) {
                visit(id, dependenciesOf, visited, result);
            }
        }

        return result;
    }

    private static void visit(
            String node,
            Map<String, Set<String>> dependenciesOf,
            Set<String> visited,
            List<String> result) {

        if (visited.contains(node)) {
            return;
        }

        visited.add(node);

        Set<String> deps = dependenciesOf.getOrDefault(node, Set.of());
        List<String> sortedDeps = new ArrayList<>(deps);
        sortedDeps.sort(String::compareTo);
        for (String dep : sortedDeps) {
            visit(dep, dependenciesOf, visited, result);
        }

        result.add(node);
    }

    private static final class ModuleStatus {
        final InactiveReason reason;
        final String detail;

        ModuleStatus(InactiveReason reason, String detail) {
            this.reason = reason;
            this.detail = detail;
        }
    }
}
