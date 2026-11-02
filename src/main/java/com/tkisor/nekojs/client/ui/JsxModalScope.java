//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Keeps modal focus history with retained identities, never guest callbacks or candidate nodes. */
final class JsxModalScope {
    private final List<Frame> frames = new ArrayList<>();

    static void validate(Map<String, Object> props) {
        if (!(props.get("id") instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("[NEKO-7012] Dialog id must be a non-empty string");
        }
        if (props.containsKey("title") && !(props.get("title") instanceof String)) {
            throw new IllegalArgumentException("[NEKO-7012] Dialog title must be a string");
        }
        for (String key : List.of("closeOnEscape", "closeOnBackdrop")) {
            if (props.containsKey(key) && !(props.get(key) instanceof Boolean)) {
                throw new IllegalArgumentException("[NEKO-7012] Dialog " + key + " must be a boolean");
            }
        }
    }

    static List<JsxHostTree.Node> dialogs(List<JsxHostTree.Node> roots) {
        List<JsxHostTree.Node> dialogs = new ArrayList<>();
        collectDialogs(roots, dialogs);
        return dialogs;
    }

    private static void collectDialogs(List<JsxHostTree.Node> roots, List<JsxHostTree.Node> output) {
        for (JsxHostTree.Node node : roots) {
            if (!visible(node)) continue;
            if ("dialog".equals(node.type)) output.add(node);
            collectDialogs(node.children, output);
        }
    }

    static JsxHostTree.Node active(List<JsxHostTree.Node> roots) {
        List<JsxHostTree.Node> dialogs = dialogs(roots);
        return dialogs.isEmpty() ? null : dialogs.getLast();
    }

    static boolean contains(JsxHostTree.Node root, JsxHostTree.Node target) {
        if (root == null || target == null || !visible(root)) return false;
        if (root.identity == target.identity) return true;
        for (JsxHostTree.Node child : root.children) if (contains(child, target)) return true;
        return false;
    }

    void synchronize(JsxHostTree tree) {
        List<JsxHostTree.Node> dialogs = dialogs(tree.roots());
        int shared = 0;
        while (shared < frames.size() && shared < dialogs.size()
                && frames.get(shared).dialog == dialogs.get(shared).identity) shared++;
        boolean changed = shared != frames.size() || shared != dialogs.size();
        if (!changed && dialogs.isEmpty()) return;
        JsxHostTree.Node target = focused(tree.roots());
        for (int index = frames.size() - 1; index >= shared; index--) {
            Long restore = frames.remove(index).returnFocus;
            target = restore == null ? null : tree.retained(restore);
        }
        for (int index = shared; index < dialogs.size(); index++) {
            JsxHostTree.Node dialog = dialogs.get(index);
            frames.add(new Frame(dialog.identity, target == null ? null : target.identity));
            target = firstFocusable(dialog.children);
            if (target == null) target = dialog;
        }
        JsxHostTree.Node active = dialogs.isEmpty() ? null : dialogs.getLast();
        boolean invalidFocus = target != null && (tree.retained(target) == null
                || active != null && !contains(active, target)
                || !focusable(target) && (active == null || target.identity != active.identity));
        if (invalidFocus) target = null;
        if (active != null && target == null) {
            target = firstFocusable(active.children);
            if (target == null) target = active;
        } else if ((changed || invalidFocus) && target == null) {
            target = firstFocusable(tree.roots());
        }
        if (changed) tree.cancelCapture();
        if (changed || active != null || invalidFocus) {
            clearFocus(tree.roots());
            if (target != null) target.focused = true;
        }
    }

    static JsxHostTree.Node firstFocusable(List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node node : roots) {
            if (!visible(node) || "dialog".equals(node.type)) continue;
            if (!Boolean.TRUE.equals(node.props.get("disabled")) && focusable(node)) return node;
            JsxHostTree.Node child = firstFocusable(node.children);
            if (child != null) return child;
        }
        return null;
    }

    private static JsxHostTree.Node focused(List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node node : roots) {
            if (!visible(node)) continue;
            if (node.focused) return node;
            JsxHostTree.Node child = focused(node.children);
            if (child != null) return child;
        }
        return null;
    }

    private static void clearFocus(List<JsxHostTree.Node> roots) {
        for (JsxHostTree.Node node : roots) {
            node.focused = false;
            clearFocus(node.children);
        }
    }

    private static boolean focusable(JsxHostTree.Node node) {
        return "button".equals(node.type) || "input".equals(node.type) || "slider".equals(node.type);
    }

    private static boolean visible(JsxHostTree.Node node) {
        return !node.removed && !Boolean.FALSE.equals(node.props.get("visible"));
    }

    private record Frame(long dialog, Long returnFocus) { }
}
//?}
