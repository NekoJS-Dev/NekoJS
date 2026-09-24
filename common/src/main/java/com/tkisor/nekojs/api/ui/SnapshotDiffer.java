package com.tkisor.nekojs.api.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure comparator behind the ticket 45 difference report: a reference
 * {@link InspectorSnapshot} versus an actual one, no host state involved. Nodes are
 * matched by structural path (root index, then child index plus type/key/id), so the
 * report stays meaningful across reloads where host identities change. The screenshot
 * and reference image never produce entries: pixel evidence is auxiliary by contract;
 * only public measurements and behavior facts are compared.
 */
public final class SnapshotDiffer {
    private SnapshotDiffer() {
    }

    /**
     * Compares a reference snapshot against an actual one.
     *
     * @param reference      the expected measurement
     * @param actual         the measured output
     * @param referenceImage reference image identifier kept as provenance, null when none
     * @return ranked difference report, {@code matches} when nothing differed
     */
    public static SnapshotDiff diff(InspectorSnapshot reference, InspectorSnapshot actual, String referenceImage) {
        List<SnapshotDiff.Entry> entries = new ArrayList<>();
        compareFrame(reference, actual, entries);
        Map<String, InspectorNode> referenceNodes = new LinkedHashMap<>();
        Map<String, InspectorNode> actualNodes = new LinkedHashMap<>();
        index(reference.nodes(), "", referenceNodes);
        index(actual.nodes(), "", actualNodes);
        for (Map.Entry<String, InspectorNode> matched : referenceNodes.entrySet()) {
            InspectorNode other = actualNodes.get(matched.getKey());
            if (other == null) structural(entries, matched.getKey(), "present", "missing");
            else compareNode(matched.getKey(), matched.getValue(), other, entries);
        }
        for (String path : actualNodes.keySet()) {
            if (!referenceNodes.containsKey(path)) structural(entries, path, "missing", "present");
        }
        entries.sort(Comparator
                .comparingDouble(SnapshotDiff.Entry::deviation).reversed()
                .thenComparing(SnapshotDiff.Entry::nodePath)
                .thenComparing(SnapshotDiff.Entry::field));
        return new SnapshotDiff(entries.isEmpty(), entries, reference.source(), actual.source(),
                reference.profile(), actual.profile(), reference.viewport(), actual.viewport(),
                referenceImage, actual.screenshot());
    }

    private static void compareFrame(InspectorSnapshot reference, InspectorSnapshot actual,
            List<SnapshotDiff.Entry> entries) {
        InspectorViewport expected = reference.viewport();
        InspectorViewport measured = actual.viewport();
        discrete(entries, "", "profile", expected.profile(), measured.profile());
        number(entries, "", "viewport.width", expected.width(), measured.width());
        number(entries, "", "viewport.height", expected.height(), measured.height());
        number(entries, "", "safeArea.top", expected.safeArea().top(), measured.safeArea().top());
        number(entries, "", "safeArea.right", expected.safeArea().right(), measured.safeArea().right());
        number(entries, "", "safeArea.bottom", expected.safeArea().bottom(), measured.safeArea().bottom());
        number(entries, "", "safeArea.left", expected.safeArea().left(), measured.safeArea().left());
        if (!reference.diagnostics().equals(actual.diagnostics())) {
            entries.add(new SnapshotDiff.Entry("", "diagnostics",
                    String.join(";", reference.diagnostics()), String.join(";", actual.diagnostics()), 1));
        }
    }

    private static void index(List<InspectorNode> nodes, String parent, Map<String, InspectorNode> output) {
        for (int i = 0; i < nodes.size(); i++) {
            InspectorNode node = nodes.get(i);
            String path = (parent.isEmpty() ? "" : parent + "/") + i + "/" + describe(node);
            output.put(path, node);
            index(node.children(), path, output);
        }
    }

    private static String describe(InspectorNode node) {
        StringBuilder name = new StringBuilder(node.type());
        if (node.key() != null) name.append('#').append(node.key());
        if (node.id() != null) name.append(':').append(node.id());
        return name.toString();
    }

