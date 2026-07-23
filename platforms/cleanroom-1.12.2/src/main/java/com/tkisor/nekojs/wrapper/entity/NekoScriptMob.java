package com.tkisor.nekojs.wrapper.entity;

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
public class NekoScriptMob extends EntityCreature {

    /** Registry id of this mob, set by the {@code EntityEntryBuilder.factory} lambda. */
    private ResourceLocation nekoId;

    public NekoScriptMob(World world) {
        super(world);
    }

    /**
     * Set by the factory lambda in EntityTypeBuilderJS.build() so the mob can resolve its
     * attribute config + goals. Public because the factory runs from a different package.
     */
    public void setNekoId(ResourceLocation id) {
        this.nekoId = id;
    }

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
