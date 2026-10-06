package com.tkisor.nekojs.wrapper.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoalRegistryApiTest {

    @Test
    void builderMustBeBoundToAnEntityBeforeRegistration() {
        GoalRegistry.GoalBuilderJS builder = GoalRegistry.builder();

        assertThrows(IllegalStateException.class, builder::register);
    }

    @Test
    void builtInGoalMethodsRemainFluent() {
        GoalRegistry.GoalBuilderJS builder = GoalRegistry.builder();

        assertSame(builder, builder.floatInWater(0));
        assertSame(builder, builder.randomStroll(5, 0.8));
        assertSame(builder, builder.meleeAttack(4, 1.2, false));
        assertSame(builder, builder.panic(1, 2.0));
        assertSame(builder, builder.target(2, "minecraft:zombie", true));
        assertSame(builder, builder.hurtByTarget(3));
        assertSame(builder, builder.lookAt(3, "minecraft:player", 8.0F));
        assertSame(builder, builder.avoid(3, "minecraft:player", 8.0F, 1.0));
    }

    @Test
    void foreignNamespaceDoesNotResolveToAVanillaMobWithTheSamePath() {
        assertThrows(IllegalArgumentException.class,
                () -> GoalRegistry.builder().target("demo:zombie"));
    }

    @Test
    void guestGoalFactoriesAreRejectedBeforeTheyCanOutliveTheirContext() {
        try (graal.graalvm.polyglot.Context context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowAllAccess(true).build()) {
            context.getBindings("js").putMember("builder", GoalRegistry.builder());
            graal.graalvm.polyglot.PolyglotException failure = assertThrows(
                    graal.graalvm.polyglot.PolyglotException.class,
                    () -> context.eval("js", "builder.custom(2, mob => null)"));
            org.junit.jupiter.api.Assertions.assertTrue(failure.isHostException());
            org.junit.jupiter.api.Assertions.assertTrue(failure.asHostException().getMessage().contains("NEKO-4017"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeGoalClassRemainsUsableAfterTheDeclaringScriptContextCloses() throws Exception {
        GoalRegistry.GoalBuilderJS builder = GoalRegistry.builder();
        try (graal.graalvm.polyglot.Context context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowAllAccess(true).build()) {
            context.getBindings("js").putMember("builder", builder);
            context.eval("js", "builder.customClass(2, Java.type('"
                    + StableGoal.class.getName() + "'))");
        }
        java.lang.reflect.Field collection = GoalRegistry.GoalBuilderJS.class.getDeclaredField("goals");
        collection.setAccessible(true);
        var factories = (java.util.List<GoalRegistry.GoalFactory>) collection.get(builder);
        org.junit.jupiter.api.Assertions.assertTrue(factories.get(0).create(null).canUse());
    }

    public static final class StableGoal extends net.minecraft.world.entity.ai.goal.Goal {
        public StableGoal(net.minecraft.world.entity.Mob owner) {}

        @Override
        public boolean canUse() {
            return true;
        }
    }
}
