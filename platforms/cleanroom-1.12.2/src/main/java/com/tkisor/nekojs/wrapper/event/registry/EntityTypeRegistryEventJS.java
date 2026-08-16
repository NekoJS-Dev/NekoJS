package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** Wraps the raw Forge Register<EntityEntry> event. */
    public EntityTypeRegistryEventJS(RegistryEvent.Register<EntityEntry> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** Creates an entity type builder. */
    @Doc("Creates a new entity type builder.")
    @Param(name = "id", value = "registry id like 'my_mob' or 'mymod:my_mob'")
    @Return("a new EntityTypeBuilderJS for chaining; the entity is registered when the event completes")
    public EntityTypeBuilderJS create(String id) {
        EntityTypeBuilderJS builder = new EntityTypeBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** Creates an entity type builder and configures it in one call. */
    @Doc("Creates a new entity type builder and configures it in one call.")
    @Param(name = "id", value = "registry id like 'my_mob' or 'mymod:my_mob'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<EntityTypeBuilderJS> consumer) {
        EntityTypeBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** Exposes the raw Forge entity entry registry. */
    @Doc("Exposes the raw Forge entity entry registry for advanced use.")
    @Return("the Forge IForgeRegistry<EntityEntry> backing this event")
    public IForgeRegistry<EntityEntry> getRegistry() {
        return rawEvent.getRegistry();
    }

    /** Registers all entity entries and commits their attribute configs and goals. */
    @Doc("Registers all entity types created in this event, committing attribute configs and AI goals.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
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
