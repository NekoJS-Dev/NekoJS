package com.tkisor.nekojs.wrapper.event.entity;

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
public class GoalRegisterEventJS {

    public GoalRegistry.GoalBuilderJS forType(String entityId) {
        return GoalRegistry.builder().forType(entityId);
    }

    public void forType(String entityId, Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        GoalRegistry.GoalBuilderJS builder = forType(entityId);
        consumer.accept(builder);
        builder.register();
    }
}
