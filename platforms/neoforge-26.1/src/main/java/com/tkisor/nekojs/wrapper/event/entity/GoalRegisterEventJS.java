package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import net.minecraft.world.entity.EntityType;

import java.util.function.Consumer;

public class GoalRegisterEventJS {
    public GoalRegistry.GoalBuilderJS forType(EntityType<?> type) {
        return GoalRegistry.builder().forType(type);
    }

    public GoalRegistry.GoalBuilderJS forType(String id) {
        return GoalRegistry.builder().forType(id);
    }

    public void forType(EntityType<?> type, Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        GoalRegistry.GoalBuilderJS builder = forType(type);
        consumer.accept(builder);
        builder.register();
    }

    public void forType(String id, Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        GoalRegistry.GoalBuilderJS builder = forType(id);
        consumer.accept(builder);
        builder.register();
    }
}
