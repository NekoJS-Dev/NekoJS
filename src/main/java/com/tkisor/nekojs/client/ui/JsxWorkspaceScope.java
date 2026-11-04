//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates workspace ownership and confines input to active windows and explicit presentation panels. */
final class JsxWorkspaceScope {
    private JsxWorkspaceScope() { }

    static void validateProps(String type, Map<String, Object> props) {
        if ("panel".equals(type)) optionalBoolean(props, "workspacePresentation");
        if (!"workspace".equals(type) && !"window".equals(type)) return;
        if (props.containsKey("coordinateSpace") && !"logical".equals(props.get("coordinateSpace"))) {
            throw invalid(type + " coordinateSpace must be logical");
        }
        for (String key : List.of("visible", "disabled")) optionalBoolean(props, key);
        if (!"window".equals(type)) return;
        if (!(props.get("id") instanceof String id) || id.isEmpty()) {
            throw invalid("Window id must be a non-empty string");
        }
        if (!(props.get("title") instanceof String)) throw invalid("Window title must be a string");
        optionalBoolean(props, "active");
        geometry(props, "x", -Double.MAX_VALUE);
        geometry(props, "y", -Double.MAX_VALUE);
        geometry(props, "width", 40);
        geometry(props, "height", 20);
        optionalBoolean(props, "draggable");
        optionalBoolean(props, "closable");
        optionalBoolean(props, "resizable");
        optionalBoolean(props, "minimizable");
        optionalBoolean(props, "maximizable");
        optionalBoolean(props, "titlebarTitle");
        chromeMetric(props, "titlebarHeight", 0);
        chromeMetric(props, "titlebarButtonWidth", 1);
        if (props.containsKey("mode") && !"normal".equals(props.get("mode"))
                && !"maximized".equals(props.get("mode"))) {
            throw invalid("Window mode must be normal or maximized");
        }
    }

    static void validateTree(List<JsxHostTree.Node> roots) {
        validateTree(roots, null, 0, false, new int[2]);
    }

    private static void validateTree(List<JsxHostTree.Node> nodes, JsxHostTree.Node parent,
                                     int depth, boolean inheritedDesign, int[] counts) {
        for (JsxHostTree.Node node : nodes) {
            if (node.removed) continue;
            validateProps(node.type, node.props);
            if (Boolean.TRUE.equals(node.props.get("workspacePresentation"))
                    && (!"panel".equals(node.type) || parent == null || depth != 1
                    || !"screen".equals(parent.type)
                    || parent.children.stream().noneMatch(sibling -> "workspace".equals(sibling.type)))) {
                throw invalid("Presentation panels must be direct root screen siblings of a workspace");
            }
            boolean design = inheritedDesign || "design".equals(node.props.get("coordinateSpace"));
            if (design && ("workspace".equals(node.type) || "window".equals(node.type))) {
                throw invalid("Workspace/window coordinates and their ancestors must use GUI space");
            }
            if (parent != null && "workspace".equals(parent.type) && !"window".equals(node.type)) {
                throw invalid("Workspace children must be windows");
            }
            if ("workspace".equals(node.type)) {
                if (++counts[0] > 1) throw invalid("A host tree may contain only one workspace");
                if (parent != null && !(depth == 1 && "screen".equals(parent.type))) {
                    throw invalid("Workspace must be a root or a direct child of a root screen");
                }
            } else if ("window".equals(node.type)) {
                if (parent == null || !"workspace".equals(parent.type)) {
                    throw invalid("Window must be a direct workspace child");
                }
                if (Boolean.TRUE.equals(node.props.get("active")) && ++counts[1] > 1) {
                    throw invalid("A host tree may contain only one active window");
                }
            }
            validateTree(node.children, node, depth + 1, design, counts);
        }
    }

