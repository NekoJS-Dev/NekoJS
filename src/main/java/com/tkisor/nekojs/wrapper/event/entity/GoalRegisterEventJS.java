package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import net.minecraft.world.entity.EntityType;

import java.util.function.Consumer;

/**
 * Event object for {@code GoalEvents.register} (startup scripts): registers the built-in AI
 * goals of an entity type.
 */
@Doc("Event given to GoalEvents.register callbacks (startup scripts) for registering built-in AI goals on entity types.")
public class GoalRegisterEventJS {
    /** Starts a goal builder for the given entity type; call {@code register()} yourself to apply it. */
    @Doc("Starts a goal builder for the given entity type; call builder.register() to apply.")
    @Param(name = "type", value = "the entity type to register goals for")
    @Return("a GoalBuilderJS bound to the entity type; not registered until register() is called")
    public GoalRegistry.GoalBuilderJS forType(EntityType<?> type) {
        return GoalRegistry.builder().forType(type);
    }

    /** Starts a goal builder for the entity type with the given id; call {@code register()} yourself to apply it. */
    @Doc("Starts a goal builder for the entity type with the given id; call builder.register() to apply.")
    @Param(name = "id", value = "entity type id like 'minecraft:zombie'")
    @Return("a GoalBuilderJS bound to the entity type; not registered until register() is called")
    public GoalRegistry.GoalBuilderJS forType(String id) {
        return GoalRegistry.builder().forType(id);
    }

    /** Registers goals on an entity type through a callback; {@code register()} runs after the callback returns. */
    @Doc("Registers goals on the given entity type via a builder callback; register() runs automatically afterwards.")
    @Param(name = "type", value = "the entity type to register goals for")
    @Param(name = "consumer", value = "callback receiving the GoalBuilderJS to configure")
    public void forType(EntityType<?> type, Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        GoalRegistry.GoalBuilderJS builder = forType(type);
        consumer.accept(builder);
        builder.register();
    }

    /** Registers goals on an entity type id through a callback; {@code register()} runs after the callback returns. */
    @Doc("Registers goals on the entity type with the given id via a builder callback; register() runs automatically afterwards.")
    @Param(name = "id", value = "entity type id like 'minecraft:zombie'")
    @Param(name = "consumer", value = "callback receiving the GoalBuilderJS to configure")
    public void forType(String id, Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        GoalRegistry.GoalBuilderJS builder = forType(id);
        consumer.accept(builder);
        builder.register();
    }
}
