package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.entity.NekoScriptMob;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.Consumer;

public class EntityTypeBuilderJS {
    @Getter
    private final ResourceLocation location;

    private MobCategory category = MobCategory.CREATURE;
    private float width = 0.6F;
    private float height = 1.8F;
    private int trackingRange = 8;
    private int updateInterval = 3;
    private boolean receiveVelocityUpdates = true;
    private boolean fireImmune = false;
    private boolean noSave = false;
    private boolean noSummon = false;
    private final EntityAttributeBuilderJS attributes = new EntityAttributeBuilderJS();
    private final GoalRegistry.GoalBuilderJS goals = GoalRegistry.builder();

    public EntityTypeBuilderJS(ResourceLocation location) {
        this.location = location;
    }

    public EntityTypeBuilderJS category(String category) {
        this.category = switch (category.toLowerCase()) {
            case "monster", "hostile" -> MobCategory.MONSTER;
            case "ambient" -> MobCategory.AMBIENT;
            case "water_creature", "watercreature" -> MobCategory.WATER_CREATURE;
            case "water_ambient", "waterambient" -> MobCategory.WATER_AMBIENT;
            case "underground_water_creature", "undergroundwatercreature" -> MobCategory.UNDERGROUND_WATER_CREATURE;
            case "axolotls", "axolotl" -> MobCategory.AXOLOTLS;
            case "misc" -> MobCategory.MISC;
            default -> MobCategory.CREATURE;
        };
        return this;
    }

    public EntityTypeBuilderJS size(double width, double height) {
        this.width = (float) width;
        this.height = (float) height;
        return this;
    }

    public EntityTypeBuilderJS trackingRange(int trackingRange) {
        this.trackingRange = trackingRange;
        return this;
    }

    public EntityTypeBuilderJS updateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
        return this;
    }

    public EntityTypeBuilderJS receiveVelocityUpdates(boolean receiveVelocityUpdates) {
        this.receiveVelocityUpdates = receiveVelocityUpdates;
        return this;
    }

    public EntityTypeBuilderJS fireImmune() {
        this.fireImmune = true;
        return this;
    }

    public EntityTypeBuilderJS noSave() {
        this.noSave = true;
        return this;
    }

    public EntityTypeBuilderJS noSummon() {
        this.noSummon = true;
        return this;
    }

    public EntityTypeBuilderJS attributes(Consumer<EntityAttributeBuilderJS> consumer) {
        consumer.accept(attributes);
        return this;
    }

    public EntityTypeBuilderJS goals(Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        consumer.accept(goals);
        return this;
    }

    public AttributeSupplier createAttributes() {
        return attributes.build();
    }

    public EntityType<? extends PathfinderMob> createEntityType() {
        EntityType.Builder<NekoScriptMob> builder = EntityType.Builder.of(NekoScriptMob::new, category)
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .setShouldReceiveVelocityUpdates(receiveVelocityUpdates);

        if (fireImmune) {
            builder.fireImmune();
        }
        if (noSave) {
            builder.noSave();
        }
        if (noSummon) {
            builder.noSummon();
        }

        EntityType<NekoScriptMob> type = builder.build(location.toString());
        goals.forType(type).register();
        return type;
    }
}
