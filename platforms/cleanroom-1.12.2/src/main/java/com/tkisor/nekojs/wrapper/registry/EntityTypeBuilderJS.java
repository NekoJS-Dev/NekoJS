package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
@Doc("Builder for registering a new entity type; obtain it from RegistryEvents.entityType.create(id).")
@Doc("Defaults to NekoScriptMob; configure attributes via attributes(cb) and AI via goals(cb).")
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

    /** Overrides the entity class (defaults to NekoScriptMob). */
    @Doc("Uses a custom entity class instead of NekoScriptMob.")
    @Param(name = "clazz", value = "a concrete Entity subclass")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS entityClass(Class<? extends Entity> clazz) {
        this.entityClass = clazz;
        return this;
    }

    /** Sets the client tracking range in blocks. */
    @Doc("Sets the client tracking range.")
    @Param(name = "range", value = "tracking range in blocks; default 80")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS trackingRange(int range) {
        this.trackingRange = range;
        return this;
    }

    /** Sets the sync update frequency in ticks. */
    @Doc("Sets how often the entity syncs to clients.")
    @Param(name = "freq", value = "update interval in ticks; default 3")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS updateFrequency(int freq) {
        this.updateFrequency = freq;
        return this;
    }

    /** Enables or disables velocity updates. */
    @Doc("Toggles velocity updates sent to clients.")
    @Param(name = "send", value = "true to send velocity updates; default true")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS sendVelocityUpdates(boolean send) {
        this.sendVelocityUpdates = send;
        return this;
    }

    /** Sets the spawn egg colors. */
    @Doc("Sets the spawn egg colors.")
    @Param(name = "primary", value = "primary egg color as 0xRRGGBB")
    @Param(name = "secondary", value = "secondary egg color as 0xRRGGBB")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS eggColors(int primary, int secondary) {
        this.eggPrimary = primary;
        this.eggSecondary = secondary;
        return this;
    }

    /** Configures the mob's attributes. */
    @Doc("Configures the entity's attributes (health, speed, attack, ...) via a callback.")
    @Param(name = "consumer", value = "callback receiving the EntityAttributeConfig")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS attributes(Consumer<EntityAttributeConfig> consumer) {
        consumer.accept(attributes);
        return this;
    }

    /** Configures the mob's AI goals. */
    @Doc("Configures the entity's AI goals and targets via a callback.")
    @Param(name = "consumer", value = "callback receiving the GoalBuilderJS pre-seeded with this entity's id")
    @Return("this builder, for chaining")
    public EntityTypeBuilderJS goals(Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        consumer.accept(goals);
        return this;
    }

    /** The entity name given at creation. */
    @Doc("Gets the entity registry name.")
    @Return("the registry name string")
    public String getName() {
        return name;
    }

    /** The attribute config collected so far. */
    @Doc("Gets the attribute config collected on this builder.")
    @Return("the shared EntityAttributeConfig instance")
    public EntityAttributeConfig getAttributeConfig() {
        return attributes;
    }

    /** Commit collected goals into the GoalRegistry under this entity's id. */
    @Doc("Commits the collected AI goals into the GoalRegistry under this entity's id.")
    @Doc("Called automatically when the registry event completes.")
    public void registerGoals() {
        GoalRegistry.registerForEntity(new ResourceLocation(name), goals);
    }

    /**
     * Build and register the entity entry.
     * Should be called during RegistryEvent.Register&lt;EntityEntry&gt;.
     */
    @Doc("Builds the Forge EntityEntry; registration happens when the event completes.")
    @Return("the entity entry configured with tracker settings, egg colors, and factory")
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

        return builder.build();
    }
}
