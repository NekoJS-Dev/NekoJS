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
 * 不作为 props 成员出现。读取值按受控类型提供——脚本侧的 props 值只允许
 * string / number / boolean / null / 事件函数句柄 / 受控资源标识，不得持有
 * Minecraft、loader、{@code GuiGraphics}、Font、Screen、GL 或长期可变 host 对象
 * （jsx-ui-spec「VNode 语义」）。
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
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /** 原始值（可能为 {@code null}；用 {@link #has} 区分「不存在」与「显式 null」）。 */
    public Object raw(String name) {
        return values.get(name);
    }

    /** 字符串值；缺失或非字符串返回 {@code null}。 */
    public String string(String name) {
        Object value = values.get(name);
        return value instanceof String string ? string : null;
    }

    /** 字符串值，缺失时用 {@code fallback}。 */
    public String string(String name, String fallback) {
        String value = string(name);
        return value == null ? fallback : value;
    }

    /** 数值值；缺失或非数值返回 {@code fallback}。 */
    public double number(String name, double fallback) {
        Object value = values.get(name);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    /** 数值值是否存在。 */
    public boolean hasNumber(String name) {
        return values.get(name) instanceof Number;
    }

    /** 布尔值；缺失或非布尔返回 {@code fallback}。 */
    public boolean bool(String name, boolean fallback) {
        Object value = values.get(name);
        return value instanceof Boolean bool ? bool : fallback;
    }

    /** 可迭代副本，供 host Adapter 与 reconciler 遍历。 */
    public Map<String, Object> asMap() {
        return values;
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
