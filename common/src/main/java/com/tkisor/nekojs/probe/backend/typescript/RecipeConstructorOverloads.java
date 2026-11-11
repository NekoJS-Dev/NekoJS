package com.tkisor.nekojs.probe.backend.typescript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps constructor call domains intact while folding equivalent contiguous prefix signatures. */
final class RecipeConstructorOverloads {
    private RecipeConstructorOverloads() {}

    record Overload(List<String> fields, int requiredCount) {
        Overload { fields = List.copyOf(fields); }
    }

    static List<Overload> plan(List<List<String>> constructors, boolean hasConstructorOverrides) {
        List<List<String>> variants = constructors.stream().map(List::copyOf).toList();
        if (hasConstructorOverrides) {
            return variants.stream().map(fields -> new Overload(fields, fields.size())).toList();
        }
        Map<Integer, List<Integer>> byArity = new LinkedHashMap<>();
        for (int i = 0; i < variants.size(); i++) {
            byArity.computeIfAbsent(variants.get(i).size(), ignored -> new ArrayList<>()).add(i);
        }
        boolean[] consumed = new boolean[variants.size()];
        List<Overload> result = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) {
            if (consumed[i]) continue;
            List<String> first = variants.get(i);
            if (byArity.get(first.size()).size() != 1) {
                consumed[i] = true;
                result.add(new Overload(first, first.size()));
                continue;
            }
            // Declaration order is not guaranteed to follow arity; find the chain's actual root.
            int root = i;
            while (true) {
                int previous = uniqueIndex(byArity, variants.get(root).size() - 1);
                if (previous < 0 || !isPrefix(variants.get(previous), variants.get(root))) break;
                root = previous;
            }
            int required = variants.get(root).size();
            int tail = root;
            while (true) {
                consumed[tail] = true;
                int next = uniqueIndex(byArity, variants.get(tail).size() + 1);
                if (next < 0 || !isPrefix(variants.get(tail), variants.get(next))) break;
                tail = next;
            }
            result.add(new Overload(variants.get(tail), required));
        }
        return List.copyOf(result);
    }

    private static int uniqueIndex(Map<Integer, List<Integer>> byArity, int arity) {
        List<Integer> candidates = byArity.get(arity);
        return candidates != null && candidates.size() == 1 ? candidates.getFirst() : -1;
    }

    private static boolean isPrefix(List<String> shorter, List<String> longer) {
        return shorter.size() < longer.size() && longer.subList(0, shorter.size()).equals(shorter);
    }
}
