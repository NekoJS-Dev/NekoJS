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

    List<Node> roots() { return roots; }

    void clear() { nodes = new LinkedHashMap<>(); roots = List.of(); }

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
            node.props = new LinkedHashMap<>(props);
            if ("input".equals(type) && !node.focused) node.inputValue = text(node.props.get("value"));
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
        boolean focused;
        boolean removed;
        int x;
        int y;
        int width;
        int height;
        double scrollRange;

        Node(long identity, String type, String key, Map<String, Object> props) {
            this.identity = identity;
            this.type = type;
            this.key = key;
            this.props = props;
            this.inputValue = "input".equals(type) ? text(props.get("value")) : null;
        }

        Node copy() {
            Node copy = new Node(identity, type, key, new LinkedHashMap<>(props));
            copy.inputValue = inputValue;
            copy.focused = focused;
            copy.removed = removed;
            copy.x = x;
            copy.y = y;
            copy.width = width;
            copy.height = height;
            copy.scrollRange = scrollRange;
            return copy;
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
//?}
