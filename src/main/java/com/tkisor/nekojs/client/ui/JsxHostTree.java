//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minecraft-independent retained host nodes and atomic reconciliation transaction. */
final class JsxHostTree {
    private Map<Long, Node> nodes = new LinkedHashMap<>();
    private List<Node> roots = List.of();
    private long nextIdentity = 1;
    private Long capturedIdentity;
    private int capturedButton;

    List<Node> roots() { return roots; }

    void capture(Node node, int button) {
        cancelCapture();
        if (!nodes.containsKey(node.identity)) return;
        capturedIdentity = node.identity;
        capturedButton = button;
        nodes.get(node.identity).pressed = true;
    }

    Node releaseCapture(int button) {
        if (capturedIdentity == null || capturedButton != button) return null;
        Node captured = nodes.get(capturedIdentity);
        cancelCapture();
        return captured;
    }

    void cancelCapture() {
        if (capturedIdentity != null) {
            Node captured = nodes.get(capturedIdentity);
            if (captured != null) captured.pressed = false;
        }
        capturedIdentity = null;
    }

    void clear() {
        cancelCapture();
        nodes = new LinkedHashMap<>();
        roots = List.of();
    }

    void close(Runnable closeRoot) {
        closeRoot.run();
        clear();
    }

    Transaction begin() { return new Transaction(); }

    final class Transaction {
        private final Map<Long, Node> staged = new LinkedHashMap<>();
        private List<Node> stagedRoots = new ArrayList<>();
        private boolean finished;

        private Transaction() {
            for (Node node : nodes.values()) staged.put(node.identity, node.copy());
            for (Node node : nodes.values()) {
                Node copy = staged.get(node.identity);
                for (Node child : node.children) copy.children.add(staged.get(child.identity));
            }
            for (Node node : roots) stagedRoots.add(staged.get(node.identity));
        }

        Object create(String type, String key, Map<String, Object> props) {
            requireOpen();
            Node node = new Node(nextIdentity++, type, key, new LinkedHashMap<>(props));
            staged.put(node.identity, node);
            return node;
        }

        void update(Object handle, String type, String key, Map<String, Object> props) {
            requireOpen();
            Node node = node(handle);
            node.type = type;
            node.key = key;
            Object previousOffset = node.props.get("scrollOffset");
            node.props = new LinkedHashMap<>(props);
            if ("scroll".equals(type) && !node.props.containsKey("scrollOffset") && previousOffset != null) {
                node.props.put("scrollOffset", previousOffset);
            }
            if ("input".equals(type) && node.props.containsKey("value")) {
                node.setInputValue(text(node.props.get("value")));
            }
        }

        void order(Object parent, List<?> children) {
            requireOpen();
            List<Node> ordered = new ArrayList<>();
            for (Object child : children) ordered.add(node(child));
            if (parent == null) stagedRoots = ordered;
            else node(parent).children = ordered;
        }

        void remove(Object handle) {
            requireOpen();
            Node node = node(handle);
            node.removed = true;
            staged.remove(node.identity);
            for (Node candidate : staged.values()) candidate.children.remove(node);
            stagedRoots.remove(node);
        }

        void commit(List<?> requestedRoots) {
            commit(requestedRoots, ignored -> { });
        }

        void commit(List<?> requestedRoots, java.util.function.Consumer<List<Node>> beforePublish) {
            requireOpen();
            List<Node> ordered = new ArrayList<>();
            for (Object root : requestedRoots) ordered.add(node(root));
            if (!ordered.equals(stagedRoots)) throw new IllegalStateException("JSX host root order was not staged");
            List<Node> candidate = List.copyOf(stagedRoots);
            beforePublish.accept(candidate);
            nodes = staged;
            roots = candidate;
            finished = true;
        }

        void rollback() { finished = true; }

        private Node node(Object handle) {
            if (handle instanceof Node candidate && staged.get(candidate.identity) == candidate) return candidate;
            if (handle instanceof Node candidate && staged.containsKey(candidate.identity)) return staged.get(candidate.identity);
            throw new IllegalArgumentException("Unknown JSX host node handle");
        }

        private void requireOpen() {
            if (finished) throw new IllegalStateException("JSX host transaction is closed");
        }
    }

    static final class Node {
        final long identity;
        String type;
        String key;
        Map<String, Object> props;
        List<Node> children = new ArrayList<>();
        String inputValue;
        int cursor;
        int selectionStart;
        int selectionEnd;
        boolean focused;
        boolean pressed;
        boolean removed;
        int x;
        int y;
        int width;
        int height;
        int clipX;
        int clipY;
        int clipWidth;
        int clipHeight;
        boolean hasClip;
        double scrollRange;
        com.tkisor.nekojs.api.ui.VisualSpec visual;
        UiTextureBlitPlan texturePlan;
        com.tkisor.nekojs.api.ui.TextLayout textLayout;
        double textScale = 1;

