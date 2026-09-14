package com.tkisor.nekojs.core.ui;

import com.tkisor.nekojs.api.ui.ComponentVNode;
import com.tkisor.nekojs.api.ui.FragmentVNode;
import com.tkisor.nekojs.api.ui.HostVNode;
import com.tkisor.nekojs.api.ui.UiDiagnosticStage;
import com.tkisor.nekojs.api.ui.UiFailure;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiVNode;
import com.tkisor.nekojs.api.ui.UiVNodeFactory;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UiVNodeFactory} 的 Graal 实现：消费 automatic runtime（jsx / jsxs / Fragment）
 * 产出的脚本侧元素，以及条件表达式、数组、spread props、key 与 children。
 *
 * <p>脚本侧元素形状沿用既有运行时：
 * <ul>
 *   <li>既有裸对象：{@code { $$nekoJsx, type, key, props, children }}
 *   <li>automatic runtime 分支：{@code { kind, type, props.children }} 或
 *       {@code type === Fragment} 的 fragment 形态
 * </ul>
 * 两种形态都能在同一转换器里被识别——不新增第二套 runtime。
 *
 * <p>函数组件不在这里执行：大写标签转成 {@link ComponentVNode}，由 reconciler 经
 * {@code UiComponentRegistry} 展开（组件必须返回 VNode / fragment / 空值）。
 */
public final class ScriptUiVNodeFactory implements UiVNodeFactory {

    /** 既有 jsx-runtime 的元素标记。 */
    public static final String JSX_MARKER = "$$nekoJsx";

    private final int maxDepth;

    public ScriptUiVNodeFactory() {
        this(64);
    }

    public ScriptUiVNodeFactory(int maxDepth) {
        this.maxDepth = maxDepth <= 0 ? 64 : maxDepth;
    }

    @Override
    public List<UiVNode> toVNodes(Object scriptValue, String contextPath) {
        List<UiVNode> nodes = new ArrayList<>();
        append(scriptValue, contextPath, nodes, 0);
        return List.copyOf(nodes);
    }

