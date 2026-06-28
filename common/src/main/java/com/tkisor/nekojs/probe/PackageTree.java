package com.tkisor.nekojs.probe;

import java.util.*;

/**
 * Java 包层级树：从类全限定名构建树结构，用于生成 package 目录。
 *
 * <p>参考 ProbeJS 的 PackageTree，每个包节点都会生成 index.d.ts。
 */
public final class PackageTree {
    private final Node root = new Node("", null);

    public void addClass(String fullyQualifiedName) {
        String[] parts = fullyQualifiedName.split("\\.");
        Node current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            final Node parent = current;
            current = current.children.computeIfAbsent(parts[i], k -> new Node(k, parent));
        }
        current.classes.add(parts[parts.length - 1]);
    }

    public Node getRoot() {
        return root;
    }

    /**
     * DFS 遍历所有包节点（不含根，不含类节点），用于逐个生成 index.d.ts。
     */
    public List<Node> traversePackages() {
        List<Node> result = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node != root) {
                result.add(node);
            }
            // 逆序 push 保证左侧先处理
            List<Node> childList = new ArrayList<>(node.children.values());
            Collections.reverse(childList);
            for (Node child : childList) {
                stack.push(child);
            }
        }
        return result;
    }

    public static class Node {
        public final String name;
        public final Node parent;
        public final Map<String, Node> children = new LinkedHashMap<>();
        public final List<String> classes = new ArrayList<>();

        public Node(String name, Node parent) {
            this.name = name;
            this.parent = parent;
        }

        /**
         * 获取从根到此节点的完整包路径（Java 点分格式）。
         * 例如: "java.lang.reflect"
         */
        public String getPackageName() {
            List<String> path = new ArrayList<>();
            Node current = this;
            while (current != null && !current.name.isEmpty()) {
                path.add(current.name);
                current = current.parent;
            }
            Collections.reverse(path);
            return String.join(".", path);
        }

        /**
         * 获取从根到此节点的完整包路径（文件系统路径格式）。
         * 例如: "java/lang/reflect"
         */
        public String getPackagePath() {
            return getPackageName().replace('.', '/');
        }

        /**
         * 获取直接子包名称列表。
         */
        public List<String> getSubPackageNames() {
            List<String> result = new ArrayList<>();
            for (Node child : children.values()) {
                if (!child.children.isEmpty() || !child.classes.isEmpty()) {
                    result.add(child.name);
                }
            }
            return result;
        }
    }
}
