package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.entity.EntityAttributeConfig;
import com.tkisor.nekojs.wrapper.registry.EntityTypeBuilderJS;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Script-facing wrapper around {@link RegistryEvent.Register}{@code <EntityEntry>}.
 *
 * <p>Scripts create entities via {@link #create(String)} and {@link #registerAll()} flushes
 * them. The attribute config and goals collected on each builder are committed to their
 * respective static registries ({@link EntityAttributeConfig} / {@code GoalRegistry}) at
 * build time so that {@code NekoScriptMob} can resolve them by registry id later.
 */
public class EntityTypeRegistryEventJS {

    private final RegistryEvent.Register<EntityEntry> rawEvent;
    private final List<EntityTypeBuilderJS> builders = new ArrayList<>();

    public EntityTypeRegistryEventJS(RegistryEvent.Register<EntityEntry> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public EntityTypeBuilderJS create(String id) {
        EntityTypeBuilderJS builder = new EntityTypeBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<EntityTypeBuilderJS> consumer) {
        EntityTypeBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public IForgeRegistry<EntityEntry> getRegistry() {
        return rawEvent.getRegistry();
    }

    public void registerAll() {
        for (EntityTypeBuilderJS builder : builders) {
            ResourceLocation id = new ResourceLocation(builder.getName());
            // Commit attribute config + goals before the entry is built & registered, so
            // NekoScriptMob.initEntityAI / applyEntityAttributes can resolve them by id.
            EntityAttributeConfig.register(id, builder.getAttributeConfig());
            builder.registerGoals();
            rawEvent.getRegistry().register(builder.build());
        }
        builders.clear();
    }
}
