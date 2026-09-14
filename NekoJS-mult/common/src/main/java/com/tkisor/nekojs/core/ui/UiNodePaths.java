package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.UiPrimitive;

/**
 * host tree 的稳定节点路径：{@code screen/row[0]/button[2]}，带 key 的节点追加 {@code #key}。
 *
 * <p>路径是诊断（{@link com.tkisor.nekojs.api.ui.UiDiagnostic#nodePath()}）与 inspector 的
 * 稳定标识，不暴露 reconciler 内部结构。
 */
final class UiNodePaths {

    private UiNodePaths() {
    }

    static String rootPath(UiPrimitive primitive) {
        return primitive.tag();
    }

    static String childPath(String parentPath, UiPrimitive primitive, int index, String key) {
        if (parentPath == null) {
            return primitive.tag();
        }
        StringBuilder sb = new StringBuilder(parentPath);
        sb.append('/').append(primitive.tag()).append('[').append(index).append(']');
        if (key != null) {
            sb.append('#').append(key);
        }
        return sb.toString();
    }
}
