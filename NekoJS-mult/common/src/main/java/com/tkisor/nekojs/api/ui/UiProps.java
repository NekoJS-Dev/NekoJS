package com.tkisor.nekojs.api.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 不可变 props 包：JSX automatic runtime 产出的属性集合。
 *
 * <p>构造时按插入顺序拷贝成不可修改 map；{@code key} 已由 runtime 提取为 VNode 的独立字段，
 * 不作为 props 成员出现。读取值按受控类型提供——props 值只允许 string / number / boolean /
 * null / 事件函数句柄 / 受控资源标识，不得持有 Minecraft、loader、{@code GuiGraphics}、Font、
 * Screen、GL 或长期可变 host 对象（jsx-ui-spec「VNode 语义」）。
 */
public final class UiProps {

    private static final UiProps EMPTY = new UiProps(Map.of());

    private final Map<String, Object> values;

    private UiProps(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    /** 空 props。 */
    public static UiProps empty() {
        return EMPTY;
    }

    /** 从脚本侧 props 对象构造（拷贝，不保留调用者引用）。 */
    public static UiProps of(Map<String, ?> raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isEmpty()) return EMPTY;
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("props key must not be null");
            }
            if ("key".equals(entry.getKey())) continue;
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy.isEmpty() ? EMPTY : new UiProps(copy);
    }

    /** 属性名（插入顺序，只读）。 */
    public List<String> names() {
        return List.copyOf(values.keySet());
    }

    /** 是否含该属性。 */
    public boolean has(UiProp prop) {
        return values.containsKey(prop.jsName());
    }

    /** 是否含该属性（按脚本侧名）。 */
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /** 原始值（可能为 null；用 {@link #has(UiProp)} 区分「不存在」与「显式 null」）。 */
    public Object raw(UiProp prop) {
        return values.get(prop.jsName());
    }

    /** 原始值（按脚本侧名）。 */
    public Object raw(String name) {
        return values.get(name);
    }

    /** 字符串值；缺失或非字符串返回 null。 */
    public String string(UiProp prop) {
        return string(prop.jsName());
    }

    /** 字符串值（按脚本侧名）；缺失或非字符串返回 null。 */
    public String string(String name) {
        Object value = values.get(name);
        return value instanceof String string ? string : null;
    }

    /** 字符串值，缺失时用 {@code fallback}。 */
    public String string(UiProp prop, String fallback) {
        String value = string(prop);
        return value == null ? fallback : value;
    }

    /** 数值值；缺失或非数值返回 {@code fallback}。 */
    public double number(UiProp prop, double fallback) {
        Object value = values.get(prop.jsName());
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /** 布尔值；缺失或非布尔返回 {@code fallback}。 */
    public boolean bool(UiProp prop, boolean fallback) {
        Object value = values.get(prop.jsName());
        return value instanceof Boolean bool ? bool : fallback;
    }

    /** 可迭代副本，供 host Adapter 与 reconciler 遍历。 */
    public Map<String, Object> asMap() {
        return values;
    }

    /**
     * 校验受控资源标识（{@code namespace:path}）：拒绝 URL、绝对路径、父目录跳转与
     * 反斜杠路径——脚本不得通过 props 传入任意文件句柄或浏览器 URL。
     *
     * @throws UiFailure 非法资源标识
     */
    public static void requireResourceId(String raw, String path) {
        int separator = raw.indexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) {
            throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                    "resource id must be namespace:path, got '" + raw + "'", path);
        }
        String namespace = raw.substring(0, separator);
        String rest = raw.substring(separator + 1);
        if (rest.contains("://") || rest.startsWith("/") || rest.contains("\\")
                || rest.contains("..") || namespace.contains("/") || namespace.contains(".")) {
            throw new UiFailure(UiDiagnosticStage.COMPONENT, UiFailure.INVALID_PROP,
                    "resource id must be a controlled resource path, got '" + raw + "'", path);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UiProps props && values.equals(props.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "UiProps" + values;
    }
}
