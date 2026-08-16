package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.entity.EntityCreature;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * 1.12.2 script-driven mob. Extends {@link EntityCreature} (the closest 1.12.2 analogue
 * to the modern {@code PathfinderMob}).
 *
 * <p>All script-defined mobs share this single class; they are distinguished at runtime
 * by their {@code nekoId} (the registry id assigned via
 * {@code EntityEntryBuilder.factory(...)}). The nekoId drives two lookups:
 * <ul>
 *   <li>{@link #initEntityAI()} resolves registered goals from {@link GoalRegistry}.</li>
 *   <li>{@link #applyEntityAttributes()} resolves an {@link EntityAttributeConfig} and
 *       overlays its attribute values on the base attribute map.</li>
 * </ul>
 */
@Doc("Shared entity class behind every script-registered mob; behavior is driven by its nekoId.")
@Doc("AI goals come from GoalRegistry and attributes from EntityAttributeConfig, both keyed by the mob's registry id.")
public class NekoScriptMob extends EntityCreature {

    /** Registry id of this mob, set by the {@code EntityEntryBuilder.factory} lambda. */
    private ResourceLocation nekoId;

    /** Creates a mob in the given world. */
    public NekoScriptMob(World world) {
        super(world);
    }

    /**
     * Set by the factory lambda in EntityTypeBuilderJS.build() so the mob can resolve its
     * attribute config + goals. Public because the factory runs from a different package.
     */
    @Doc("Assigns the registry id used to resolve this mob's attributes and goals (internal).")
    @Param(name = "id", value = "the entity registry id assigned at spawn")
    public void setNekoId(ResourceLocation id) {
        this.nekoId = id;
    }

    /** The registry id of this mob instance. */
    @Doc("Gets the registry id of this mob instance.")
    @Return("the nekoId, or null before the factory lambda assigned it")
    public ResourceLocation getNekoId() {
        return nekoId;
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        GoalRegistry.applyBuiltInGoals(this);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntityAttributeConfig config = EntityAttributeConfig.get(nekoId);
        if (config != null) {
            config.applyTo(getAttributeMap());
        }
    }
}
