//? if neoforge {
package com.tkisor.nekojs.wrapper.event.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BaseCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

final class CapabilityRegistrationPlan {
    enum Scope { BLOCK, BLOCK_ENTITY, ENTITY, ITEM }
    record BlockQuery(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {}
    private enum Phase { COLLECTING, APPLYING, COMMITTED, FAILED }

    interface TargetLookup {
        Object resolve(Scope scope, Identifier id);
    }

    interface Registrar {
        void register(Registration registration);
    }

    record Registration(Scope scope, Identifier id, Object target,
                        BaseCapability<?, ?> capability, BiFunction<Object, Object, Object> provider) {}

    private final TargetLookup lookup;
    private final List<Registration> pending = new ArrayList<>();
    private volatile Phase phase = Phase.COLLECTING;

    CapabilityRegistrationPlan() {
        this(CapabilityRegistrationPlan::lookupNativeTarget);
    }

    CapabilityRegistrationPlan(TargetLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup);
    }

    void collect(Scope scope, String targetId, BaseCapability<?, ?> capability,
                 BiFunction<Object, Object, ?> provider) {
        requireCollecting();
        if (capability == null || provider == null) {
            throw new IllegalArgumentException("[NEKO-4020] Capability registration requires a native capability and provider");
        }
        if (!matchesScope(scope, capability)) {
            throw new IllegalArgumentException("[NEKO-4020] Native capability scope does not match " + scope);
        }
        Identifier id = parseTargetId(targetId);
        Object target = lookup.resolve(scope, id);
        if (target == null) {
            String code = scope == Scope.BLOCK_ENTITY ? "[NEKO-4006]" : "[NEKO-4019]";
            throw new IllegalArgumentException(code + " Unknown capability target: scope=" + scope + ", id=" + id);
        }
        if (pending.stream().anyMatch(existing -> existing.scope == scope && existing.target == target
                && existing.capability == capability)) {
            throw new IllegalArgumentException("[NEKO-4014] Duplicate capability provider: scope=" + scope
                    + ", id=" + id + ", capability=" + capability.name());
        }
        pending.add(new Registration(scope, id, target, capability,
                (owner, context) -> invoke(scope, id, capability, provider, owner, context)));
    }

    void apply(RegisterCapabilitiesEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("[NEKO-4020] Native capability registration event must not be null");
        }
        commit(registration -> install(event, registration));
    }

    void commit(Registrar registrar) {
        requireCollecting();
        Objects.requireNonNull(registrar);
        phase = Phase.APPLYING;
        try {
            for (Registration registration : pending) {
                registrar.register(registration);
            }
            phase = Phase.COMMITTED;
        } catch (RuntimeException | Error failure) {
            phase = Phase.FAILED;
            throw failure;
        } finally {
            pending.clear();
        }
    }

    private Object invoke(Scope scope, Identifier id, BaseCapability<?, ?> capability,
                          BiFunction<Object, Object, ?> provider, Object owner, Object context) {
        if (phase != Phase.COMMITTED) {
            throw new IllegalStateException("[NEKO-4011] Capability provider is unavailable because registration did not commit");
        }
        Class<?> contextType = capability.contextClass();
        if (context != null && !contextType.isInstance(context)) {
            throw new IllegalArgumentException("[NEKO-4020] Invalid native capability query context: capability="
                    + capability.name() + ", expected=" + contextType.getName());
        }
        Object value;
        try {
            value = provider.apply(owner, context);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("[NEKO-4009] Capability provider failed: scope=" + scope
                    + ", id=" + id + ", capability=" + capability.name(), failure);
        }
        if (value != null && !capability.typeClass().isInstance(value)) {
            throw new IllegalStateException("[NEKO-4010] Capability provider returned an incompatible value: scope="
                    + scope + ", id=" + id + ", capability=" + capability.name()
                    + ", expected=" + capability.typeClass().getName() + ", actual=" + value.getClass().getName());
        }
        return value;
    }

    private void requireCollecting() {
        if (phase != Phase.COLLECTING) {
            throw new IllegalStateException("[NEKO-4011] Capability registration is single-use: phase=" + phase);
        }
    }

    private static Identifier parseTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4019] Capability target id must not be blank");
        }
        Identifier id = Identifier.tryParse(targetId.trim());
        if (id == null) {
            throw new IllegalArgumentException("[NEKO-4019] Invalid capability target id: " + targetId);
        }
        return id;
    }

    private static boolean matchesScope(Scope scope, BaseCapability<?, ?> capability) {
        return switch (scope) {
            case BLOCK, BLOCK_ENTITY -> capability instanceof BlockCapability<?, ?>;
            case ENTITY -> capability instanceof EntityCapability<?, ?>;
            case ITEM -> capability instanceof ItemCapability<?, ?>;
        };
    }

    private static Object lookupNativeTarget(Scope scope, Identifier id) {
        return switch (scope) {
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id)
                    ? BuiltInRegistries.BLOCK.getOptional(id).orElse(null) : null;
            case BLOCK_ENTITY -> BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)
                    ? BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id).orElse(null) : null;
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null) : null;
            case ITEM -> BuiltInRegistries.ITEM.containsKey(id)
                    ? BuiltInRegistries.ITEM.getOptional(id).orElse(null) : null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void install(RegisterCapabilitiesEvent event, Registration registration) {
        switch (registration.scope) {
            case BLOCK -> event.registerBlock((BlockCapability) registration.capability,
                    (level, pos, state, blockEntity, context) -> registration.provider.apply(
                            new BlockQuery(level, pos, state, blockEntity), context), (Block) registration.target);
            case BLOCK_ENTITY -> event.registerBlockEntity((BlockCapability) registration.capability,
                    (BlockEntityType) registration.target, registration.provider::apply);
            case ENTITY -> event.registerEntity((EntityCapability) registration.capability,
                    (EntityType) registration.target, registration.provider::apply);
            case ITEM -> event.registerItem((ItemCapability) registration.capability,
                    registration.provider::apply, (Item) registration.target);
        }
    }
}
//?}
