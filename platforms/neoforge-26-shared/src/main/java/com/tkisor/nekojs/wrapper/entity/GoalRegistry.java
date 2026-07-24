package com.tkisor.nekojs.wrapper.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class GoalRegistry {
    private static final Map<EntityType<?>, List<GoalFactory>> GOALS = new HashMap<>();
    private static final Set<Mob> APPLIED_JOIN_GOALS = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private GoalRegistry() {}

    public static GoalBuilderJS builder() {
        return new GoalBuilderJS();
    }

    public static void register(EntityType<?> type, List<GoalFactory> goals) {
        if (type == null || goals.isEmpty()) {
            return;
        }
        GOALS.computeIfAbsent(type, ignored -> new ArrayList<>()).addAll(goals);
    }

    public static void applyBuiltInGoals(Mob mob) {
        List<GoalFactory> goals = GOALS.get(mob.getType());
        if (goals == null) {
            return;
        }
        apply(mob, goals);
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || mob instanceof NekoScriptMob) {
            return;
        }
        if (!APPLIED_JOIN_GOALS.add(mob)) {
            return;
        }
        applyBuiltInGoals(mob);
    }

    private static void apply(Mob mob, List<GoalFactory> goals) {
        for (GoalFactory factory : goals) {
            Goal goal = factory.create(mob);
            if (goal != null) {
                mob.goalSelector.addGoal(factory.priority(), goal);
            }
        }
    }

    public record GoalFactory(int priority, Function<Mob, Goal> factory) {
        Goal create(Mob mob) {
            return factory.apply(mob);
        }
    }

    public static class GoalBuilderJS {
        private EntityType<?> type;
        private final List<GoalFactory> goals = new ArrayList<>();

        public GoalBuilderJS forType(EntityType<?> type) {
            this.type = type;
            return this;
        }

        public GoalBuilderJS forType(String id) {
            Identifier location = id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("minecraft", id);
            this.type = BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElseThrow(() -> new IllegalArgumentException("Unknown entity type: " + id));
            return this;
        }

        public GoalBuilderJS floatInWater() {
            return floatInWater(0);
        }

        public GoalBuilderJS floatInWater(int priority) {
            goals.add(new GoalFactory(priority, mob -> new FloatGoal(mob)));
            return this;
        }

        public GoalBuilderJS randomStroll(double speed) {
            return randomStroll(5, speed);
        }

        public GoalBuilderJS randomStroll(int priority, double speed) {
            goals.add(new GoalFactory(priority, mob -> mob instanceof PathfinderMob pathfinderMob ? new RandomStrollGoal(pathfinderMob, speed) : null));
            return this;
        }

        public GoalBuilderJS meleeAttack(double speed, boolean longMemory) {
            return meleeAttack(4, speed, longMemory);
        }

        public GoalBuilderJS meleeAttack(int priority, double speed, boolean longMemory) {
            goals.add(new GoalFactory(priority, mob -> mob instanceof PathfinderMob pathfinderMob ? new MeleeAttackGoal(pathfinderMob, speed, longMemory) : null));
            return this;
        }

        public GoalBuilderJS panic(double speed) {
            return panic(1, speed);
        }

        public GoalBuilderJS panic(int priority, double speed) {
            goals.add(new GoalFactory(priority, mob -> mob instanceof PathfinderMob pathfinderMob ? new PanicGoal(pathfinderMob, speed) : null));
            return this;
        }

        public void register() {
            if (type == null) {
                throw new IllegalStateException("Goal builder requires forType(...) before register()");
            }
            GoalRegistry.register(type, goals);
        }
    }
}
