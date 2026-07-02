package com.tkisor.nekojs.api;

import java.util.List;

/**
 * 声明式描述一个 {@link JSTypeAdapter} 接受的输入形状。
 *
 * <p>适配器通过 {@link JSTypeAdapter#inputShapes()} 返回一组形状，probe 的
 * {@code AdapterAliasGenerator} 将其渲染成 TypeScript 输入别名（如 {@code $ItemStack_}），
 * 让脚本在期望 {@code ItemStack} 的地方也能直接传入字符串、对象字面量等。
 *
 * <p>本类型是纯数据，渲染逻辑由 probe 侧统一处理（需要知道目标类型与 import 收集）。
 * 用法示例：
 * <pre>{@code
 * @Override
 * public List<AdapterInputShape> inputShapes() {
 *     return List.of(
 *         self(),                          // $ItemStack
 *         string(),                        // "minecraft:apple"
 *         host(Item.class),                // $Item
 *         object(                          // { item|id, count? }
 *             Slot.opt("item", string()),
 *             Slot.opt("count", number()))
 *     );
 * }
 * }</pre>
 *
 * <p>推荐静态导入工厂方法：{@code import static ...AdapterInputShape.*;}。
 */
public sealed interface AdapterInputShape permits
        AdapterInputShape.StringValue,
        AdapterInputShape.NumberValue,
        AdapterInputShape.BooleanValue,
        AdapterInputShape.SelfValue,
        AdapterInputShape.HostValue,
        AdapterInputShape.ArrayOfValue,
        AdapterInputShape.ObjectValue,
        AdapterInputShape.RegistryValue,
        AdapterInputShape.RawValue {

    // ===================== 静态工厂 =====================

    static StringValue string() { return new StringValue(); }
    static NumberValue number() { return new NumberValue(); }
    static BooleanValue bool() { return new BooleanValue(); }
    static SelfValue self() { return new SelfValue(); }
    static HostValue host(Class<?> cls) { return new HostValue(cls); }
    static ArrayOfValue arrayOf(AdapterInputShape element) { return new ArrayOfValue(element); }
    static ObjectValue object(List<Slot> slots) { return new ObjectValue(slots); }
    static ObjectValue object(Slot... slots) { return new ObjectValue(List.of(slots)); }
    static RegistryValue registry(String typeName) { return new RegistryValue(typeName); }
    static RawValue raw(String ts) { return new RawValue(ts); }

    // ===================== 变体 =====================

    /** 字符串。 */
    record StringValue() implements AdapterInputShape {}
    /** 数字。 */
    record NumberValue() implements AdapterInputShape {}
    /** 布尔。 */
    record BooleanValue() implements AdapterInputShape {}
    /** 适配器自身的目标类型（渲染为 {@code $目标类型}）。 */
    record SelfValue() implements AdapterInputShape {}
    /** 另一个 Java 类（host object），渲染为 {@code $ClassName}，跨包时自动产生 import。 */
    record HostValue(Class<?> cls) implements AdapterInputShape {}
    /** 元素形状的数组。 */
    record ArrayOfValue(AdapterInputShape element) implements AdapterInputShape {}
    /** 对象字面量，由若干 {@link Slot} 组成。 */
    record ObjectValue(List<Slot> slots) implements AdapterInputShape {}
    /** 注册表字面量联合（复用 {@code @special} 的 {@code RegistryTypes.X}）。 */
    record RegistryValue(String typeName) implements AdapterInputShape {}
    /** 逃生舱：直接给出任意 TypeScript 片段。 */
    record RawValue(String ts) implements AdapterInputShape {}

    /**
     * 对象字面量的一个字段。
     *
     * @param name     字段名
     * @param shape    字段类型形状
     * @param required 是否必填（false 时类型后加 {@code ?}）
     */
    record Slot(String name, AdapterInputShape shape, boolean required) {
        public static Slot req(String name, AdapterInputShape shape) { return new Slot(name, shape, true); }
        public static Slot opt(String name, AdapterInputShape shape) { return new Slot(name, shape, false); }
    }
}