    private static void compareNode(String path, InspectorNode reference, InspectorNode actual,
            List<SnapshotDiff.Entry> entries) {
        discrete(entries, path, "visible", reference.visible(), actual.visible());
        discrete(entries, path, "focused", reference.focused(), actual.focused());
        number(entries, path, "rect.x", reference.rect().x(), actual.rect().x());
        number(entries, path, "rect.y", reference.rect().y(), actual.rect().y());
        number(entries, path, "rect.width", reference.rect().width(), actual.rect().width());
        number(entries, path, "rect.height", reference.rect().height(), actual.rect().height());
        number(entries, path, "clip.x", reference.clip().x(), actual.clip().x());
        number(entries, path, "clip.y", reference.clip().y(), actual.clip().y());
        number(entries, path, "clip.width", reference.clip().width(), actual.clip().width());
        number(entries, path, "clip.height", reference.clip().height(), actual.clip().height());
        number(entries, path, "overflow.left", reference.overflow().left(), actual.overflow().left());
        number(entries, path, "overflow.top", reference.overflow().top(), actual.overflow().top());
        number(entries, path, "overflow.right", reference.overflow().right(), actual.overflow().right());
        number(entries, path, "overflow.bottom", reference.overflow().bottom(), actual.overflow().bottom());
        optionalNumber(entries, path, "scrollOffset", reference.scrollOffset(), actual.scrollOffset());
        compareStyle(path, reference.style(), actual.style(), entries);
        if (!reference.bindings().equals(actual.bindings())) {
            entries.add(new SnapshotDiff.Entry(path, "bindings",
                    String.join(",", reference.bindings()), String.join(",", actual.bindings()), 1));
        }
        compareResources(path, reference.resources(), actual.resources(), entries);
    }

    private static void compareStyle(String path, Map<String, Object> reference, Map<String, Object> actual,
            List<SnapshotDiff.Entry> entries) {
        Set<String> keys = new LinkedHashSet<>(reference.keySet());
        keys.addAll(actual.keySet());
        for (String key : keys) {
            Object expected = reference.get(key);
            Object measured = actual.get(key);
            if (expected instanceof Number expectedNumber && measured instanceof Number measuredNumber) {
                double deviation = Math.abs(expectedNumber.doubleValue() - measuredNumber.doubleValue());
                if (deviation > 0) {
                    entries.add(new SnapshotDiff.Entry(path, "style." + key,
                            InspectorSnapshots.valueText(expected), InspectorSnapshots.valueText(measured), deviation));
                }
            } else if (!Objects.equals(expected, measured)) {
                discrete(entries, path, "style." + key,
                        InspectorSnapshots.valueText(expected), InspectorSnapshots.valueText(measured));
            }
        }
    }

    private static void compareResources(String path, List<ResourceStatus> reference, List<ResourceStatus> actual,
            List<SnapshotDiff.Entry> entries) {
        Map<String, String> expected = resourceStates(reference);
        Map<String, String> measured = resourceStates(actual);
        Set<String> ids = new LinkedHashSet<>(expected.keySet());
        ids.addAll(measured.keySet());
        for (String id : ids) {
            String referenceState = expected.getOrDefault(id, "absent");
            String actualState = measured.getOrDefault(id, "absent");
            if (!referenceState.equals(actualState)) {
                discrete(entries, path, "resource:" + id, referenceState, actualState);
            }
        }
    }

    private static Map<String, String> resourceStates(List<ResourceStatus> statuses) {
        Map<String, String> states = new LinkedHashMap<>();
        for (ResourceStatus status : statuses) states.put(status.id(), status.state().name());
        return states;
    }

    private static void number(List<SnapshotDiff.Entry> entries, String path, String field,
            double expected, double actual) {
        if (expected != actual) {
            entries.add(new SnapshotDiff.Entry(path, field,
                    InspectorSnapshots.num(expected), InspectorSnapshots.num(actual), Math.abs(expected - actual)));
        }
    }

    private static void optionalNumber(List<SnapshotDiff.Entry> entries, String path, String field,
            Double expected, Double actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null) {
            discrete(entries, path, field,
                    expected == null ? "absent" : InspectorSnapshots.num(expected),
                    actual == null ? "absent" : InspectorSnapshots.num(actual));
        } else {
            number(entries, path, field, expected, actual);
        }
    }

    private static void discrete(List<SnapshotDiff.Entry> entries, String path, String field,
            Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            entries.add(new SnapshotDiff.Entry(path, field,
                    InspectorSnapshots.valueText(expected), InspectorSnapshots.valueText(actual), 1));
        }
    }

    private static void structural(List<SnapshotDiff.Entry> entries, String path, String expected, String actual) {
        entries.add(new SnapshotDiff.Entry(path, "node", expected, actual, Double.MAX_VALUE));
    }
}
