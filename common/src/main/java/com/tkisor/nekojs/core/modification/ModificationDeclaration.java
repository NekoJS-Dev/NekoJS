package com.tkisor.nekojs.core.modification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一条 Item/Block 属性修改声明（票 39）：candidate 收集期由脚本回调产出、经平台 Adapter
 * 规范化后的<b>纯 JVM</b> 数据——目标由领域种类 + 归一化 id 表达，属性值只允许
 * String/Number/Boolean/null/嵌套 Map（key 为 String、值递归同约束），不携带任何
 * MC/loader 类型。MC 应用（组件/属性写入）只发生在 commit 点的平台/版本 Adapter。
 *
 * <p>同一计划内声明的<b>顺序就是重放顺序</b>：与旧行为 characterization 一致，同目标
 * 的后一声明整体替换前一声明（每次从基线叠加，不按属性合并）——不引入同 key 拒绝，
 * 也不新增未裁定的合并政策（工单 AC6）。
 *
 * <p>不可变；{@link #properties()} 返回只读视图。
 */
public final class ModificationDeclaration {

    /** 领域种类（平台 Adapter 约定的工作名）：{@code "item"} / {@code "block"}。 */
    private final String kind;
    /** 归一化目标 id（{@code namespace:path}，缺省命名空间已补全）。 */
    private final String targetId;
    /** 规范化属性值（写入序保持；同名属性后写覆盖前写）。 */
    private final Map<String, Object> properties;
    /** 声明来源脚本 id（失败归因/诊断用；可为 null）。 */
    private final String scriptId;

    public ModificationDeclaration(String kind, String targetId, Map<String, Object> properties, String scriptId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        Map<String, Object> copy = new LinkedHashMap<>();
        if (properties != null) {
            properties.forEach((name, value) -> copy.put(
                    Objects.requireNonNull(name, "property name"),
                    requirePlainValue(value, name)));
        }
        this.properties = Collections.unmodifiableMap(copy);
        this.scriptId = scriptId;
    }

    public String kind() {
        return kind;
    }

    public String targetId() {
        return targetId;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public String scriptId() {
        return scriptId;
    }

    /** 值只允许纯 JVM 形态（Adapter 规范化的产物）；MC 对象在 common 侧结构性拒绝。 */
    private static Object requirePlainValue(Object value, String name) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long
                || value instanceof Float || value instanceof Double) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException(
                            "property '" + name + "' map key must be a String: " + entry.getKey());
                }
                copy.put(key, requirePlainValue(entry.getValue(), name + "." + key));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException(
                "property '" + name + "' has a non-plain value type " + value.getClass().getName()
                        + "; normalized declarations must be String/Number/Boolean/null/Map");
    }

    @Override
    public String toString() {
        return kind + "(" + targetId + properties + ")"
                + (scriptId == null ? "" : " from " + scriptId);
    }
}