    /** JSX 元素 → VNode；组件展开由返回值递归处理。 */
    public UiVNode toVNode(Object scriptValue, String contextPath) {
        List<UiVNode> nodes = toVNodes(scriptValue, contextPath);
        if (nodes.isEmpty()) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "JSX value did not produce a node", contextPath);
        }
        if (nodes.size() > 1) {
            return new FragmentVNode(null, nodes);
        }
        return nodes.getFirst();
    }

    private void append(Object raw, String path, List<UiVNode> out, int depth) {
        if (depth > maxDepth) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "JSX nesting exceeds " + maxDepth + " levels", path);
        }
        if (raw == null) {
            return;
        }
        if (raw instanceof UiVNode node) {
            out.add(node);
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            out.add(fromMap(map, path, depth));
            return;
        }
        if (raw instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object element : iterable) {
                append(element, path + "[" + index + "]", out, depth + 1);
                index++;
            }
            return;
        }
        if (raw instanceof Value value) {
            if (value.isNull()) {
                return;
            }
            if (value.hasArrayElements()) {
                for (long i = 0; i < value.getArraySize(); i++) {
                    append(value.getArrayElement(i), path + "[" + i + "]", out, depth + 1);
                }
                return;
            }
            if (value.isHostObject() && !value.hasMembers()) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                        "JSX child must not be a native host object", path);
            }
            if (value.canExecute()) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                        "JSX child must not be a bare function; use a component tag instead", path);
            }
            if (!value.hasMembers()) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                        "JSX child must be a VNode, fragment, array or null", path);
            }
            out.add(fromMemberValue(value, path, depth));
            return;
        }
        throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                "unsupported JSX value: " + raw.getClass().getName(), path);
    }

    /** 平台无关的元素形状转换：{@code { $$nekoJsx, kind, type, key, props, children }}。 */
    private UiVNode fromMap(Map<?, ?> map, String path, int depth) {
        Object type = map.get("type");
        Object keyRaw = map.get("key");
        String key = keyRaw == null ? null : String.valueOf(keyRaw);
        Object propsRaw = map.get("props");
        Map<String, Object> props = new java.util.LinkedHashMap<>();
        List<UiVNode> children = new ArrayList<>();
        if (propsRaw instanceof Map<?, ?> propsMap) {
            for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if ("key".equals(name)) continue;
                if ("children".equals(name)) {
                    append(entry.getValue(), path + "/children", children, depth + 1);
                    continue;
                }
                props.put(name, entry.getValue());
            }
        }
        Object inlineChildren = map.get("children");
        if (inlineChildren != null) {
            append(inlineChildren, path + "/children", children, depth + 1);
        }
        UiProps uiProps = UiProps.of(props);

        if (type == null) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "JSX element must carry a string primitive tag or a component type", path);
        }
        if (type instanceof String string) {
            if ("Fragment".equals(string)) {
                return new FragmentVNode(key, children);
            }
            UiPrimitive primitive = UiPrimitive.fromTag(string);
            if (primitive == null) {
                throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.UNKNOWN_PRIMITIVE,
                        "unknown JSX primitive <" + string + ">", path);
            }
            return new HostVNode(primitive, key, uiProps, children);
        }
        Object componentName = map.get("component");
        String name = componentName != null ? String.valueOf(componentName) : String.valueOf(type);
        if (name.isBlank()) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "component type must not be blank", path);
        }
        return new ComponentVNode(name, key, uiProps, children);
    }

    private UiVNode fromMemberValue(Value value, String path, int depth) {
        Object type = member(value, "type");
        String key = stringMember(value, "key");
        UiProps props = propsOf(value, path);
        List<UiVNode> children = childrenOf(value, props, path, depth);

        if (isFragmentType(type)) {
            return new FragmentVNode(key, children);
        }
        if (type instanceof Value typeValue && typeValue.isString()) {
            return hostNode(typeValue.asString(), key, props, children, path);
        }
        if (type instanceof String string) {
            return hostNode(string, key, props, children, path);
        }
        if (type instanceof Value typeValue && typeValue.canExecute()) {
            String name = componentNameOf(value, typeValue);
            return new ComponentVNode(name, key, props, children);
        }
        if (type != null) {
            String name = type.toString();
            if (!name.isBlank()) {
                return new ComponentVNode(name, key, props, children);
            }
        }
        throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                "JSX element must carry a string primitive tag or a function component type", path);
    }

    private UiVNode hostNode(String tag, String key, UiProps props, List<UiVNode> children, String path) {
        UiPrimitive primitive = UiPrimitive.fromTag(tag);
        if (primitive == null) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.UNKNOWN_PRIMITIVE,
                    "unknown JSX primitive <" + tag + ">", path);
        }
        return new HostVNode(primitive, key, props, children);
    }

    private static boolean isFragmentType(Object type) {
        if (type == null) return false;
        String text = type.toString();
        if ("Fragment".equals(text) || "nekojs.jsx.fragment".equals(text)) {
            return true;
        }
        if (type instanceof Value value) {
            return value.isString() && "Fragment".equals(safeString(value));
        }
        return false;
    }

    private static String safeString(Value value) {
        try {
            return value.isString() ? value.asString() : value.toString();
        } catch (RuntimeException ignored) {
            return value.toString();
        }
    }

    private UiProps propsOf(Value value, String path) {
        Object propsRaw = member(value, "props");
        if (!(propsRaw instanceof Value propsValue) || propsValue.isNull()) {
            return UiProps.empty();
        }
        if (!propsValue.hasMembers()) {
            throw new UiFailure(UiDiagnosticStage.RENDER, UiFailure.INVALID_CHILD,
                    "JSX props must be an object", path);
        }
        Map<String, Object> props = new LinkedHashMap<>();
        for (String name : propsValue.getMemberKeys()) {
            if ("key".equals(name) || "children".equals(name)) continue;
            Object raw = propsValue.getMember(name);
            props.put(name, unwrap(raw));
        }
        return UiProps.of(props);
    }

    private List<UiVNode> childrenOf(Value value, UiProps props, String path, int depth) {
        List<UiVNode> children = new ArrayList<>();
        Object inline = member(value, "children");
        if (inline != null) {
            append(removeInlineChildrenOwner(inline), path + "/children", children, depth + 1);
        }
        if (props.has("children")) {
            append(props.raw("children"), path + "/children", children, depth + 1);
        }
        return List.copyOf(children);
    }

    /** 既有 runtime 把 children 存成数组；这里保持原样交给 {@link #append}。 */
    private static Object removeInlineChildrenOwner(Object inline) {
        return inline;
    }

    private static Object member(Value value, String name) {
        if (!value.hasMember(name)) return null;
        Object member = value.getMember(name);
        if (member instanceof Value memberValue && memberValue.isNull()) return null;
        return member;
    }

    private static String stringMember(Value value, String name) {
        Object raw = member(value, name);
        if (raw instanceof Value memberValue && memberValue.isString()) return memberValue.asString();
        return raw instanceof String string && !string.isBlank() ? string : null;
    }

    /**
     * 组件标识：优先脚本侧显式 component 名，其次函数自身的 name（JS 函数声明名），
     * 最后退回进程内稳定序号。
     *
     * <p>不使用 asProxyObject()——它只对 Graal host proxy 有效，普通 JS 函数会抛
     * ClassCastException（如实记录于本票 closure record）。
     */
    private static String componentNameOf(Value element, Value typeValue) {
        Object name = member(element, "component");
        if (name instanceof Value nameValue && nameValue.isString()) return nameValue.asString();
        if (name instanceof String string && !string.isBlank()) return string;
        if (typeValue.hasMember("name")) {
            Object functionName = typeValue.getMember("name");
            if (functionName instanceof Value nameValue && nameValue.isString()
                    && !nameValue.asString().isBlank()) {
                return nameValue.asString();
            }
        }
        return "Component#" + System.identityHashCode(element);
    }

    /** 把脚本侧值解包为 Java 基本量；函数保持原样（事件值由 host Adapter 校验）。 */
    private static Object unwrap(Object raw) {
        if (!(raw instanceof Value value)) {
            return raw;
        }
        if (value.isNull()) return null;
        if (value.isString()) return value.asString();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isNumber()) return value.asDouble();
        return value;
    }
}