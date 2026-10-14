package com.tkisor.nekojs.core.pack;

import com.tkisor.nekojs.platform.Platform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Presence/version conditions and deterministic package ordering, separate from live capabilities. */
public record PackDependencyPlan(List<ScriptPack> ordered, Map<String, String> blocked,
                                 Map<String, List<String>> dependencies) {
    public PackDependencyPlan {
        ordered = List.copyOf(ordered);
        blocked = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(blocked));
        Map<String, List<String>> copied = new LinkedHashMap<>();
        dependencies.forEach((key, values) -> copied.put(key, List.copyOf(values)));
        dependencies = java.util.Collections.unmodifiableMap(copied);
    }

    public record Environment(String loader, String minecraft, Map<String, String> mods) {
        public Environment { mods = Map.copyOf(mods); }
        public static Environment current() {
            Map<String, String> mods = new LinkedHashMap<>();
            Platform.getMods().forEach((id, mod) -> mods.put(id, mod.getVersion()));
            return new Environment(Platform.getLoaderId(), Platform.getMcVersion(), mods);
        }
    }

    public static PackDependencyPlan resolve(List<ScriptPack> packs, Environment environment) {
        Map<String, ScriptPack> bySelector = new LinkedHashMap<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        Map<String, List<String>> edges = new LinkedHashMap<>();
        for (ScriptPack pack : packs) {
            if (bySelector.putIfAbsent(pack.selector(), pack) != null) {
                blocked.put(pack.selector(), "duplicate source identity");
            }
            if (!pack.enabled()) blocked.putIfAbsent(pack.selector(), "configured disabled");
            edges.putIfAbsent(pack.selector(), new ArrayList<>());
        }
        for (ScriptPack pack : packs) {
            PackDescriptor descriptor = pack.manifest().descriptor();
            if (descriptor == null) continue;
            String selector = pack.selector();
            String mismatch = environmentMismatch(descriptor, environment);
            if (mismatch != null) blocked.putIfAbsent(selector, mismatch);
            Map<String, String> prerequisites = new LinkedHashMap<>(descriptor.dependencies());
            descriptor.requires().keySet().forEach(provider -> prerequisites.putIfAbsent(provider, "*"));
            for (var dependency : prerequisites.entrySet()) {
                ScriptPack provider = resolveProvider(pack, dependency.getKey(), bySelector);
                if (provider == null) {
                    blocked.putIfAbsent(selector, "dependency " + dependency.getKey() + " is missing in its allowed source");
                } else {
                    edges.get(selector).add(provider.selector());
                    if (!PackVersion.satisfies(provider.version(), dependency.getValue())) {
                        blocked.putIfAbsent(selector, "dependency " + provider.selector() + " version " + provider.version()
                                + " does not satisfy " + dependency.getValue());
                    }
                }
            }
        }
        propagateBlocked(edges, blocked);
        Set<String> remaining = new LinkedHashSet<>(bySelector.keySet());
        remaining.removeAll(blocked.keySet());
        List<ScriptPack> ordered = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<String> ready = remaining.stream().filter(selector -> edges.get(selector).stream()
                    .noneMatch(remaining::contains)).toList();
            if (ready.isEmpty()) {
                for (String selector : remaining) blocked.put(selector, "dependency cycle or dependency on a cycle: "
                        + String.join(", ", edges.get(selector)));
                break;
            }
            for (String selector : ready) ordered.add(bySelector.get(selector));
            remaining.removeAll(ready);
        }
        return new PackDependencyPlan(ordered, blocked, edges);
    }

    private static void propagateBlocked(Map<String, List<String>> edges, Map<String, String> blocked) {
        boolean changed;
        do {
            changed = false;
            for (var entry : edges.entrySet()) {
                if (blocked.containsKey(entry.getKey())) continue;
                for (String provider : entry.getValue()) {
                    if (blocked.containsKey(provider)) {
                        blocked.put(entry.getKey(), "dependency " + provider + " is blocked: " + blocked.get(provider));
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
    }

    private static ScriptPack resolveProvider(ScriptPack consumer, String dependency, Map<String, ScriptPack> packs) {
        if (consumer.scope() == ScriptPackScope.SERVER_CACHE) {
            if (dependency.startsWith("MOD:")) return null;
            // A declared local source in transmitted metadata refers to the same authorized bucket,
            // never to a local trusted pack. Bare ids likewise stay inside the remote batch.
            int colon = dependency.indexOf(':');
            String id = colon < 0 ? dependency : dependency.substring(colon + 1);
            if (dependency.startsWith("SERVER_CACHE:")) return packs.get(dependency);
            String origin = dependency.startsWith("WORLD:") ? "worldpacks" : dependency.startsWith("GLOBAL:") ? "packs"
                    : consumer.root().getFileName().toString().startsWith("worldpacks_") ? "worldpacks" : "packs";
            ScriptPack qualified = packs.get("SERVER_CACHE:" + origin + ":" + id);
            if (qualified != null) return qualified;
            if (colon < 0 && origin.equals("worldpacks")) {
                qualified = packs.get("SERVER_CACHE:packs:" + id);
                if (qualified != null) return qualified;
            }
            return packs.get(ScriptPackScope.SERVER_CACHE.name() + ":" + id);
        }
        if (dependency.contains(":")) {
            if (dependency.startsWith("SERVER_CACHE:") || consumer.scope() != ScriptPackScope.WORLD
                    && dependency.startsWith("WORLD:")) return null;
            return packs.get(dependency);
        }
        ScriptPack sameSource = packs.get(consumer.scope().name() + ":" + dependency);
        if (sameSource != null) return sameSource;
        ScriptPack global = packs.get("GLOBAL:" + dependency);
        if (consumer.scope() == ScriptPackScope.WORLD && global != null) return global;
        ScriptPack mod = packs.get("MOD:" + dependency);
        if (consumer.scope() == ScriptPackScope.GLOBAL || consumer.scope() == ScriptPackScope.WORLD) return mod;
        return consumer.scope() == ScriptPackScope.MOD ? global : null;
    }

    public static ScriptPack providerFor(ScriptPack consumer, String dependency, List<ScriptPack> packs) {
        Map<String, ScriptPack> sources = new LinkedHashMap<>();
        packs.forEach(pack -> sources.putIfAbsent(pack.selector(), pack));
        return resolveProvider(consumer, dependency, sources);
    }

    private static String environmentMismatch(PackDescriptor descriptor, Environment environment) {
        if (!descriptor.loaders().isEmpty() && !descriptor.loaders().contains(environment.loader())) {
            return "loader " + environment.loader() + " is not allowed";
        }
        if (!PackVersion.satisfies(environment.minecraft(), descriptor.minecraft())) {
            return "Minecraft " + environment.minecraft() + " does not satisfy " + descriptor.minecraft();
        }
        for (var mod : descriptor.mods().entrySet()) {
            String version = environment.mods().get(mod.getKey());
            if (version == null || !PackVersion.satisfies(version, mod.getValue())) {
                return "mod " + mod.getKey() + " is missing or does not satisfy " + mod.getValue();
            }
        }
        return null;
    }
}
