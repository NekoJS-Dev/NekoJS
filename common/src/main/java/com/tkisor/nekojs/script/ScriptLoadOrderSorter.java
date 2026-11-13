package com.tkisor.nekojs.script;

import com.tkisor.nekojs.script.prop.ScriptProperty;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Works out the order scripts load in. The main order is a stable sort by
 * {@link ScriptProperty#PRIORITY} from high to low; within one priority, scripts that declare
 * {@link ScriptProperty#AFTER} dependencies are topologically sorted with Kahn's algorithm, keeping
 * ties stable.
 *
 * <p>Rules for resolving an {@code after:} reference (lenient):
 * <ul>
 * <li>{@code aaa/bbb.js} — relative to the script type root;</li>
 * <li>{@code ./ccc.js} — relative to the directory of the declaring script;</li>
 * <li>{@code aaa/*} — every file under the script type root {@code aaa/};</li>
 * <li>a {@code nekojs/}, {@code nekojs:<type>/} or {@code <type>/} prefix is allowed, and a
 * backslash counts as {@code /};</li>
 * <li>unknown references are ignored and recorded in the diagnostics; cyclic dependencies fall back
 * to the original stable order.</li>
 * </ul>
 *
 * <p>A dependency edge only applies between scripts in the same priority group that will both run
 * ({@code shouldRun}). A reference to another priority group or to a script that will not run is
 * allowed and silently ignored.
 *
 * <p>Determinism: scripts that {@code after} cannot order keep their original relative order after
 * the stable priority sort (the discovery order, which matches path lexicographic order), so the
 * result is reproducible from the input batch order.
 */
final class ScriptLoadOrderSorter {

    private ScriptLoadOrderSorter() {
    }

    /** Diagnostics of one sort pass: whether problems occurred, plus description fragments that can be
     * joined into a log line. */
    static final class Result {
        private final List<String> problems;

        Result(List<String> problems) {
            this.problems = problems;
        }

        boolean hasProblems() {
            return !problems.isEmpty();
        }

        List<String> problems() {
            return List.copyOf(problems);
        }

        String describe() {
            return String.join("；", problems);
        }
    }

    /** One resolved {@code after} reference: {@code key} is a path relative to the script root, and
     * {@code glob} marks a directory wildcard. */
    private record Ref(String key, boolean glob) {

        /** Whether this reference matches any script key in the batch, whatever its priority or whether it
         * runs. */
        boolean known(Map<String, Boolean> knownKeys) {
            if (!glob) {
                return knownKeys.containsKey(key);
            }
            for (String candidate : knownKeys.keySet()) {
                if (matchesKey(candidate)) {
                    return true;
                }
            }
            return false;
        }

        boolean matchesKey(String candidate) {
            if (!glob) {
                return key.equals(candidate);
            }
            return key.isEmpty() || candidate.startsWith(key + "/");
        }
    }

    /**
     * Reorders the script list in place: first a stable sort by priority from high to low, then a stable
     * topological sort of the {@code after:} dependencies within each group of equal priority.
     *
     * @param scripts   the script batch to sort (the same list passed to {@code loadScriptsInto}); it is
     *                  reordered in place
     * @param shouldRun decides whether a script will really run ({@link ScriptContainer#shouldRun()});
     *                  a dependency edge only applies when both sides run
     * @return the diagnostics; when there are no problems, {@link Result#hasProblems()} is {@code false}
     */
    static Result applyAfterOrder(List<ScriptContainer> scripts, Predicate<ScriptContainer> shouldRun) {
        List<String> problems = new ArrayList<>();

        // 1. Stable sort by priority from high to low; this sets the main order.
        scripts.sort((s1, s2) -> {
            int p1 = s1.properties.getOrDefault(ScriptProperty.PRIORITY);
            int p2 = s2.properties.getOrDefault(ScriptProperty.PRIORITY);
            return Integer.compare(p2, p1);
        });

        // 2. Known keys of the whole batch: tells an unknown reference (warned) apart from a known one in
        // another priority group or in a script that does not run (silent).
        Map<String, Boolean> knownKeys = new HashMap<>();
        for (ScriptContainer script : scripts) {
            knownKeys.putIfAbsent(keyOf(script), shouldRun.test(script));
        }

        // 3. Stable topological sort of each priority group.
        int i = 0;
        while (i < scripts.size()) {
            int priority = scripts.get(i).properties.getOrDefault(ScriptProperty.PRIORITY);
            int end = i + 1;
            while (end < scripts.size() && scripts.get(end).properties.getOrDefault(ScriptProperty.PRIORITY) == priority) {
                end++;
            }
            if (end - i > 1) {
                topoSortGroup(scripts, i, end, shouldRun, knownKeys, problems);
            }
            i = end;
        }

        applyPackageOrder(scripts, shouldRun, problems);
        return new Result(problems);
    }

    /** Package prerequisites override file preferences; unrelated legacy order stays stable. */
    private static void applyPackageOrder(List<ScriptContainer> scripts, Predicate<ScriptContainer> shouldRun,
                                          List<String> problems) {
        if (scripts.stream().allMatch(script -> script.packDependencies.isEmpty())) return;
        Map<String, Integer> outstanding = new HashMap<>();
        boolean[] runnable = new boolean[scripts.size()];
        String[] selectors = new String[scripts.size()];
        for (int index = 0; index < scripts.size(); index++) {
            ScriptContainer script = scripts.get(index);
            runnable[index] = shouldRun.test(script);
            selectors[index] = script.packId == null ? null : script.packScope.name() + ":" + script.packId;
            if (runnable[index] && selectors[index] != null) outstanding.merge(selectors[index], 1, Integer::sum);
        }
        int[] waiting = new int[scripts.size()];
        Map<String, List<Integer>> consumers = new HashMap<>();
        java.util.PriorityQueue<Integer> ready = new java.util.PriorityQueue<>();
        for (int index = 0; index < scripts.size(); index++) {
            if (runnable[index]) {
                for (String provider : scripts.get(index).packDependencies) {
                    if (outstanding.getOrDefault(provider, 0) == 0) continue;
                    waiting[index]++;
                    consumers.computeIfAbsent(provider, ignored -> new ArrayList<>()).add(index);
                }
            }
            if (waiting[index] == 0) ready.add(index);
        }
        List<ScriptContainer> ordered = new ArrayList<>();
        boolean[] emitted = new boolean[scripts.size()];
        while (!ready.isEmpty()) {
            int index = ready.remove();
            emitted[index] = true;
            ordered.add(scripts.get(index));
            String provider = selectors[index];
            if (runnable[index] && provider != null && outstanding.merge(provider, -1, Integer::sum) == 0) {
                for (int consumer : consumers.getOrDefault(provider, List.of())) {
                    if (--waiting[consumer] == 0) ready.add(consumer);
                }
            }
        }
        if (ordered.size() != scripts.size()) {
            problems.add("Package dependency order cannot be resolved");
            for (int index = 0; index < scripts.size(); index++) if (!emitted[index]) ordered.add(scripts.get(index));
        }
        scripts.clear();
        scripts.addAll(ordered);
    }

    /** Stable topological sort of the {@code [from, to)} range (one priority), written back in place. */
    private static void topoSortGroup(List<ScriptContainer> scripts, int from, int to,
                                      Predicate<ScriptContainer> shouldRun,
                                      Map<String, Boolean> knownKeys, List<String> problems) {
        int n = to - from;

        // key → index within the group; only scripts that run can be dependency targets.
        Map<String, Integer> indexByKey = new HashMap<>();
        for (int k = from; k < to; k++) {
            ScriptContainer script = scripts.get(k);
            if (shouldRun.test(script)) {
                indexByKey.putIfAbsent(keyOf(script), k - from);
            }
        }

        int[] inDegree = new int[n];
        List<List<Integer>> successors = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            successors.add(new ArrayList<>());
        }

        List<String> unresolved = new ArrayList<>();
        for (int k = from; k < to; k++) {
            ScriptContainer declarer = scripts.get(k);
            if (!shouldRun.test(declarer)) {
                continue;
            }
            for (String raw : declarer.properties.getOrDefault(ScriptProperty.AFTER)) {
                Ref ref = resolve(raw, declarer);
                if (ref == null || !ref.known(knownKeys)) {
                    unresolved.add(display(declarer) + " → " + raw.trim());
                    continue;
                }
                // No runnable target in this group (another priority group, a script that does not run, or a
                // self-reference only): allowed, silently ignored.
                if (ref.glob()) {
                    for (Map.Entry<String, Integer> entry : indexByKey.entrySet()) {
                        if (ref.matchesKey(entry.getKey()) && entry.getValue() != k - from) {
                            addEdge(entry.getValue(), k - from, inDegree, successors);
                        }
                    }
                } else {
                    Integer target = indexByKey.get(ref.key());
                    if (target != null && target != k - from) {
                        addEdge(target, k - from, inDegree, successors);
                    }
                }
            }
        }

        if (!unresolved.isEmpty()) {
            problems.add(formatProblems(unresolved.size() + " 个 after 引用无法解析，已忽略", unresolved));
        }

        // Snapshot of the original stable order, used to fall back for the whole group on a cycle
        // (see the sorted.size() < n branch below).
        List<ScriptContainer> originalOrder = new ArrayList<>(scripts.subList(from, to));

        // Kahn: fill the initial ready queue in the group's original order, which keeps the result
        // stable and deterministic.
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int k = 0; k < n; k++) {
            if (inDegree[k] == 0) {
                ready.addLast(k);
            }
        }

        List<ScriptContainer> sorted = new ArrayList<>(n);
        while (!ready.isEmpty()) {
            int node = ready.pollFirst();
            sorted.add(scripts.get(from + node));
            for (int succ : successors.get(node)) {
                if (--inDegree[succ] == 0) {
                    ready.addLast(succ);
                }
            }
        }

        if (sorted.size() < n) {
            // Cyclic dependency: the whole group falls back to the original stable order, as the class
            // documentation promises, instead of appending the cycle members after the already sorted
            // ones. Appending would move unrelated scripts (it pushes independent scripts to the
            // front) and break the after semantics.
            List<String> cycled = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                if (inDegree[k] > 0) {
                    cycled.add(display(scripts.get(from + k)));
                }
            }
            sorted.clear();
            sorted.addAll(originalOrder);
            problems.add(formatProblems(cycled.size() + " 个脚本构成 after 循环依赖，回退到原始顺序", cycled));
        }

        for (int k = 0; k < n; k++) {
            scripts.set(from + k, sorted.get(k));
        }
    }

    private static void addEdge(int target, int dependent, int[] inDegree, List<List<Integer>> successors) {
        successors.get(target).add(dependent);
        inDegree[dependent]++;
    }

    /** Resolves one {@code after} reference; returns {@code null} when it cannot be resolved (absolute
     * path, escaping the root, or an invalid path). */
    private static Ref resolve(String raw, ScriptContainer declarer) {
        String r = raw.trim().replace('\\', '/');
        if (r.isEmpty()) {
            return null;
        }
        boolean glob = false;
        if (r.endsWith("/*")) {
            glob = true;
            r = r.substring(0, r.length() - 2);
        }
        boolean relativeToDeclarer = false;
        while (true) {
            if (r.startsWith("./")) {
                relativeToDeclarer = true;
                r = r.substring(2);
            } else if (r.startsWith("nekojs:")) {
                r = r.substring("nekojs:".length());
            } else if (r.startsWith("nekojs/")) {
                r = r.substring("nekojs/".length());
            } else if (r.startsWith(declarer.type.name + "/")) {
                r = r.substring(declarer.type.name.length() + 1);
            } else if (r.startsWith("/")) {
                r = r.substring(1);
            } else {
                break;
            }
        }
        if (relativeToDeclarer) {
            String key = keyOf(declarer);
            int slash = key.lastIndexOf('/');
            r = slash < 0 ? r : key.substring(0, slash + 1) + r;
        }
        Path normalized;
        try {
            normalized = Path.of(r).normalize();
        } catch (InvalidPathException e) {
            return null;
        }
        if (normalized.isAbsolute()) {
            return null;
        }
        String key = normalized.toString().replace('\\', '/');
        if (key.isEmpty() || key.equals("..") || key.startsWith("../")) {
            return null;
        }
        return new Ref(key, glob);
    }

    /**
     * The script's relative key inside its type scope, with forward slashes, spelled the same way as
     * {@link com.tkisor.nekojs.api.ScriptType#makeId} and {@link ScriptPack#idPathPrefix()}. It takes
     * the {@link ScriptId} computed during discovery as the single source and strips the leading type
     * segment: a flat script becomes {@code foo/bar.js}, a pack script becomes
     * {@code packs/<id>/foo/bar.js}.
     *
     * <p>A pack script does not live under the type root, so relativizing against the type root would
     * produce {@code ../packs/...} keys, which the {@code ..} escape check in {@link #resolve} rejects.
     * That silently drops both {@code after:} references into a pack and the {@code ./sibling.js}
     * references inside a pack.
     */
    private static String keyOf(ScriptContainer script) {
        String path = script.id.path();
        String prefix = script.type.name + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private static String display(ScriptContainer script) {
        return script.type.name + "/" + keyOf(script);
    }

    private static String formatProblems(String prefix, List<String> items) {
        int limit = 5;
        String sample = String.join(", ", items.subList(0, Math.min(limit, items.size())));
        if (items.size() > limit) {
            sample += ", …";
        }
        return prefix + "：" + sample;
    }
}
