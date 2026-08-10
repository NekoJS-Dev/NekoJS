package com.tkisor.nekojs.wrapper.entity;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAIPanic;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 1.12.2 entity goal (AI) registry.
 *
 * <p>Unlike the 1.20+ neoforge variant which keys goals off {@code EntityType<?>}, this
 * implementation keys goals off the entity's registry {@link ResourceLocation}. This is
 * deliberate: every script-defined mob shares the single {@link NekoScriptMob} class, so
 * keying on {@code Class} would cause cross-mob collisions. The {@code ResourceLocation}
 * is unique per registered entity.
 *
 * <p>Goals registered here are applied in two flows:
 * <ul>
 *   <li>{@link NekoScriptMob#initEntityAI()} calls {@link #applyBuiltInGoals(NekoScriptMob)}
 *       which looks up goals by the mob's {@code nekoId}.</li>
 *   <li>{@link #onEntityJoinWorld(EntityJoinWorldEvent)} injects goals into vanilla
 *       {@link EntityCreature}s whose class resolves (via {@link EntityRegistry#getEntry})
 *       to an id with registered goals. A {@link WeakHashMap}-backed set prevents
 *       duplicate injection across chunk reloads.</li>
 * </ul>
 */
public final class GoalRegistry {

    /** Goals keyed by entity registry id. */
    private static final Map<ResourceLocation, List<GoalFactory>> GOALS = new ConcurrentHashMap<>();
    /** Targeting goals keyed by entity registry id (added to {@code mob.targetTasks}). */
    private static final Map<ResourceLocation, List<GoalFactory>> TARGET_GOALS = new ConcurrentHashMap<>();
    /**
     * Set of entities that have already had join-time goals applied. Backed by a
     * {@link WeakHashMap} so entries are reclaimed once the entity is GC'd, avoiding
     * unbounded retention across repeated chunk reloads / world transitions.
     */
    private static final Set<EntityCreature> APPLIED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private GoalRegistry() {}

    public static GoalBuilderJS builder() {
        return new GoalBuilderJS();
    }

    /** Persist a builder's collected goals for the given entity id. */
    public static void registerForEntity(ResourceLocation id, GoalBuilderJS builder) {
        if (id == null || builder == null) {
            return;
        }
        if (!builder.goalsList().isEmpty()) {
            GOALS.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(builder.goalsList());
        }
        if (!builder.targetGoalsList().isEmpty()) {
            TARGET_GOALS.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(builder.targetGoalsList());
        }
    }

    /** Apply registered goals to a NekoScriptMob by its nekoId (called from initEntityAI). */
    public static void applyBuiltInGoals(NekoScriptMob mob) {
        ResourceLocation id = mob.getNekoId();
        if (id == null) {
            return;
        }
        apply(mob, GOALS.get(id), false);
        apply(mob, TARGET_GOALS.get(id), true);
    }

    /**
     * Forge event hook: inject script-registered goals into vanilla {@link EntityCreature}s.
     *
     * <p>Skipped on the client, skipped for {@link NekoScriptMob} (those get goals via
     * {@link #applyBuiltInGoals}), and skipped for entities already processed.
     */
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }
        if (!(event.getEntity() instanceof EntityCreature)) {
            return;
        }
        EntityCreature mob = (EntityCreature) event.getEntity();
        if (mob instanceof NekoScriptMob) {
            return;
        }
        if (!APPLIED.add(mob)) {
            return;
        }
        EntityEntry entry = EntityRegistry.getEntry(mob.getClass());
        if (entry == null) {
            return;
        }
        ResourceLocation id = entry.getRegistryName();
        if (id == null) {
            return;
        }
        List<GoalFactory> goals = GOALS.get(id);
        List<GoalFactory> targets = TARGET_GOALS.get(id);
        if ((goals == null || goals.isEmpty()) && (targets == null || targets.isEmpty())) {
            return;
        }
        apply(mob, goals, false);
        apply(mob, targets, true);
    }

    private static void apply(EntityCreature mob, List<GoalFactory> list, boolean target) {
        if (list == null || list.isEmpty()) {
            return;
        }
        EntityAITasks dest = target ? mob.targetTasks : mob.tasks;
        for (GoalFactory factory : list) {
            EntityAIBase ai = factory.create(mob);
            if (ai != null) {
                dest.addTask(factory.priority(), ai);
            }
        }
    }

    /** A single goal definition: a priority plus a factory that produces the AI task. */
    public record GoalFactory(int priority, Function<EntityCreature, EntityAIBase> factory) {
        public EntityAIBase create(EntityCreature mob) {
            return factory.apply(mob);
        }
    }

    /**
     * Script-facing builder. Switches between normal-goal and target-goal modes via
     * {@link #target()} / {@link #goal()}; defaults to normal-goal mode.
     */
    public static class GoalBuilderJS {
        private ResourceLocation id;
        private final List<GoalFactory> goals = new ArrayList<>();
        private final List<GoalFactory> targetGoals = new ArrayList<>();
        private boolean targetMode = false;
        /** Priority used for the next appended goal; reset to 0 implicitly per call site. */
        private int priority = 0;

        /** Resolve an entity id string (with optional {@code namespace:path}) to its registry id. */
        public GoalBuilderJS forType(String entityId) {
            this.id = parseId(entityId);
            // Validate the entity exists, resolving its class to confirm EntityCreature compatibility.
            EntityEntry entry = ForgeRegistries.ENTITIES.getValue(this.id);
            if (entry == null) {
                throw new IllegalArgumentException("Unknown entity type: " + entityId);
            }
            Class<?> cls = entry.getEntityClass();
            if (!EntityCreature.class.isAssignableFrom(cls)) {
                throw new IllegalArgumentException(
                        "Entity " + entityId + " (" + cls.getName() + ") is not an EntityCreature");
            }
            return this;
        }

        /** Switch to targeting-goal mode (goals added to {@code mob.targetTasks}). */
        public GoalBuilderJS target() {
            this.targetMode = true;
            return this;
        }

        /** Switch to normal-goal mode (goals added to {@code mob.tasks}). Default. */
        public GoalBuilderJS goal() {
            this.targetMode = false;
            return this;
        }

        /** Set the priority for the next appended goal(s). */
        public GoalBuilderJS priority(int p) {
            this.priority = p;
            return this;
        }

        public GoalBuilderJS swim() {
            return add(new GoalFactory(priority, mob -> new EntityAISwimming((EntityLiving) mob)));
        }

        public GoalBuilderJS wander(double speed) {
            return add(new GoalFactory(priority, mob -> new EntityAIWander(mob, speed)));
        }

        public GoalBuilderJS meleeAttack(double speed, boolean longMemory) {
            return add(new GoalFactory(priority,
                    mob -> new EntityAIAttackMelee(mob, speed, longMemory)));
        }

        public GoalBuilderJS panic(double speed) {
            return add(new GoalFactory(priority, mob -> new EntityAIPanic(mob, speed)));
        }

        public GoalBuilderJS lookIdle() {
            return add(new GoalFactory(priority, mob -> new EntityAILookIdle((EntityLiving) mob)));
        }

        public GoalBuilderJS leapAtTarget(float leapMotionY) {
            return add(new GoalFactory(priority,
                    mob -> new EntityAILeapAtTarget((EntityLiving) mob, leapMotionY)));
        }

        public GoalBuilderJS hurtByTarget(boolean callsForHelp) {
            return add(new GoalFactory(priority,
                    mob -> new EntityAIHurtByTarget(mob, callsForHelp)));
        }

        /**
         * Add a nearest-attackable-target goal for the given target class.
         *
         * @param targetClassName fully-qualified or simple class name resolvable via
         *                        {@link Class#forName(String)}; must be an
         *                        {@code EntityLivingBase} subclass
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        public GoalBuilderJS nearestAttackableTarget(String targetClassName) {
            return add(new GoalFactory(priority, mob -> {
                Class<?> cls = resolveClass(targetClassName);
                if (!EntityLivingBase.class.isAssignableFrom(cls)) {
                    return null;
                }
                return new EntityAINearestAttackableTarget(mob, (Class) cls, false);
            }));
        }

        public boolean isEmpty() {
            return goals.isEmpty() && targetGoals.isEmpty();
        }

        /** Commit this builder into the registry under its resolved id. */
        public void register() {
            registerForEntity(id, this);
        }

        // ---- package-private getters used by GoalRegistry.registerForEntity ----
        List<GoalFactory> goalsList() {
            return goals;
        }

        List<GoalFactory> targetGoalsList() {
            return targetGoals;
        }

        private GoalBuilderJS add(GoalFactory factory) {
            (targetMode ? targetGoals : goals).add(factory);
            return this;
        }

        private static ResourceLocation parseId(String raw) {
            int idx = raw.indexOf(':');
            if (idx < 0) {
                return new ResourceLocation("minecraft", raw);
            }
            return new ResourceLocation(raw.substring(0, idx), raw.substring(idx + 1));
        }

        private static Class<?> resolveClass(String name) {
            ClassLoader[] loaders = {
                    Thread.currentThread().getContextClassLoader(),
                    GoalBuilderJS.class.getClassLoader()
            };
            // Try FQN first, then common net.minecraft.entity package prefix.
            String[] candidates = name.contains(".")
                    ? new String[]{name}
                    : new String[]{
                            "net.minecraft.entity." + name,
                            "net.minecraft.entity.player." + name,
                            name
                    };
            for (String c : candidates) {
                for (ClassLoader cl : loaders) {
                    try {
                        return Class.forName(c, false, cl);
                    } catch (ClassNotFoundException ignored) {
                        // try next combination
                    }
                }
            }
            throw new IllegalArgumentException("Cannot resolve entity class: " + name);
        }
    }
}
