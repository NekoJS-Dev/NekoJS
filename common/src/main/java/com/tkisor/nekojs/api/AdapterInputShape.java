package com.tkisor.nekojs.api;

import java.util.List;

/**
 * Declaratively describes an input shape accepted by a {@link JSTypeAdapter}.
 *
 * <p>Adapters return a set of shapes via {@link JSTypeAdapter#inputShapes()}. The probe's
 * {@code AdapterAliasGenerator} renders these into TypeScript input aliases (e.g., {@code $ItemStack_}),
 * allowing scripts to pass strings, object literals, etc., where an {@code ItemStack} is expected.
 *
 * <p>This type is pure data; rendering logic is handled uniformly by the probe (requiring knowledge
 * of the target type and import collection). Usage example:
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
 * <p>Static factory methods are recommended ({@code import static ...AdapterInputShape.*;}).
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
        AdapterInputShape.RawValue,
        AdapterInputShape.CallableValue,
        AdapterInputShape.NamedValue {

    // ===================== Static factories =====================

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
    /** Names a reusable part of an adapter input without changing its accepted values. */
    static NamedValue named(String suffix, AdapterInputShape shape) { return new NamedValue(suffix, shape); }
    /** A callback with required parameters and a typed result; parameter host types are not input-coerced. */
    static CallableValue callable(List<Slot> parameters, AdapterInputShape result) { return new CallableValue(parameters, result); }
    /** Literal template: {@code prefix + hole}, e.g., {@code template("#", registryTag("Item"))}. */
    static TemplateValue template(String prefix, AdapterInputShape hole) {
        return new TemplateValue(prefix, hole, "");
    }
    /** Literal template: {@code prefix + hole + suffix}, e.g., {@code template("/", string(), "/")}. */
    static TemplateValue template(String prefix, AdapterInputShape hole, String suffix) {
        return new TemplateValue(prefix, hole, suffix);
    }

    // ===================== Variants =====================

    /**
     * String.
     *
     * <p>Note: In TypeScript, if a plain {@code string} is present in a union, it absorbs all
     * string literal members, disabling IDE completions for those IDs. Do not use
     * {@code string()} for locations like "ID or prefixed syntax". Instead, use
     * {@link #registry(String)} + {@link #template(String, AdapterInputShape)} to list them
     * explicitly (see {@link TemplateValue}). Use {@code string()} only for free-text fields
     * (e.g., recipe IDs, mod IDs).
     */
    record StringValue() implements AdapterInputShape {}
    /** A single string literal, e.g., {@code literal("*")} renders as {@code "*"}. */
    record LiteralValue(String text) implements AdapterInputShape {}
    /** Number. */
    record NumberValue() implements AdapterInputShape {}
    /** Boolean. */
    record BooleanValue() implements AdapterInputShape {}
    /** Adapter's own target type (renders as {@code $TargetType}). */
    record SelfValue() implements AdapterInputShape {}
    /** Another Java class (host object); renders as {@code $ClassName}, generating imports automatically when crossing packages. */
    record HostValue(Class<?> cls) implements AdapterInputShape {}
    /** Array of element shapes. */
    record ArrayOfValue(AdapterInputShape element) implements AdapterInputShape {}
    /** Object literal consisting of multiple {@link Slot} entries. */
    record ObjectValue(List<Slot> slots) implements AdapterInputShape {}
    /**
     * Union of several shapes, rendered with parentheses as {@code (A | B)}.
     *
     * <p>Use it when one slot accepts several forms, for example a filter {@code output} that takes
     * either an item id or {@code #tag}. A top-level {@code inputShapes()} list is already a union,
     * so it needs no extra wrapper.
     */
    record UnionValue(List<AdapterInputShape> members) implements AdapterInputShape {
        public UnionValue {
            members = List.copyOf(members);
        }
    }
    /** Union of registry literals; reuses the {@code RegistryTypes.X} of {@code @special}. */
    record RegistryValue(String typeName) implements AdapterInputShape {}
    /**
     * Union of registry tag literals ({@code RegistryTypes.XTag}).
     *
     * <p>Scripts write tags in two forms: the string form with a prefix
     * ({@code "#minecraft:planks"}) and the object field form
     * ({@code { tag: "minecraft:planks" }}). This shape renders only the bare tag id; for the
     * prefixed form, wrap it with {@code template("#", registryTag("Item"))}.
     */
    record RegistryTagValue(String typeName) implements AdapterInputShape {}
    /**
     * Union of id namespace literals ({@code RegistryTypes.Namespace}).
     *
     * <p>It unions two sources: the loader mod list
     * ({@code NekoCatalogPlatformProvider.modIds()}) covers mods that are installed but register
     * nothing in a registry, and the {@code ':'} prefix of registry entry ids covers namespaces that
     * belong to no mod (a script calling {@code event.create('mymod:cool_gem')}, data packs, and so
     * on).
     *
     * <p>Namespaces are not split by registry: whether a namespace works does not depend on which
     * registry it appears in, and a filter that matches nothing returns an empty result instead of
     * an error. For the prefixed form ({@code "@create"}), use {@code template("@", namespace())}.
     */
    record NamespaceValue() implements AdapterInputShape {}
    /**
     * A TypeScript template literal type, used for string syntax that has a fixed prefix and
     * suffix around a completable id.
     *
     * <p>It renders as <code>`prefix${hole}suffix`</code>; for example
     * {@code template("#", registryTag("Item"))} renders as <code>`#${RegistryTypes.ItemTag}`</code>.
     * Unlike a bare {@code string}, a template type does not swallow the literal completions of the
     * other members of a union, so id completion still works next to syntax such as {@code @mod} or
     * {@code /regex}.
     */
    record TemplateValue(String prefix, AdapterInputShape hole, String suffix) implements AdapterInputShape {}
    /** Escape hatch: emits an arbitrary TypeScript snippet. */
    record RawValue(String ts) implements AdapterInputShape {}

    /** Typed callbacks with fixed, required parameters, shared by TypeScript and Python declarations. */
    record CallableValue(List<Slot> parameters, AdapterInputShape result) implements AdapterInputShape {
        public CallableValue {
            parameters = List.copyOf(parameters);
            java.util.Objects.requireNonNull(result, "result");
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Slot parameter : parameters) {
                if (!parameter.required() || parameter.name() == null || !parameter.name().matches("[A-Za-z_$][A-Za-z0-9_$]*")
                        || !names.add(parameter.name())) {
                    throw new IllegalArgumentException("[NEKO-4063] Callable input shapes require uniquely named, required parameters.");
                }
                java.util.Objects.requireNonNull(parameter.shape(), "parameter shape");
            }
        }
    }

    /**
     * A presentation-only input section. TypeScript prefixes its suffix with the adapter target name;
     * renderers without an adapter owner inline the wrapped shape. Repeated suffixes must resolve
     * to the same type; conflicting host/input positions or definitions are rejected.
     */
    record NamedValue(String suffix, AdapterInputShape shape) implements AdapterInputShape {
        public NamedValue {
            if (suffix == null || !suffix.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                throw new IllegalArgumentException("[NEKO-4063] Named input shapes require an identifier suffix.");
            }
            java.util.Objects.requireNonNull(shape, "shape");
        }
    }

    /**
     * One field of an object literal.
     *
     * @param name     field name
     * @param shape    field type shape
     * @param required whether the field is required ({@code ?} is appended to the type when false)
     */
    record Slot(String name, AdapterInputShape shape, boolean required) {
        public static Slot req(String name, AdapterInputShape shape) { return new Slot(name, shape, true); }
        public static Slot opt(String name, AdapterInputShape shape) { return new Slot(name, shape, false); }
    }
}
