//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.DiskPackUiResourceResolver;
import com.tkisor.nekojs.api.ui.InspectorNode;
import com.tkisor.nekojs.api.ui.InspectorSnapshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshots;
import com.tkisor.nekojs.api.ui.ResourceStatus;
import com.tkisor.nekojs.api.ui.SnapshotDiff;
import com.tkisor.nekojs.api.ui.SnapshotDiffer;
import com.tkisor.nekojs.api.ui.UiInspector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 45 NeoForge host-side inspector smoke scenarios, executable without a live
 * client: the adapter's collect path ({@code focusedIds} plus the shared
 * {@link InspectorSnapshots} read/decorate) runs against host-object snapshot data and a
 * real {@link DiskPackUiResourceResolver}, and the difference report ranks a controlled
 * drift. Real-client frame capture on NeoForge 26.2 is NOT RUN in this environment (no
 * Minecraft client); this suite pins the contract the client run would exercise.
 */
class Ticket45InspectorSmokeTest {

    @Test
    void adapterPublishesThePublicInspectorContract() {
        assertTrue(UiInspector.class.isAssignableFrom(JsxHostAdapter.class),
                "the NeoForge host is a UiInspector");
    }

    @Test
    void focusedIdsCollectIdBearingFocusedNodes() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction transaction = tree.begin();
        Object screen = transaction.create("screen", null, new LinkedHashMap<>(Map.of("id", "root")));
        Object named = transaction.create("input", null, new LinkedHashMap<>(Map.of("id", "name")));
        Object anonymous = transaction.create("button", "anon", new LinkedHashMap<>());
        transaction.order(screen, List.of(named, anonymous));
        transaction.order(null, List.of(screen));
        transaction.commit(List.of(screen));
        JsxHostTree.Node focused = tree.roots().getFirst().children.get(1);
        focused.focused = true;

        assertEquals(java.util.Set.of(), JsxHostAdapter.focusedIds(tree.roots()),
                "a focused node without an id is not locatable by the inspector");
        tree.roots().getFirst().children.getFirst().focused = true;
        assertEquals(java.util.Set.of("name"), JsxHostAdapter.focusedIds(tree.roots()),
                "the focused node with an id is collected");
    }

    @Test
    void hostObjectCollectPathDecoratesAndDiffs(@TempDir Path packRoot) throws IOException {
        Path texture = packRoot.resolve("assets/mymod/textures/gui/hero.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] { 1 });
        DiskPackUiResourceResolver resources = new DiskPackUiResourceResolver(packRoot);

        InspectorSnapshot base = InspectorSnapshots.read("smoke-root", "neoforge-host", snapshotMap(240, 120));
        InspectorSnapshot decorated = InspectorSnapshots.decorate(base, java.util.Set.of("act"),
                resources::resolveTexture, List.of(new InspectorSnapshot.PhaseError(
                        "layout", "smoke-root", "width min must not exceed max")), null);

        InspectorNode act = find(decorated.nodes(), "act");
        assertNotNull(act);
        assertEquals(true, act.focused(), "focus decoration marks the focused id");
        assertEquals(ResourceStatus.State.RESOLVED, act.resources().get(0).state(),
                "the backed image id resolves");
        assertEquals("mymod:gui/hero", act.resources().get(0).id());
        assertEquals(ResourceStatus.State.MISSING, act.resources().get(1).state(),
                "the unbacked icon id reports missing");
        assertEquals("layout", decorated.errors().getFirst().phase());

        InspectorSnapshot drifted = InspectorSnapshots.decorate(
                InspectorSnapshots.read("smoke-root", "neoforge-host", snapshotMap(360, 120)),
                java.util.Set.of(), resources::resolveTexture, List.of(), null);
        SnapshotDiff diff = SnapshotDiffer.diff(decorated, drifted, "references/smoke.png");
        assertEquals(false, diff.matches());
        SnapshotDiff.Entry largest = diff.largest().orElseThrow();
        assertEquals("rect.width", largest.field(), "the button widening is the largest drift");
        assertEquals("240", largest.expected());
        assertEquals("360", largest.actual());
        assertEquals(120, largest.deviation(), 1e-9);
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.field().equals("focused")),
                "focus loss is still reported");
        assertEquals("references/smoke.png", diff.referenceImage());
        assertEquals("neoforge-host", diff.actualSource());
    }

    private static InspectorNode find(List<InspectorNode> nodes, String id) {
        for (InspectorNode node : nodes) {
            if (id.equals(node.id())) return node;
            InspectorNode nested = find(node.children(), id);
            if (nested != null) return nested;
        }
        return null;
    }

    /** The runtime layout snapshot shape as plain host data, one screen with a button. */
    private static Map<String, Object> snapshotMap(int buttonWidth, int viewportWidth) {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("type", "button");
        button.put("key", null);
        button.put("id", "act");
        button.put("visible", true);
        button.put("rect", rect(0, 0, buttonWidth, 20));
        // Parental clip stays constant, so the button widening is the unique largest drift.
        button.put("clip", rect(0, 0, 120, 20));
        button.put("overflow", Map.of("left", 0, "top", 0, "right", 0, "bottom", 0));
        button.put("style", new LinkedHashMap<>(Map.of(
                "resource", "mymod:gui/hero", "icon", "mymod:gui/absent", "text", "Act")));
        button.put("bindings", List.of("click"));
        button.put("children", List.of());

        Map<String, Object> screen = new LinkedHashMap<>(button);
        screen.put("type", "screen");
        screen.put("id", "root");
        screen.put("rect", rect(0, 0, viewportWidth, 120));
        screen.put("clip", rect(0, 0, viewportWidth, 120));
        screen.put("style", new LinkedHashMap<>());
        screen.put("bindings", List.of());
        screen.put("children", List.of(button));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profile", 2);
        snapshot.put("viewport", viewport(viewportWidth, 120));
        snapshot.put("nodes", List.of(screen));
        snapshot.put("diagnostics", List.of());
        return snapshot;
    }

    private static Map<String, Object> rect(double x, double y, double width, double height) {
        return new LinkedHashMap<>(Map.of("x", x, "y", y, "width", width, "height", height));
    }

    private static Map<String, Object> viewport(int width, int height) {
        Map<String, Object> viewport = new LinkedHashMap<>();
        viewport.put("width", width);
        viewport.put("height", height);
        viewport.put("safeArea", Map.of("top", 0, "right", 0, "bottom", 0, "left", 0));
        viewport.put("contentWidth", width);
        viewport.put("contentHeight", height);
        viewport.put("profile", 2);
        viewport.put("guiScale", 2);
        viewport.put("designScale", 1.0);
        return viewport;
    }
}
//?}
