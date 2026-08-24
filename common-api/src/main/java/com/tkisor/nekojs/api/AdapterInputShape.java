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
        AdapterInputShape.LiteralValue,
        AdapterInputShape.NumberValue,
        AdapterInputShape.BooleanValue,
        AdapterInputShape.SelfValue,
        AdapterInputShape.HostValue,
        AdapterInputShape.ArrayOfValue,
        AdapterInputShape.ObjectValue,
        AdapterInputShape.UnionValue,
        AdapterInputShape.RegistryValue,
        AdapterInputShape.RegistryTagValue,
        AdapterInputShape.NamespaceValue,
        AdapterInputShape.TemplateValue,
        AdapterInputShape.RawValue {

    // ===================== 静态工厂 =====================

    static StringValue string() { return new StringValue(); }
    static LiteralValue literal(String text) { return new LiteralValue(text); }
    static NumberValue number() { return new NumberValue(); }
    static BooleanValue bool() { return new BooleanValue(); }
    static SelfValue self() { return new SelfValue(); }
    static HostValue host(Class<?> cls) { return new HostValue(cls); }
    static ArrayOfValue arrayOf(AdapterInputShape element) { return new ArrayOfValue(element); }
    static ObjectValue object(List<Slot> slots) { return new ObjectValue(slots); }
    static ObjectValue object(Slot... slots) { return new ObjectValue(List.of(slots)); }
    static UnionValue union(AdapterInputShape... members) { return new UnionValue(List.of(members)); }
    static RegistryValue registry(String typeName) { return new RegistryValue(typeName); }
    static RegistryTagValue registryTag(String typeName) { return new RegistryTagValue(typeName); }
    static NamespaceValue namespace() { return new NamespaceValue(); }
    static RawValue raw(String ts) { return new RawValue(ts); }
    /** 字面量模板：{@code prefix + hole}，如 {@code template("#", registryTag("Item"))}。 */
    static TemplateValue template(String prefix, AdapterInputShape hole) {
        return new TemplateValue(prefix, hole, "");
    }
    /** 字面量模板：{@code prefix + hole + suffix}，如 {@code template("/", string(), "/")}。 */
    static TemplateValue template(String prefix, AdapterInputShape hole, String suffix) {
        return new TemplateValue(prefix, hole, suffix);
    }

    // ===================== 变体 =====================

    /**
     * 字符串。
     *
     * <p>注意：TypeScript 一旦在同一个联合里看到裸 {@code string}，就会把该联合里的字符串
     * 字面量成员吸收掉，编辑器不再给任何 id 补全。所以「id 或若干前缀语法」这种位置不要用
     * {@code string()}，用 {@link #registry(String)} + {@link #template(String, AdapterInputShape)}
     * 逐个列出（见 {@link TemplateValue}）。自由文本字段（配方 id、mod id）才用 {@code string()}。
     */
    record StringValue() implements AdapterInputShape {}
    /** 单个字符串字面量，如 {@code literal("*")} 渲染成 {@code "*"}。 */
    record LiteralValue(String text) implements AdapterInputShape {}
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
    /**
     * 若干形状的联合，渲染成带括号的 {@code (A | B)}。
     *
     * <p>用于单个槽位需要多种写法的场合，例如过滤器的 {@code output} 既接受物品 id
     * 也接受 {@code #标签}。顶层的 {@code inputShapes()} 列表本身已经是联合，无需再包一层。
     */
    record UnionValue(List<AdapterInputShape> members) implements AdapterInputShape {
        public UnionValue {
            members = List.copyOf(members);
        }
    }
    /** 注册表字面量联合（复用 {@code @special} 的 {@code RegistryTypes.X}）。 */
    record RegistryValue(String typeName) implements AdapterInputShape {}
    /**
     * 注册表标签字面量联合（{@code RegistryTypes.XTag}）。
     *
     * <p>标签在脚本里有两种写法：字符串前缀式（{@code "#minecraft:planks"}）与对象字段式
     * （{@code { tag: "minecraft:planks" }}）。本形状只渲染裸标签 id，前缀式请用
     * {@code template("#", registryTag("Item"))} 包一层。
     */
    record RegistryTagValue(String typeName) implements AdapterInputShape {}
    /**
     * id 命名空间的字面量联合（{@code RegistryTypes.Namespace}）。
     *
     * <p>两个数据源取并集：加载器的 mod 列表（{@code NekoCatalogPlatformProvider.modIds()}）
     * 覆盖「装了但没往某个注册表注册东西」的 mod；注册表条目 id 的 {@code ':'} 前缀覆盖不属于
     * 任何 mod 的命名空间（脚本 {@code event.create('mymod:cool_gem')}、数据包等）。
     *
     * <p>不按注册表分：一个命名空间能不能用与它出现在哪个注册表无关，过滤不到东西只是空结果，
     * 不是错误。前缀式写法（{@code "@create"}）用 {@code template("@", namespace())}。
     */
    record NamespaceValue() implements AdapterInputShape {}
    /**
     * TypeScript 模板字面量类型，用于「固定前后缀 + 可补全 id」的字符串语法。
     *
     * <p>渲染成 <code>`prefix${hole}suffix`</code>，例如 {@code template("#", registryTag("Item"))}
     * 得到 <code>`#${RegistryTypes.ItemTag}`</code>。相比裸 {@code string}，模板类型不会吞掉联合里
     * 其它成员的字面量补全，因此可以在保留 id 补全的同时接纳 {@code @mod} / {@code /regex} 这类语法。
     */
    record TemplateValue(String prefix, AdapterInputShape hole, String suffix) implements AdapterInputShape {}
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
