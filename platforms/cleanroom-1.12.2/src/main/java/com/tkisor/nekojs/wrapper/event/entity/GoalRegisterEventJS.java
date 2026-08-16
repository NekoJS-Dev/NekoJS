package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;

import java.util.function.Consumer;

/**
 * Script-facing event object posted on the {@code GoalEvents.register} startup bus.
 *
 * <p>Scripts register entity goals by targeting an entity id and chaining goal builders:
 * <pre>{@code
 * GoalEvents.register.forType("minecraft:zombie", b => {
 *     b.goal().swim();
 *     b.target().hurtByTarget(true);
 *     b.register();   // not needed when using the Consumer overload
 * });
 * }</pre>
 */
@Doc("Startup event for attaching AI goals and targets to entity types by id.")
public class GoalRegisterEventJS {

    /** Returns a goal builder targeting the given entity id. */
    @Doc("Creates a goal builder for an entity type.")
    @Param(name = "entityId", value = "entity id like 'minecraft:zombie'")
    @Return("a GoalBuilderJS; call register() on it to commit the goals")
    public GoalRegistry.GoalBuilderJS forType(String entityId) {
        return GoalRegistry.builder().forType(entityId);
    }

    /** Creates a goal builder and registers it after configuration. */
    @Doc("Creates a goal builder for an entity type and registers it after configuration.")
    @Param(name = "entityId", value = "entity id like 'minecraft:zombie'")
    @Param(name = "consumer", value = "callback receiving the builder; register() is called automatically afterwards")
    public void forType(String entityId, Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        GoalRegistry.GoalBuilderJS builder = forType(entityId);
        consumer.accept(builder);
        builder.register();
    }
}
