//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shares shown chrome geometry and controlled resize calculations across paint, layout, and input. */
final class JsxWindowChrome {
    private JsxWindowChrome() { }

    static double titlebarHeight(Map<String, Object> props, double shownHeight) {
        double requested = props.get("titlebarHeight") instanceof Number number ? number.doubleValue() : 20;
        return Math.min(requested, Math.max(0, shownHeight));
    }

    static int titlebarHeight(JsxHostTree.Node node) {
        return (int) titlebarHeight(node.props, node.height);
    }

    static int buttonWidth(JsxHostTree.Node node) {
        double requested = node.props.get("titlebarButtonWidth") instanceof Number number ? number.doubleValue() : 20;
        return (int) Math.min(requested, Math.max(0, node.width));
    }

    record Control(String event, String glyph, int x, int width) { }

    static List<Control> controls(JsxHostTree.Node node) {
        if (titlebarHeight(node) == 0) return List.of();
        List<Control> controls = new ArrayList<>();
        int remaining = node.width;
        int requested = buttonWidth(node);
        if (!Boolean.FALSE.equals(node.props.get("closable"))) {
            remaining = addControl(controls, node.x, remaining, requested, "dismiss", "×");
        }
        if (Boolean.TRUE.equals(node.props.get("maximizable"))) {
            remaining = addControl(controls, node.x, remaining, requested,
                    maximized(node) ? "restore" : "maximize", maximized(node) ? "▣" : "□");
        }
        if (Boolean.TRUE.equals(node.props.get("minimizable"))) {
            addControl(controls, node.x, remaining, requested, "minimize", "−");
        }
        return List.copyOf(controls);
    }

    private static int addControl(List<Control> controls, int x, int remaining, int requested,
                                  String event, String glyph) {
        int width = Math.min(remaining, requested);
        if (width > 0) controls.add(new Control(event, glyph, x + remaining - width, width));
        return remaining - width;
    }

    static Control controlAt(JsxHostTree.Node node, double x) {
        for (Control control : controls(node)) {
            if (x >= control.x() && x < (double) control.x() + control.width()) return control;
        }
        return null;
    }

    static boolean maximized(JsxHostTree.Node node) {
        return "maximized".equals(node.props.get("mode"));
    }

    static boolean resizable(JsxHostTree.Node node) {
        return Boolean.TRUE.equals(node.props.get("resizable")) && !maximized(node);
    }

    static String resizeEdge(JsxHostTree.Node node, double x, double y) {
        if (!resizable(node)) return null;
        double horizontalBand = Math.min(4, node.width / 2.0);
        double verticalBand = Math.min(4, node.height / 2.0);
        String horizontal = x < node.x + horizontalBand ? "w"
                : x >= (double) node.x + node.width - horizontalBand ? "e" : "";
        String vertical = y < node.y + verticalBand ? "n"
                : y >= (double) node.y + node.height - verticalBand ? "s" : "";
        String edge = vertical + horizontal;
        return edge.isEmpty() ? null : edge;
    }

    /** Keeps the original opposite edge fixed; controller policy owns subsequent min/max clamping. */
    record ResizeCapture(long identity, String edge, double pointerX, double pointerY,
                         double x, double y, double width, double height) {
        Map<String, Object> request(double mouseX, double mouseY) {
            double deltaX = mouseX - pointerX;
            double deltaY = mouseY - pointerY;
            double left = edge.endsWith("w") ? Math.min(x + width, x + deltaX) : x;
            double right = edge.endsWith("e") ? Math.max(x, x + width + deltaX) : x + width;
            double top = edge.startsWith("n") ? Math.min(y + height, y + deltaY) : y;
            double bottom = edge.startsWith("s") ? Math.max(y, y + height + deltaY) : y + height;
            return Map.of("x", left, "y", top, "width", right - left, "height", bottom - top, "edge", edge);
        }
    }
}
//?}
