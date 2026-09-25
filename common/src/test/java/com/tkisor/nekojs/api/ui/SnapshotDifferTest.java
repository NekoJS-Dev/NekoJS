package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 45 difference-report tests: controlled differences between a reference and an
 * actual snapshot must produce precisely the expected ranked entries — node rects,
 * resolved style, overflow, resource states, bindings, and structural breaks — with all
 * provenance fields retained.
 */
class SnapshotDifferTest {

    @Test
    void identicalSnapshotsMatchWithNoEntries() {
        InspectorSnapshot reference = snapshot(3, node("card", "panel", 0, 0, 100, 40));
        SnapshotDiff diff = SnapshotDiffer.diff(reference, snapshot(3, node("card", "panel", 0, 0, 100, 40)), null);
        assertTrue(diff.matches(), "no difference means no entries");
        assertTrue(diff.entries().isEmpty());
        assertTrue(diff.largest().isEmpty());
    }

    @Test
    void largestDeviationIsRankedFirstAndNamesNodeAndField() {
        InspectorSnapshot reference = snapshot(3,
                node("card", "panel", 0, 0, 100, 40),
                node("title", "label", 4, 4, 66, 9));
        InspectorSnapshot actual = snapshot(3,
                node("card", "panel", 0, 0, 140, 40),
                node("title", "label", 9, 4, 66, 9));
        SnapshotDiff diff = SnapshotDiffer.diff(reference, actual, null);
        assertEquals(2, diff.entries().size(), "exactly the two changed fields differ");
        SnapshotDiff.Entry largest = diff.largest().orElseThrow();
        assertEquals("0/panel:card", largest.nodePath(), "the widest drift is the panel rect");
        assertEquals("rect.width", largest.field());
        assertEquals("100", largest.expected());
        assertEquals("140", largest.actual());
        assertEquals(40, largest.deviation(), 1e-9);
        assertEquals("rect.x", diff.entries().get(1).field());
        assertEquals(5, diff.entries().get(1).deviation(), 1e-9);
    }