    static JsxHostTree.Node workspace(List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node node : roots) {
            if (node.removed) continue;
            if ("workspace".equals(node.type)) return node;
            JsxHostTree.Node workspace = workspace(node.children);
            if (workspace != null) return workspace;
        }
        return null;
    }

    static JsxHostTree.Node activeWindow(List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node node : roots) {
            if (!enabled(node)) continue;
            if ("window".equals(node.type) && Boolean.TRUE.equals(node.props.get("active"))) return node;
            JsxHostTree.Node active = activeWindow(node.children);
            if (active != null) return active;
        }
        return null;
    }

    static List<JsxHostTree.Node> inputRoots(List<JsxHostTree.Node> roots) {
        List<JsxHostTree.Node> scope = new ArrayList<>();
        JsxHostTree.Node active = activeWindow(roots);
        if (active != null) scope.add(active);
        scope.addAll(presentations(roots));
        return scope;
    }

    private static List<JsxHostTree.Node> presentations(List<JsxHostTree.Node> roots) {
        List<JsxHostTree.Node> panels = new ArrayList<>();
        for (JsxHostTree.Node root : roots) {
            if (!visible(root) || !"screen".equals(root.type)) continue;
            for (JsxHostTree.Node child : root.children) {
                if (visible(child) && Boolean.TRUE.equals(child.props.get("workspacePresentation"))) panels.add(child);
            }
        }
        return panels;
    }

    static JsxHostTree.Node presentationAt(List<JsxHostTree.Node> roots, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
        JsxHostTree.Node result = null;
        for (JsxHostTree.Node panel : presentations(roots)) {
            if (withinBounds(panel, x, y) && withinClip(panel, x, y)) result = panel;
        }
        return result;
    }

    /** Empty and disabled window bodies still occlude windows earlier in published order. */
    static JsxHostTree.Node windowAt(List<JsxHostTree.Node> roots, double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
        JsxHostTree.Node result = null;
        for (JsxHostTree.Node node : roots) {
            if (!visible(node) || !withinClip(node, x, y)) continue;
            if ("window".equals(node.type) && withinBounds(node, x, y)) result = node;
            JsxHostTree.Node nested = windowAt(node.children, x, y);
            if (nested != null) result = nested;
        }
        return result;
    }

    /** Resolves retained identities even when reconciliation has replaced the node objects. */
    static JsxHostTree.Node windowFor(List<JsxHostTree.Node> roots, JsxHostTree.Node target) {
        return target == null ? null : windowFor(roots, target.identity, null);
    }

    private static JsxHostTree.Node windowFor(List<JsxHostTree.Node> nodes, long identity,
                                             JsxHostTree.Node window) {
        for (JsxHostTree.Node node : nodes) {
            if (node.removed) continue;
            JsxHostTree.Node owner = "window".equals(node.type) ? node : window;
            if (node.identity == identity) return owner;
            JsxHostTree.Node nested = windowFor(node.children, identity, owner);
            if (nested != null) return nested;
        }
        return null;
    }

    /** A visible global modal outranks window activation, including modals in inactive windows. */
    static boolean scopeAllowed(List<JsxHostTree.Node> roots, JsxHostTree.Node target) {
        if (target == null || !containsEnabled(roots, target.identity)) return false;
        JsxHostTree.Node modal = JsxModalScope.active(roots);
        if (modal != null) return JsxModalScope.contains(modal, target);
        if (workspace(roots) == null) return true;
        for (JsxHostTree.Node scope : inputRoots(roots)) {
            if (JsxModalScope.contains(scope, target)) return true;
        }
        return false;
    }

    static void synchronize(JsxHostTree tree) {
        List<JsxHostTree.Node> roots = tree.roots();
        // Modal focus history and capture transitions belong exclusively to the modal scope.
        if (workspace(roots) == null || JsxModalScope.active(roots) != null) return;
        if (clearOutside(roots, activeWindow(roots), false, true)) tree.cancelCapture();
    }

    private static boolean clearOutside(List<JsxHostTree.Node> nodes, JsxHostTree.Node active,
                                        boolean inside, boolean ancestorsEnabled) {
        boolean cancelCapture = false;
        for (JsxHostTree.Node node : nodes) {
            boolean inWindow = inside || active != null && node.identity == active.identity
                    || Boolean.TRUE.equals(node.props.get("workspacePresentation"));
            boolean eligible = ancestorsEnabled && enabled(node);
            if (!inWindow || !eligible) {
                node.focused = false;
                // Capture marks its published node pressed, regardless of which mouse button owns it.
                if (node.pressed) cancelCapture = true;
            }
            if (clearOutside(node.children, active, inWindow, eligible)) cancelCapture = true;
        }
        return cancelCapture;
    }

    private static boolean containsEnabled(List<JsxHostTree.Node> nodes, long identity) {
        for (JsxHostTree.Node node : nodes) {
            if (!enabled(node)) continue;
            if (node.identity == identity || containsEnabled(node.children, identity)) return true;
        }
        return false;
    }

    private static boolean withinBounds(JsxHostTree.Node node, double x, double y) {
        return x >= node.x && y >= node.y && x < (double) node.x + node.width
                && y < (double) node.y + node.height;
    }

    private static boolean withinClip(JsxHostTree.Node node, double x, double y) {
        return !node.hasClip || x >= node.clipX && y >= node.clipY
                && x < (double) node.clipX + node.clipWidth && y < (double) node.clipY + node.clipHeight;
    }

    private static boolean visible(JsxHostTree.Node node) {
        return !node.removed && !Boolean.FALSE.equals(node.props.get("visible"));
    }

    private static boolean enabled(JsxHostTree.Node node) {
        return visible(node) && !Boolean.TRUE.equals(node.props.get("disabled"));
    }

    private static void optionalBoolean(Map<String, Object> props, String key) {
        if (props.containsKey(key) && !(props.get(key) instanceof Boolean)) {
            throw invalid("Workspace/window " + key + " must be a boolean");
        }
    }

    private static void chromeMetric(Map<String, Object> props, String key, double minimum) {
        if (!props.containsKey(key)) return;
        Object raw = props.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < minimum || number.doubleValue() != Math.rint(number.doubleValue())) {
            throw invalid("Window " + key + " must be a finite integer >= " + minimum);
        }
    }

    private static void geometry(Map<String, Object> props, String key, double minimum) {
        Object raw = props.get(key);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < minimum) {
            throw invalid("Window " + key + " must be a finite logical coordinate and >= " + minimum);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("[NEKO-7013] " + message);
    }
}
//?}
