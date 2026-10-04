//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.InspectorNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Ticket43HostLayoutProjectionTest {
    @Test
    void committedGeometryAndResolvedPropsUseTheCommonMeasurement() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction transaction = tree.begin();
        Object button = transaction.create("button", "hit", Map.of("id", "hit", "width", Map.of("base", 80)));
        transaction.order(null, List.of(button));
        InspectorNode measured = measured("button", 160, 10, 40, 20, Map.of("id", "hit", "width", 40), List.of());
        transaction.commit(List.of(button), nodes -> JsxHostLayout.project(nodes, List.of(measured)));
        JsxHostTree.Node actual = tree.roots().getFirst();
        assertEquals(160, actual.x);
        assertEquals(40, actual.width);
        assertEquals(40, actual.props.get("width"));
        assertEquals(160, actual.clipX);
        assertEquals(40, actual.clipWidth);
    }

    @Test
    void aFailedProjectionDoesNotPublishCandidateGeometry() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object button = initial.create("button", "hit", Map.of("id", "hit"));
        initial.order(null, List.of(button));
        initial.commit(List.of(button), nodes -> JsxHostLayout.project(nodes,
                List.of(measured("button", 160, 0, 40, 20, Map.of("id", "hit"), List.of()))));
        JsxHostTree.Transaction failed = tree.begin();
        failed.update(button, "button", "hit", Map.of("id", "hit", "width", 80));
        assertThrows(IllegalStateException.class, () -> failed.commit(List.of(button),
                nodes -> JsxHostLayout.project(nodes, List.of(measured("image", 120, 0, 80, 20, Map.of(), List.of())))));
        failed.rollback();
        assertEquals(160, tree.roots().getFirst().x);
        assertEquals(40, tree.roots().getFirst().width);
    }

    @Test
    void reprojectingForResizePreservesFocusCursorAndCaptureIdentity() {
        JsxHostTree tree = new JsxHostTree();
        JsxHostTree.Transaction initial = tree.begin();
        Object input = initial.create("input", "field", Map.of("value", "Neko"));
        initial.order(null, List.of(input));
        initial.commit(List.of(input));
        tree.roots().getFirst().focused = true;
        tree.roots().getFirst().collapseSelection(2);
        tree.capture(tree.roots().getFirst(), 0);
        JsxHostTree.Transaction resize = tree.begin();
        resize.commit(tree.roots(), nodes -> JsxHostLayout.project(nodes,
                List.of(measured("input", 24, 30, 160, 20, Map.of("value", "Neko"), List.of()))));
        assertEquals(2, tree.roots().getFirst().cursor);
        assertEquals(true, tree.roots().getFirst().focused);
        assertEquals(tree.roots().getFirst(), tree.releaseCapture(0));
        assertFalse(tree.roots().getFirst().pressed);
    }

    @Test
    void uncontrolledScrollProjectsRetainedOffsetIntoGeometryAndInspector() {
        JsxHostTree.Node scroll = new JsxHostTree.Node(1, "scroll", null, new LinkedHashMap<>(Map.of("scrollOffset", 24.0)));
        JsxHostTree.Node child = new JsxHostTree.Node(2, "label", null, new LinkedHashMap<>());
        scroll.children.add(child);
        InspectorNode childLayout = measured("label", 0, 0, 100, 100, Map.of(), List.of());
        InspectorNode scrollLayout = measured("scroll", 0, 0, 100, 50, Map.of(), List.of(childLayout));
        List<InspectorNode> projected = JsxHostLayout.project(List.of(scroll), List.of(scrollLayout));
        assertEquals(-24, child.y);
        assertEquals(24.0, projected.getFirst().scrollOffset());
        assertEquals(-24.0, projected.getFirst().children().getFirst().rect().y());
        assertEquals(50, child.clipHeight);
    }

    @Test
    void horizontalDesignScrollingUsesTheLayoutAxisAndScaledLogicalOffset() {
        JsxHostTree.Node scroll = new JsxHostTree.Node(1, "scroll", null,
                new LinkedHashMap<>(Map.of("scrollOffset", 20.0)));
        JsxHostTree.Node child = new JsxHostTree.Node(2, "panel", null, new LinkedHashMap<>());
        scroll.children.add(child);
        InspectorNode childLayout = measured("panel", 0, 0, 200, 20, Map.of(), List.of());
        InspectorNode scrollLayout = measured("scroll", 0, 0, 100, 20,
                Map.of("direction", "row", "coordinateSpace", "design"), List.of(childLayout));
        List<InspectorNode> result = JsxHostLayout.project(List.of(scroll), List.of(scrollLayout), 2);
        assertEquals(-40, child.x);
        assertEquals(0, child.y);
        assertEquals(50.0, scroll.scrollRange);
        assertEquals(20.0, result.getFirst().scrollOffset());
        assertEquals(40.0, result.getFirst().children().getFirst().overflow().left());
    }

    private static InspectorNode measured(String type, int x, int y, int width, int height,
            Map<String, Object> style, List<InspectorNode> children) {
        InspectorNode.Rect rect = new InspectorNode.Rect(x, y, width, height);
        return new InspectorNode((String) style.get("id"), type, null, true, false, rect, rect,
                new InspectorNode.Overflow(0, 0, 0, 0), null, style, List.of(), List.of(), children);
    }
}
//?}
