package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.EntityTypeBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class EntityTypeRegistryEventJS {
    private static final Map<Identifier, EntityType<? extends LivingEntity>> REGISTERED_ENTITY_TYPES = new HashMap<>();
    private static final Map<Identifier, AttributeSupplier> PENDING_ATTRIBUTES = new HashMap<>();
    /** 请求 spawn egg 的 builder（ENTITY_TYPE 分支填充，ITEM 分支消费注册 {@code <id>_spawn_egg}）。 */
    public static final Map<Identifier, EntityTypeBuilderJS> PENDING_SPAWN_EGGS = new HashMap<>();
    /** 已注册的 spawn egg item id（跨 reload 保留，客户端生成模型用）。 */
    private static final Set<Identifier> REGISTERED_SPAWN_EGGS = new HashSet<>();

    private final RegisterEvent rawEvent;
    private final List<EntityTypeBuilderJS> builders = new ArrayList<>();

    public EntityTypeRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public EntityTypeBuilderJS create(String id) {
        Identifier location = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("nekojs", id);
        EntityTypeBuilderJS builder = new EntityTypeBuilderJS(location);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<EntityTypeBuilderJS> consumer) {
        EntityTypeBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (EntityTypeBuilderJS builder : builders) {
            Identifier location = builder.getLocation();
            rawEvent.register(Registries.ENTITY_TYPE, location, () -> {
                EntityType<? extends LivingEntity> type = builder.createEntityType();
                REGISTERED_ENTITY_TYPES.put(location, type);
                PENDING_ATTRIBUTES.put(location, builder.createAttributes());
                if (builder.isSpawnEggRequested()) {
                    PENDING_SPAWN_EGGS.put(location, builder);
                    REGISTERED_SPAWN_EGGS.add(Identifier.fromNamespaceAndPath(
                            location.getNamespace(), location.getPath() + "_spawn_egg"));
                }
                return type;
            });
        }
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        PENDING_ATTRIBUTES.forEach((location, attributes) -> {
            EntityType<? extends LivingEntity> type = REGISTERED_ENTITY_TYPES.get(location);
            if (type != null) {
                event.put(type, attributes);
            }
        });
    }

    public static Iterable<EntityType<? extends LivingEntity>> registeredEntityTypes() {
        return REGISTERED_ENTITY_TYPES.values();
    }

    public static EntityType<? extends LivingEntity> getEntityType(Identifier location) {
        return REGISTERED_ENTITY_TYPES.get(location);
    }

    public static Set<Identifier> registeredSpawnEggs() {
        return REGISTERED_SPAWN_EGGS;
    }
}
