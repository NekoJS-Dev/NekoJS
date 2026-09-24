package com.tkisor.nekojs.api.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One measured UI node in an {@link InspectorSnapshot}. The {@code style} map is the
 * final resolved props — responsive values already flattened for the current profile —
 * which in this runtime is also the resolved style. {@code bindings} names the event
 * types bound on the node id (callbacks are never exposed); {@code resources} carries
 * the controlled-resource statuses for the node's image/icon ids.
 *
 * @param id           script-assigned node id, null when the node has none
 * @param type         primitive type, e.g. {@code screen} or {@code #text}
 * @param key          reconciler key, null when absent
 * @param visible      whether the node participates in layout this frame
 * @param focused      whether the node currently holds host focus
 * @param rect         laid-out rectangle in logical viewport coordinates
 * @param clip         intersection of the rect with the ancestral clip
 * @param overflow     how far the rect escapes the parental clip, per edge
 * @param scrollOffset resolved scroll offset in pixels, null when not scrolling
 * @param style        final resolved props (see class doc), insertion-ordered
 * @param bindings     sorted event names bound on this node, e.g. {@code click}
 * @param resources    statuses for the node's controlled resource ids
 * @param children     measured children in layout order
 */
public record InspectorNode(
        String id,
        String type,
        String key,
        boolean visible,
        boolean focused,
        Rect rect,
        Rect clip,
        Overflow overflow,
        Double scrollOffset,
        Map<String, Object> style,
        List<String> bindings,
        List<ResourceStatus> resources,
        List<InspectorNode> children) {
    public InspectorNode {
        style = Collections.unmodifiableMap(new LinkedHashMap<>(style));
        bindings = List.copyOf(bindings);
        resources = List.copyOf(resources);
        children = List.copyOf(children);
    }

    /** Axis-aligned rectangle in logical viewport coordinates. */
    public record Rect(double x, double y, double width, double height) {
    }

    /** Escape of a rect beyond its parental clip, per edge, non-negative. */
    public record Overflow(double left, double top, double right, double bottom) {
    }
}
