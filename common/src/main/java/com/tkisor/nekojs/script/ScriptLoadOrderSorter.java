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
 * 求解脚本的加载顺序：以 {@link ScriptProperty#PRIORITY} 降序稳定排序为主序，
 * 在同 priority 内对 {@link ScriptProperty#AFTER} 声明的依赖做稳定的拓扑排序（Kahn 算法）。
 *
 * <p>{@code after:} 引用解析规则（宽松处理）：
 * <ul>
 * <li>{@code aaa/bbb.js} —— 相对脚本类型根目录；</li>
 * <li>{@code ./ccc.js} —— 相对声明脚本所在目录；</li>
 * <li>{@code aaa/*} —— 脚本类型根目录 {@code aaa/} 下的所有文件；</li>
 * <li>允许带 {@code nekojs/}、{@code nekojs:<类型>/} 或 {@code <类型>/} 前缀，反斜杠视为 {@code /}；</li>
 * <li>未知引用被忽略并记入诊断；循环依赖回退到原始稳定顺序。</li>
 * </ul>
 *
 * <p>依赖边只在「同 priority 组内、双方都会执行（{@code shouldRun}）」的脚本之间生效；
 * 指向其他 priority 组或不会执行脚本的引用是合法的，静默忽略。
 *
 * <p>确定性：无法由 {@code after} 决定的脚本保持 priority 稳定排序后的原始相对顺序
 * （即发现顺序，等价于路径字典序）；因此结果与输入批次顺序一致地可复现。
 */
final class ScriptLoadOrderSorter {

    private ScriptLoadOrderSorter() {
    }

    /** 一次排序的诊断结果：是否出现问题，以及可拼接成日志的描述片段。 */
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

    /** 单个解析后的 {@code after} 引用：{@code key} 是脚本根目录下的相对路径，{@code glob} 表示目录通配。 */
    private record Ref(String key, boolean glob) {

        /** 该引用是否命中批次中的任何脚本 key（无论其 priority / 是否运行）。 */
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
     * 原地重排脚本列表：先按 priority 降序稳定排序，再在每个相同 priority 的组内应用
     * {@code after:} 依赖的稳定拓扑排序。
     *
     * @param scripts   待排序的脚本批次（同一次 {@code loadScriptsInto} 的入参），会被原地重排
     * @param shouldRun 判断脚本是否会真正执行的谓词（{@link ScriptContainer#shouldRun()}）；
     *                  只有双方都会执行的依赖边才生效
     * @return 诊断结果；无问题时 {@link Result#hasProblems()} 为 {@code false}
     */
    static Result applyAfterOrder(List<ScriptContainer> scripts, Predicate<ScriptContainer> shouldRun) {
        List<String> problems = new ArrayList<>();

        // 1. priority 降序稳定排序（主序，行为与旧实现完全一致）
        scripts.sort((s1, s2) -> {
            int p1 = s1.properties.getOrDefault(ScriptProperty.PRIORITY);
            int p2 = s2.properties.getOrDefault(ScriptProperty.PRIORITY);
            return Integer.compare(p2, p1);
        });

        // 2. 全批次已知 key 集合：用于区分「未知引用」（告警）与「已知但不同 priority / 不运行」（静默）
        Map<String, Boolean> knownKeys = new HashMap<>();
        for (ScriptContainer script : scripts) {
            knownKeys.putIfAbsent(keyOf(script), shouldRun.test(script));
        }

        // 3. 对每个 priority 组做稳定拓扑排序
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

        return new Result(problems);
    }

    /** 对 {@code [from, to)} 区间（同一 priority）做稳定拓扑排序并写回。 */
    private static void topoSortGroup(List<ScriptContainer> scripts, int from, int to,
                                      Predicate<ScriptContainer> shouldRun,
                                      Map<String, Boolean> knownKeys, List<String> problems) {
        int n = to - from;

        // key → 组内下标（仅会执行的脚本能作为依赖目标）
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
                // 未命中同组可执行目标（在别的 priority 组 / 不会执行 / 仅自引用）：合法，静默忽略
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

        // Kahn：初始就绪队列按组内原始顺序入队，保证结果稳定确定
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
            // 循环依赖（或依赖环的脚本）：回退到原始稳定顺序
            List<String> cycled = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                if (inDegree[k] > 0) {
                    sorted.add(scripts.get(from + k));
                    cycled.add(display(scripts.get(from + k)));
                }
            }
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

    /** 解析单个 {@code after} 引用；无法解析（绝对路径 / 越出根目录 / 非法路径）时返回 {@code null}。 */
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

    /** 脚本在类型根目录下的相对路径 key（正斜杠分隔），与 {@link com.tkisor.nekojs.api.ScriptType#makeId} 一致。 */
    private static String keyOf(ScriptContainer script) {
        Path root = script.type.path;
        Path relative;
        if (root == null) {
            relative = script.path;
        } else {
            try {
                relative = root.relativize(script.path);
            } catch (IllegalArgumentException e) {
                relative = script.path;
            }
        }
        return relative.toString().replace('\\', '/');
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
