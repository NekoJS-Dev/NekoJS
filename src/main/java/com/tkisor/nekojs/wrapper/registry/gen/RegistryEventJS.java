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
 * Platform-agnostic registration event (ADR-0004 three-layer decoupling script surface):
 * Posted <b>once</b> by the platform adapter before the first registry pass (collection phase).
 * Inside the script callback, builder objects are staged into the {@link RegistryRepository}
 * via member methods, deferring object creation until the drain phase of each registry pass.
 * Zero loader imports (prerequisite for Fabric porting).
 *
 * <h2>JS API</h2>
 * <pre>
 * RegistryEvents.register(event =&gt; {
 *     event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16 })    // Default type sugar method
 *     event.item('mymod:sword', 'sword', b =&gt; { ... })           // Named type under this registry
 *     event.custom('mymod:x', 'some_type', b =&gt; { ... })        // Globally unique type name
 *     event.register('item', 'mymod:raw', () =&gt; someFactory())   // Raw supplier escape hatch
 * })
 * </pre>
 *
 * <p>Sugar method names are derived from {@link RegistryInfo#sugarName()} and exposed via
 * {@link ProxyObject} dynamic members ("engine-generated, not handwritten", ADR-0006):
 * sugar methods appear in the member catalog once a default type is registered for a
 * registry via {@code registry_types}, and third-party registries get them for free.
 * Type-level conflicts overrideWarn (adjudicated during registration, see {@link RegistryTypesPoint});
 * object-level duplicate IDs fail-fast (during repository add phase, see {@link RegistryRepository}).
 */
public final class RegistryEventJS implements ProxyObject {

    private final RegistryRepository repository;
    private final String node;
    private final RegistryTypesPoint.RegistryTypes types;
    /** Sugar method name → registry key (first-win when merging same-name camelCase). */
    private final Map<String, ResourceKey<? extends Registry<?>>> bySugar;
    /** Full key name ({@code minecraft:item}) → registry key (full-name form for event.register). */
    private final Map<String, ResourceKey<? extends Registry<?>>> byFullName;
    private final Map<String, ProxyExecutable> members;

    /** Adapter entry point: runtime held by adapter (collector/validator/fingerprinter/drain owner), type table / meta-info taken from bootstrap products. */
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

    // ---- ProxyObject: dynamic member catalogue ----

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

    /** Member catalog: sugar methods with default types + custom + register (for probe/completion). */
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

    // ---- Member implementations ----

    /** Sugar method: {@code event.<sugar>(id, cb)} for the default type, or
     * {@code event.<sugar>(id, typeName, cb)}. */
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

    /** {@code event.custom(id, typeName, cb)} registers by globally unique type name; the same name in
     * more than one registry is an error and asks for the qualified form. */
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

    /** {@code event.register(registry, id, supplier)}: escape hatch for a bare supplier; registry is a
     * sugar name or a full {@code ns:path} key. */
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

    // ---- Shared parsing ----

    private RegistryObjectBuilder<?> addTyped(
            ResourceKey<? extends Registry<?>> registry, Identifier id, String typeName, Value callback, String what) {
        Function<Identifier, RegistryObjectBuilder<?>> factory =
                typeName == null ? defaultFactory(registry, what) : namedFactory(registry, typeName, what);
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException(what + " expects a builder callback as the last argument");
        }
        RegistryObjectBuilder<?> builder = factory.apply(id);
        // The script side gets a BuilderSurface (ProxyObject): property assignment and an explicit
        // setter write through the same point, so the raw host builder never leaks (no public-field
        // bypass).
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
