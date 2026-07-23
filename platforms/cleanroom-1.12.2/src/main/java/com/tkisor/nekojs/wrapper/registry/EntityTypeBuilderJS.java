package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.wrapper.entity.EntityAttributeConfig;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.entity.NekoScriptMob;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

import java.util.function.Consumer;

/**
 * 1.12.2 EntityTypeBuilderJS - builds {@link EntityEntry} objects from script definitions.
 *
 * <p>Wraps Forge's {@link EntityEntryBuilder}. By default the entity class is
 * {@link NekoScriptMob}; the script configures its attributes (via {@link #attributes}) and
 * its AI goals (via {@link #goals}). On {@link #build()} the attribute config and goal
 * builder are committed to their static registries (keyed by this entity's registry id) and
 * the {@link NekoScriptMob} instance resolves them at runtime through its {@code nekoId}.
 *
 * <p>For advanced use, {@link #entityClass(Class)} lets a script register a custom
 * concrete {@link Entity} subclass instead of {@code NekoScriptMob}.
 */
public class EntityTypeBuilderJS {
    private final String name;
    private Class<? extends Entity> entityClass = NekoScriptMob.class;
    private int trackingRange = 80;
    private int updateFrequency = 3;
    private boolean sendVelocityUpdates = true;
    private int eggPrimary = 0xFFFFFF;
    private int eggSecondary = 0x000000;

    /** Attribute configuration applied to NekoScriptMob instances of this entity. */
    private final EntityAttributeConfig attributes = new EntityAttributeConfig();

    /** Goal builder, pre-seeded with this entity's registry id. */
    private final GoalRegistry.GoalBuilderJS goals;

    /**
     * Next entity id. Starts at 1000 to avoid colliding with vanilla entity ids
     * (which occupy the low range); colliding ids crash the client on entity spawn.
     */
    private static int nextId = 1000;

    public EntityTypeBuilderJS(String name) {
        this.name = name;
        // Pre-seed the goal builder with this entity's resolved id, so the script can
        // call builder.swim()/wander(...) etc. without an explicit forType().
        this.goals = GoalRegistry.builder().forType(name);
    }

    public EntityTypeBuilderJS entityClass(Class<? extends Entity> clazz) {
        this.entityClass = clazz;
        return this;
    }

    public EntityTypeBuilderJS trackingRange(int range) {
        this.trackingRange = range;
        return this;
    }

    public EntityTypeBuilderJS updateFrequency(int freq) {
        this.updateFrequency = freq;
        return this;
    }

    public EntityTypeBuilderJS sendVelocityUpdates(boolean send) {
        this.sendVelocityUpdates = send;
        return this;
    }

    public EntityTypeBuilderJS eggColors(int primary, int secondary) {
        this.eggPrimary = primary;
        this.eggSecondary = secondary;
        return this;
    }

    public EntityTypeBuilderJS attributes(Consumer<EntityAttributeConfig> consumer) {
        consumer.accept(attributes);
        return this;
    }

    public EntityTypeBuilderJS goals(Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        consumer.accept(goals);
        return this;
    }

    public String getName() {
        return name;
    }

    public EntityAttributeConfig getAttributeConfig() {
        return attributes;
    }

    /** Commit collected goals into the GoalRegistry under this entity's id. */
    public void registerGoals() {
        GoalRegistry.registerForEntity(new ResourceLocation(name), goals);
    }

    /**
     * Build and register the entity entry.
     * Should be called during RegistryEvent.Register&lt;EntityEntry&gt;.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public EntityEntry build() {
        ResourceLocation id = new ResourceLocation(name);

        EntityEntryBuilder builder = EntityEntryBuilder.create()
                .entity(entityClass)
                .id(id, nextId++)
                .name(name)
                .tracker(trackingRange, updateFrequency, sendVelocityUpdates)
                .egg(eggPrimary, eggSecondary);

        // When using the shared NekoScriptMob class, inject the registry id through a
        // factory lambda so the mob can later resolve its attribute config + goals.
        if (entityClass == NekoScriptMob.class) {
            builder.factory((java.util.function.Function<net.minecraft.world.World, NekoScriptMob>)
                    world -> {
                        NekoScriptMob mob = new NekoScriptMob(world);
                        mob.setNekoId(id);
                        return mob;
                    });
        }

        return (EntityEntry) builder.build();
    }
}
