package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** Stages STARTUP definitions for one registry, using the shared repository for the epoch. */
public final class RegistryEventJS implements ProxyObject {

    private final RegistryRepository repository;
    private final String node;
    private final ResourceKey<? extends Registry<?>> registry;
    private final RegistryTypesPoint.RegistryTypes types;
    private final boolean hasFactories;
    private final ProxyExecutable create = this::createEntry;
    private final ProxyExecutable createCustom = this::createCustomEntry;

    RegistryEventJS(
            RegistryRepository repository,
            String node,
            ResourceKey<? extends Registry<?>> registry,
            RegistryTypesPoint.RegistryTypes types) {
        this.repository = repository;
        this.node = node;
        this.registry = registry;
        this.types = types;
        this.hasFactories = !types.typeNames(registry).isEmpty();
    }

    String registryName() {
        return registry.identifier().toString();
    }

    @Override
    public Object getMember(String name) {
        return switch (name) {
            case "create" -> hasFactories ? create : null;
            case "createCustom" -> createCustom;
            default -> null;
        };
    }

    @Override
    public boolean hasMember(String name) {
        return getMember(name) != null;
    }

    @Override
    public Object getMemberKeys() {
        return hasFactories ? new String[]{"create", "createCustom"} : new String[]{"createCustom"};
    }

    @Override
    public void putMember(String name, Value value) {
        throw new UnsupportedOperationException("[NEKO-4075] The STARTUP registry event is read-only");
    }

    @Override
    public boolean removeMember(String name) {
        return false;
    }

    private Object createEntry(Value... args) {
        if (args.length != 2 && args.length != 3) {
            throw new IllegalArgumentException("[NEKO-4075] Expected create(id, callback) or create(id, typeName, callback)");
        }
        Identifier id = parseId(args[0], "create");
        String typeName = null;
        if (args.length == 3) {
            if (!args[1].isString()) {
                throw new IllegalArgumentException("[NEKO-4075] create expects a string type name");
            }
            typeName = args[1].asString();
        }
        Value callback = args[args.length - 1];
        if (!callback.canExecute()) {
            throw new IllegalArgumentException("[NEKO-4075] create expects a builder callback as the last argument");
        }
        RegistryObjectBuilder<?> builder = factory(typeName).apply(id);
        // Pass only the script surface to configuration. Staging freezes the definition after it succeeds.
        callback.executeVoid(BuilderSurface.of(builder));
        repository.add(registry, builder, typeName == null ? "<default>" : typeName, "create:" + registryName());
        return BuilderSurface.of(builder);
    }

    private Object createCustomEntry(Value... args) {
        if (args.length != 2 || !args[1].canExecute()) {
            throw new IllegalArgumentException("[NEKO-4075] Expected createCustom(id, supplier) with a supplier function");
        }
        Identifier id = parseId(args[0], "createCustom");
        Value supplier = args[1];
        RegistryObjectBuilder<Object> builder = new RegistryObjectBuilder<>(id) {
            @Override
            public Object build() {
                return supplier.execute().as(Object.class);
            }
        };
        repository.add(registry, builder, "supplier", "createCustom:" + registryName());
        return BuilderSurface.of(builder);
    }

    private Function<Identifier, RegistryObjectBuilder<?>> factory(String typeName) {
        Function<Identifier, RegistryObjectBuilder<?>> factory =
                typeName == null ? types.defaultType(registry) : types.type(registry, typeName);
        if (factory == null) {
            String reason = typeName == null ? "has no default type. Pass one explicitly" : "has no type '" + typeName + "'";
            throw new IllegalArgumentException("[NEKO-4076] Registry '" + registryName() + "' " + reason
                    + " (node " + node + ", known types " + types.typeNames(registry) + ")");
        }
        return factory;
    }

    private static Identifier parseId(Value value, String method) {
        if (value.isString()) {
            return Identifier.parse(value.asString());
        }
        if (value.isHostObject()) {
            return value.as(Identifier.class);
        }
        throw new IllegalArgumentException("[NEKO-4075] " + method + " expects an id string like 'mymod:thing'");
    }
}