    @Test
    void structuralBreakOutranksAnyMeasurementDrift() {
        InspectorSnapshot reference = snapshot(3,
                node("card", "panel", 0, 0, 100, 40),
                node("title", "label", 0, 44, 66, 9));
        InspectorSnapshot actual = snapshot(3, node("card", "panel", 0, 0, 900, 40));
        SnapshotDiff diff = SnapshotDiffer.diff(reference, actual, null);
        SnapshotDiff.Entry largest = diff.largest().orElseThrow();
        assertEquals("node", largest.field(), "a removed node is the largest deviation");
        assertEquals("present", largest.expected());
        assertEquals("missing", largest.actual());
        assertTrue(largest.deviation() == Double.MAX_VALUE);
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.field().equals("rect.width")
                        && entry.deviation() == 800),
                "the rect drift is still reported, ranked below the structural break");
    }

    @Test
    void controlledFieldChangesProducePreciseEntries() {
        InspectorNode referencePanel = new InspectorNode("card", "panel", "k", true, false,
                rect(0, 0, 100, 40), rect(0, 0, 100, 40), overflow(0, 0, 0, 0), 0.0,
                Map.of("padding", 4, "background", "#101418"),
                List.of("click"), List.of(ResourceStatus.resolved("mymod:gui/hero", "pack/hero.png")), List.of());
        InspectorNode actualPanel = new InspectorNode("card", "panel", "k", true, false,
                rect(0, 0, 100, 52), rect(0, 0, 100, 52), overflow(0, 0, 0, 12), 0.0,
                Map.of("padding", 9, "background", "#101418"),
                List.of("click", "key"), List.of(ResourceStatus.missing(id("mymod:gui/hero"))), List.of());
        SnapshotDiff diff = SnapshotDiffer.diff(snapshot(3, referencePanel),
                snapshot("neoforge-host", 4, actualPanel), "references/main-card.png");

        assertEquals(false, diff.matches());
        SnapshotDiff.Entry overflow = entry(diff, "overflow.bottom");
        assertEquals("0", overflow.expected());
        assertEquals("12", overflow.actual());
        assertEquals(12, overflow.deviation(), 1e-9);
        SnapshotDiff.Entry padding = entry(diff, "style.padding");
        assertEquals("4", padding.expected());
        assertEquals("9", padding.actual());
        assertEquals(5, padding.deviation(), 1e-9);
        SnapshotDiff.Entry binding = entry(diff, "bindings");
        assertEquals("click", binding.expected());
        assertEquals("click,key", binding.actual());
        SnapshotDiff.Entry resource = entry(diff, "resource:mymod:gui/hero");
        assertEquals("RESOLVED", resource.expected());
        assertEquals("MISSING", resource.actual());
        SnapshotDiff.Entry profile = entry(diff, "profile");
        assertEquals("3", profile.expected());
        assertEquals("4", profile.actual());
        assertEquals(12, entry(diff, "rect.height").deviation(), 1e-9);
        assertTrue(entry(diff, "style.background") == null, "unchanged style fields stay silent");

        assertEquals("references/main-card.png", diff.referenceImage());
        assertEquals(3, diff.referenceProfile());
        assertEquals(4, diff.actualProfile());
        assertEquals("golden", diff.referenceSource());
        assertEquals("neoforge-host", diff.actualSource());
        assertEquals(480, diff.referenceViewport().width());
        assertEquals(480, diff.actualViewport().width());
    }

    @Test
    void guiScaleAndDesignScaleDriftAreFrameEntries() {
        InspectorSnapshot reference = new InspectorSnapshot("root", "golden",
                new InspectorViewport(480, 240, new InspectorViewport.SafeArea(0, 0, 0, 0),
                        480, 240, 3, 2, 1.0),
                List.of(node("card", "panel", 0, 0, 100, 40)), List.of(), List.of(), null);
        InspectorSnapshot actual = new InspectorSnapshot("root", "neoforge-host",
                new InspectorViewport(480, 240, new InspectorViewport.SafeArea(0, 0, 0, 0),
                        480, 240, 3, 4, 2.0),
                List.of(node("card", "panel", 0, 0, 100, 40)), List.of(), List.of(), null);
        SnapshotDiff diff = SnapshotDiffer.diff(reference, actual, null);
        SnapshotDiff.Entry gui = entry(diff, "guiScale");
        assertEquals("2", gui.expected(), "the reference gui scale is an environment input");
        assertEquals("4", gui.actual());
        assertEquals(2, gui.deviation(), 1e-9);
        SnapshotDiff.Entry design = entry(diff, "designScale");
        assertEquals("1", design.expected());
        assertEquals("2", design.actual());
    }

    @Test
    void screenshotAndReferenceImageStayProvenanceNotEntries() {
        InspectorSnapshot reference = snapshot(3, node("card", "panel", 0, 0, 100, 40));
        InspectorSnapshot actual = new InspectorSnapshot("root", "neoforge-host", viewport(3, 640, 360),
                List.of(node("card", "panel", 0, 0, 100, 40)), List.of(), List.of(),
                new InspectorScreenshot("neoforge-viewport-meta", 640, 360));
        SnapshotDiff diff = SnapshotDiffer.diff(reference, actual, "references/card.png");
        assertTrue(diff.entries().stream().noneMatch(entry -> entry.field().startsWith("screenshot")),
                "pixel evidence never produces entries");
        assertEquals("references/card.png", diff.referenceImage());
        assertEquals("neoforge-viewport-meta", diff.actualScreenshot().source());
    }

    private static SnapshotDiff.Entry entry(SnapshotDiff diff, String field) {
        return diff.entries().stream().filter(candidate -> candidate.field().equals(field)).findFirst().orElse(null);
    }

    private static UiResourceId id(String raw) {
        return UiResourceId.parse(raw).orElseThrow();
    }

    private static InspectorNode.Rect rect(double x, double y, double width, double height) {
        return new InspectorNode.Rect(x, y, width, height);
    }

    private static InspectorNode.Overflow overflow(double left, double top, double right, double bottom) {
        return new InspectorNode.Overflow(left, top, right, bottom);
    }

    private static InspectorViewport viewport(int profile, int width, int height) {
        return new InspectorViewport(width, height, new InspectorViewport.SafeArea(0, 0, 0, 0),
                width, height, profile, 2, 1.0);
    }

    private static InspectorSnapshot snapshot(int profile, InspectorNode... nodes) {
        return snapshot("golden", profile, nodes);
    }

    private static InspectorSnapshot snapshot(String source, int profile, InspectorNode... nodes) {
        return new InspectorSnapshot("root", source, viewport(profile, 480, 240),
                List.of(nodes), List.of(), List.of(), null);
    }

    private static InspectorNode node(String id, String type, double x, double y, double width, double height) {
        // Fixed parental clip larger than every rect used here, so only the fields a test
        // changes produce entries.
        return new InspectorNode(id, type, null, true, false,
                rect(x, y, width, height), rect(0, 0, 200, 200), overflow(0, 0, 0, 0), null,
                Map.of(), List.of(), List.of(), List.of());
    }
}