        Node(long identity, String type, String key, Map<String, Object> props) {
            this.identity = identity;
            this.type = type;
            this.key = key;
            this.props = props;
            this.inputValue = "input".equals(type) ? text(props.get("value")) : null;
            this.cursor = this.inputValue == null ? 0 : this.inputValue.length();
            this.selectionStart = this.cursor;
            this.selectionEnd = this.cursor;
        }

        boolean hasSelection() { return selectionStart != selectionEnd; }

        int selectionStart() { return Math.min(selectionStart, selectionEnd); }

        int selectionEnd() { return Math.max(selectionStart, selectionEnd); }

        void collapseSelection(int position) {
            cursor = Math.max(0, Math.min(position, inputValue.length()));
            if (cursor > 0 && cursor < inputValue.length()
                    && Character.isHighSurrogate(inputValue.charAt(cursor - 1))
                    && Character.isLowSurrogate(inputValue.charAt(cursor))) cursor--;
            selectionStart = cursor;
            selectionEnd = cursor;
        }

        void replaceSelection(String replacement) {
            int start = selectionStart();
            int end = selectionEnd();
            inputValue = inputValue.substring(0, start) + replacement + inputValue.substring(end);
            cursor = start + replacement.length();
            selectionStart = cursor;
            selectionEnd = cursor;
        }

        boolean deleteBackward() {
            if (hasSelection()) {
                replaceSelection("");
                return true;
            }
            if (cursor <= 0) return false;
            int previous = inputValue.offsetByCodePoints(cursor, -1);
            inputValue = inputValue.substring(0, previous) + inputValue.substring(cursor);
            collapseSelection(previous);
            return true;
        }

        boolean deleteForward() {
            if (hasSelection()) {
                replaceSelection("");
                return true;
            }
            if (cursor >= inputValue.length()) return false;
            int next = inputValue.offsetByCodePoints(cursor, 1);
            inputValue = inputValue.substring(0, cursor) + inputValue.substring(next);
            collapseSelection(cursor);
            return true;
        }

        void setInputValue(String value) {
            if (value.equals(inputValue)) return;
            inputValue = value;
            collapseSelection(Math.min(cursor, value.length()));
        }

        void moveCursor(int position, boolean selecting) {
            int target = Math.max(0, Math.min(position, inputValue.length()));
            if (target > 0 && target < inputValue.length()
                    && Character.isLowSurrogate(inputValue.charAt(target))) target--;
            cursor = target;
            if (selecting) selectionEnd = cursor;
            else collapseSelection(cursor);
        }

        void selectAll() {
            selectionStart = 0;
            selectionEnd = inputValue.length();
            cursor = selectionEnd;
        }

        void insertText(String value, int maxLength) {
            int start = selectionStart();
            int end = selectionEnd();
            int retainedLength = inputValue.codePointCount(0, start)
                    + inputValue.codePointCount(end, inputValue.length());
            int capacity = Math.max(0, maxLength - retainedLength);
            int accepted = Math.min(capacity, value.codePointCount(0, value.length()));
            if (accepted == 0 && !hasSelection()) return;
            replaceSelection(value.substring(0, value.offsetByCodePoints(0, accepted)));
        }

        void updateScrollRange(double range) {
            scrollRange = Math.max(0, range);
            Object offset = props.get("scrollOffset");
            double previous = offset instanceof Number number ? number.doubleValue() : 0;
            double next = Math.max(0, Math.min(previous, scrollRange));
            props.put("scrollOffset", next);
            int displacement = (int) previous - (int) next;
            for (Node child : children) child.translateVertically(displacement);
        }

        private void translateVertically(int displacement) {
            y += displacement;
            for (Node child : children) child.translateVertically(displacement);
        }

        Node copy() {
            Node copy = new Node(identity, type, key, new LinkedHashMap<>(props));
            copy.inputValue = inputValue;
            copy.cursor = cursor;
            copy.selectionStart = selectionStart;
            copy.selectionEnd = selectionEnd;
            copy.focused = focused;
            copy.pressed = pressed;
            copy.removed = removed;
            copy.x = x;
            copy.y = y;
            copy.width = width;
            copy.height = height;
            copy.clipX = clipX;
            copy.clipY = clipY;
            copy.clipWidth = clipWidth;
            copy.clipHeight = clipHeight;
            copy.hasClip = hasClip;
            copy.scrollRange = scrollRange;
            copy.visual = visual;
            copy.texturePlan = texturePlan;
            copy.textLayout = textLayout;
            copy.textScale = textScale;
            return copy;
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
//?}
