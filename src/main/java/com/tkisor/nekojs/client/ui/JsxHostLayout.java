//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ui.InspectorNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class JsxHostLayout {
    private JsxHostLayout() {
    }

    static List<InspectorNode> project(List<JsxHostTree.Node> nodes, List<InspectorNode> measured) {
        return project(nodes, measured, 1);
    }

    static List<InspectorNode> project(List<JsxHostTree.Node> nodes, List<InspectorNode> measured, double designScale) {
        return project(nodes, measured, 0, 0, null, designScale);
    }

    private static List<InspectorNode> project(List<JsxHostTree.Node> nodes, List<InspectorNode> measured,
            double horizontalShift, double verticalShift, InspectorNode.Rect parentClip, double designScale) {
        if (nodes.size() != measured.size()) {
            throw new IllegalStateException("[NEKO-8001] JSX host layout rejected: measured and retained node counts differ");
        }
        List<InspectorNode> projected = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            JsxHostTree.Node node = nodes.get(index);
            InspectorNode source = measured.get(index);
            if (!node.type.equals(source.type())) {
                throw new IllegalStateException("[NEKO-8001] JSX host layout rejected: measured and retained node types differ");
            }
            Object previousOffset = node.props.get("scrollOffset");
            InspectorNode.Rect rect = new InspectorNode.Rect(source.rect().x() + horizontalShift,
                    source.rect().y() + verticalShift, source.rect().width(), source.rect().height());
            InspectorNode.Rect ancestralClip = parentClip == null ? source.clip() : parentClip;
            InspectorNode.Rect clip = intersect(rect, ancestralClip);
            node.x = (int) Math.round(rect.x());
            node.y = (int) Math.round(rect.y());
            node.width = (int) Math.round(rect.width());
            node.height = (int) Math.round(rect.height());
            node.clipX = (int) Math.ceil(clip.x());
            node.clipY = (int) Math.ceil(clip.y());
            node.clipWidth = Math.max(0, (int) Math.floor(clip.x() + clip.width()) - node.clipX);
            node.clipHeight = Math.max(0, (int) Math.floor(clip.y() + clip.height()) - node.clipY);
            node.hasClip = true;
            node.props = new LinkedHashMap<>(source.style());
            node.props.put("visible", source.visible());
            Double offset = source.scrollOffset();
            double childHorizontalShift = horizontalShift;
            double childVerticalShift = verticalShift;
            if ("scroll".equals(node.type)) {
                boolean horizontal = "row".equals(source.style().get("direction"));
                double scale = "design".equals(source.style().get("coordinateSpace")) ? designScale : 1;
                double authoredOffset = offset == null ? 0 : offset;
                double desiredOffset = source.style().containsKey("scrollOffset") ? authoredOffset
                        : previousOffset instanceof Number number ? number.doubleValue() : 0;
                double origin = horizontal ? source.rect().x() : source.rect().y();
                double extent = horizontal ? source.rect().width() : source.rect().height();
                double contentEnd = origin;
                for (InspectorNode child : source.children()) {
                    double end = horizontal ? child.rect().x() + child.rect().width()
                            : child.rect().y() + child.rect().height();
                    contentEnd = Math.max(contentEnd, end + authoredOffset * scale);
                }
                node.scrollRange = Math.max(0, (contentEnd - origin - extent) / scale);
                offset = Math.max(0, Math.min(desiredOffset, node.scrollRange));
                node.props.put("scrollOffset", offset);
                double correction = (authoredOffset - offset) * scale;
                if (horizontal) childHorizontalShift += correction;
                else childVerticalShift += correction;
            }
            List<InspectorNode> children = project(node.children, source.children(),
                    childHorizontalShift, childVerticalShift, clip, designScale);
            InspectorNode.Overflow overflow = new InspectorNode.Overflow(
                    Math.max(0, ancestralClip.x() - rect.x()), Math.max(0, ancestralClip.y() - rect.y()),
                    Math.max(0, rect.x() + rect.width() - ancestralClip.x() - ancestralClip.width()),
                    Math.max(0, rect.y() + rect.height() - ancestralClip.y() - ancestralClip.height()));
            projected.add(new InspectorNode(source.id(), source.type(), source.key(), source.visible(), node.focused,
                    rect, clip, overflow, offset, node.props, source.bindings(), source.resources(), children));
        }
        return List.copyOf(projected);
    }

    private static InspectorNode.Rect intersect(InspectorNode.Rect rect, InspectorNode.Rect clip) {
        double left = Math.max(rect.x(), clip.x());
        double top = Math.max(rect.y(), clip.y());
        double right = Math.min(rect.x() + rect.width(), clip.x() + clip.width());
        double bottom = Math.min(rect.y() + rect.height(), clip.y() + clip.height());
        return new InspectorNode.Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }
}
//?}
