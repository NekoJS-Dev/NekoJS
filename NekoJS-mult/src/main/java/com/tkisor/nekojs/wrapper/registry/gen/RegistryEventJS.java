package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * 平台无关注册事件（ADR-0004 三层解耦的脚本面）：由平台 adapter 在首个注册表
 * pass 前 post <b>一次</b>（收集阶段），脚本回调内经成员方法把 builder 攒进
 * {@link RegistryRepository}，对象创建推迟到各注册表 pass 的抽干期。
 * 零 loader import（Fabric 移植前提）。
 *
 * <h2>JS API</h2>
 * <pre>
 * RegistryEvents.register(event =&gt; {
 *     event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16 })    // default 类型糖方法
 *     event.item('mymod:sword', 'sword', b =&gt; { ... })           // 该注册表下的命名类型
 *     event.custom('mymod:x', 'some_type', b =&gt; { ... })        // 全局唯一类型名
 *     event.register('item', 'mymod:raw', () =&gt; someFactory())   // 裸 supplier 逃逸口
 * })
 * </pre>
 *
 * <p>糖方法名由 {@link RegistryInfo#sugarName()} 派生、经 {@link ProxyObject}
 * 动态成员暴露（「引擎生成，不手写」，ADR-0006）：{@code registry_types} 给某注册表
 * 登记 default 类型后糖方法即出现在成员目录里，第三方注册表同样免费获得。
 * 类型层冲突 overrideWarn（登记期裁决，见 {@link RegistryTypesPoint}）；
 * 对象层同 id 重复 fail-fast（仓库 add 期，见 {@link RegistryRepository}）。
 */
public final class RegistryEventJS implements ProxyObject {

    private final RegistryRepository repository;
    private final String node;
    private final RegistryTypesPoint.RegistryTypes types;
    /** 糖方法名 → 注册表键（camelCase 归并同名时首胜）。 */
    private final Map<String, ResourceKey<? extends Registry<?>>> bySugar;
    /** 完整键名（{@code minecraft:item}）→ 注册表键（event.register 的全名形态）。 */
    private final Map<String, ResourceKey<? extends Registry<?>>> byFullName;
    private final Map<String, ProxyExecutable> members;

    /** adapter 入口：runtime 由 adapter 持有（收集/校验/指纹/抽干 owner），类型表 / 元信息取 bootstrap 产物。 */
    public static RegistryEventJS create(RegistryRepository repository, String node) {
        return new RegistryEventJS(repository, node, NekoRegistryPointsPlugin.registryInfos(),
                NekoRegistryPointsPlugin.registryTypes());
    }

    RegistryEventJS(
            RegistryRepository repository,
            String node,
            RegistryInfosPoint.RegistryInfos infos,
            RegistryTypesPoint.RegistryTypes types) {
        this.repository = repository;
        this.node = node;
        this.types = types;
        this.bySugar = new LinkedHashMap<>();
        this.byFullName = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<? extends Registry<?>>, RegistryInfo> entry : infos.infos().entrySet()) {
            RegistryInfo info = entry.getValue();
            bySugar.putIfAbsent(info.sugarName(), info.key());
            byFullName.putIfAbsent(info.key().identifier().toString(), info.key());
        }
        this.members = new LinkedHashMap<>();
        members.put("custom", new CustomMember());
        members.put("register", new RegisterMember());
        bySugar.forEach((sugar, registry) -> members.put(sugar, new SugarMember(sugar, registry)));
    }

    // ---- ProxyObject：动态成员目录 ----

    @Override
    public Object getMember(String name) {
        ProxyExecutable member = members.get(name);
        if (member == null) {
            throw new IllegalArgumentException(
                    "RegistryEvent has no member '" + name + "'; known: " + memberDirectory());
        }
        return member;
    }

    @Override
    public boolean hasMember(String name) {
        return members.containsKey(name);
    }

    /** 成员目录：有 default 类型的糖方法 + custom + register（probe / 补全用）。 */
    @Override
    public Object getMemberKeys() {
        List<String> keys = new ArrayList<>();
        bySugar.forEach((sugar, registry) -> {
            if (types.defaultType(registry) != null) {
                keys.add(sugar);
            }
        });
        keys.add("custom");
        keys.add("register");
        return keys.toArray();
    }

    @Override
    public void putMember(String name, Value value) {
        throw new UnsupportedOperationException("RegistryEvent is read-only");
    }

    @Override
    public boolean removeMember(String name) {
        return false;
    }

    private String memberDirectory() {
        List<String> keys = new ArrayList<>();
        bySugar.forEach((sugar, registry) -> {
            if (types.defaultType(registry) != null) {
                keys.add(sugar);
            }
        });
        keys.add("custom");
        keys.add("register");
        return keys.toString();
    }

    // ---- 成员实现 ----

    /** 糖方法：{@code event.<sugar>(id, cb)}（default 类型）或 {@code event.<sugar>(id, typeName, cb)}。 */
    private final class SugarMember implements ProxyExecutable {

        private final String sugar;
        private final ResourceKey<? extends Registry<?>> registry;

        SugarMember(String sugar, ResourceKey<? extends Registry<?>> registry) {
            this.sugar = sugar;
            this.registry = registry;
        }

        @Override
        public Object execute(Value... args) {
            if (args.length != 2 && args.length != 3) {
                throw new IllegalArgumentException(
                        sugar + "(id, callback) or " + sugar + "(id, typeName, callback)");
            }
            Identifier id = parseId(args[0], sugar);
            String typeName = args.length == 3 ? stringArg(args[1], sugar + " typeName") : null;
            return BuilderSurface.of(addTyped(registry, id, typeName, args[args.length - 1], sugar));
        }
    }

    /** {@code event.custom(id, typeName, cb)}：按全局唯一类型名登记；跨注册表同名时报错并要求限定写法。 */
    private final class CustomMember implements ProxyExecutable {

        @Override
        public Object execute(Value... args) {
            if (args.length != 3) {
                throw new IllegalArgumentException("custom(id, typeName, callback)");
            }
            Identifier id = parseId(args[0], "custom");
            String typeName = stringArg(args[1], "custom typeName");
            List<ResourceKey<? extends Registry<?>>> owners = types.registriesOf(typeName);
            if (owners.isEmpty()) {
                throw new IllegalArgumentException("unknown type name '" + typeName + "' (searched every registry; node "
                        + node + ")");
            }
            if (owners.size() > 1) {
                throw new IllegalArgumentException("type name '" + typeName
                        + "' is registered under multiple registries; use event.<registry>(id, '" + typeName
                        + "', callback) to pick one (node " + node + ")");
            }
            return BuilderSurface.of(addTyped(owners.get(0), id, typeName, args[2], "custom"));
        }
    }

    /** {@code event.register(registry, id, supplier)}：裸 supplier 逃逸口，registry 为糖名或 {@code ns:path}。 */
    private final class RegisterMember implements ProxyExecutable {

        @Override
        public Object execute(Value... args) {
            if (args.length != 3) {
                throw new IllegalArgumentException(
                        "register(registry, id, supplier) — registry is a sugar name like 'item' or a full key like 'minecraft:item'");
            }
            ResourceKey<? extends Registry<?>> registry =
                    resolveRegistry(stringArg(args[0], "register registry"));
            Identifier id = parseId(args[1], "register");
            Value function = args[2];
            if (!function.canExecute()) {
                throw new IllegalArgumentException("register(...) expects a supplier function as the third argument");
            }
            RegistryObjectBuilder<Object> builder = new RegistryObjectBuilder<>(id) {
                @Override
                public Object build() {
                    return function.execute().as(Object.class);
                }
            };
            repository.add(registry, builder, "supplier", "register(" + name(registry) + ")");
            return BuilderSurface.of(builder);
        }
    }

    // ---- 共用解析 ----

    private RegistryObjectBuilder<?> addTyped(
            ResourceKey<? extends Registry<?>> registry, Identifier id, String typeName, Value callback, String what) {
        Function<Identifier, RegistryObjectBuilder<?>> factory =
                typeName == null ? defaultFactory(registry, what) : namedFactory(registry, typeName, what);
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException(what + " expects a builder callback as the last argument");
        }
        RegistryObjectBuilder<?> builder = factory.apply(id);
        // 脚本面拿到的是 BuilderSurface（ProxyObject）：property 赋值与显式 setter 同一写入点，
        // 不让裸宿主 builder 泄漏（无 public-field 旁路，AC3）
        callback.executeVoid(BuilderSurface.of(builder));
        repository.add(registry, builder,
                typeName == null ? "<default>" : typeName,
                what + ":" + name(registry));
        return builder;
    }

    private Function<Identifier, RegistryObjectBuilder<?>> defaultFactory(
            ResourceKey<? extends Registry<?>> registry, String what) {
        Function<Identifier, RegistryObjectBuilder<?>> factory = types.defaultType(registry);
        if (factory == null) {
            throw new IllegalArgumentException("registry '" + name(registry) + "' has no default type (from " + what
                    + "); pass one explicitly — known types: " + types.typeNames(registry) + " (node " + node + ")");
        }
        return factory;
    }

    private Function<Identifier, RegistryObjectBuilder<?>> namedFactory(
            ResourceKey<? extends Registry<?>> registry, String typeName, String what) {
        Function<Identifier, RegistryObjectBuilder<?>> factory = types.type(registry, typeName);
        if (factory == null) {
            throw new IllegalArgumentException("unknown type '" + typeName + "' for registry '" + name(registry)
                    + "'; known types: " + types.typeNames(registry) + " (node " + node + ")");
        }
        return factory;
    }

    private ResourceKey<? extends Registry<?>> resolveRegistry(String name) {
        ResourceKey<? extends Registry<?>> registry =
                name.indexOf(':') >= 0 ? byFullName.get(name) : bySugar.get(name);
        if (registry == null) {
            throw new IllegalArgumentException(
                    "unknown registry '" + name + "'; use a sugar name like 'item' or a full key like 'minecraft:item'"
                            + " (node " + node + ")");
        }
        return registry;
    }

    private static Identifier parseId(Value value, String what) {
        if (value.isString()) {
            return Identifier.parse(value.asString());
        }
        if (value.isHostObject()) {
            return value.as(Identifier.class);
        }
        throw new IllegalArgumentException(what + " expects an id string like 'mymod:thing'");
    }

    private static String stringArg(Value value, String what) {
        if (!value.isString()) {
            throw new IllegalArgumentException(what + " expects a string");
        }
        return value.asString();
    }

    private static String name(ResourceKey<? extends Registry<?>> registry) {
        return registry.identifier().toString();
    }
}
