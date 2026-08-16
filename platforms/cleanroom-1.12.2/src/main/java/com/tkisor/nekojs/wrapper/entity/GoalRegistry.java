package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
@Doc("Entity AI goal registry keyed by entity registry id (not class, since all script mobs share NekoScriptMob).")
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

    /** Creates a new goal builder. */
    @Doc("Creates a new goal builder.")
    @Return("a fresh GoalBuilderJS, not yet bound to an entity id")
    public static GoalBuilderJS builder() {
        return new GoalBuilderJS();
    }

    /** Persist a builder's collected goals for the given entity id. */
    @Doc("Commits a builder's collected goals under the given entity id.")
    @Param(name = "id", value = "the entity registry id")
    @Param(name = "builder", value = "the builder whose goals are persisted")
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
    @Doc("Applies registered goals to a NekoScriptMob by its nekoId (internal).")
    @Param(name = "mob", value = "the mob to configure")
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
    @Doc("Forge hook injecting script-registered goals into vanilla creatures on world join (internal).")
    @Param(name = "event", value = "the EntityJoinWorldEvent")
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
    @Doc("Script-facing AI goal builder; goals are appended with the current priority.")
    @Doc("Switch between mob.tasks (goal()) and mob.targetTasks (target()) mode; default is goal() mode.")
    public static class GoalBuilderJS {
        private ResourceLocation id;
        private final List<GoalFactory> goals = new ArrayList<>();
        private final List<GoalFactory> targetGoals = new ArrayList<>();
        private boolean targetMode = false;
        /** Priority used for the next appended goal; reset to 0 implicitly per call site. */
        private int priority = 0;

        /** Resolve an entity id string (with optional {@code namespace:path}) to its registry id. */
        @Doc("Binds the builder to an entity type by id.")
        @Param(name = "entityId", value = "entity id like 'minecraft:zombie'; must be an EntityCreature type")
        @Return("this builder, for chaining")
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
        @Doc("Switches to targeting-goal mode (mob.targetTasks).")
        @Return("this builder, for chaining")
        public GoalBuilderJS target() {
            this.targetMode = true;
            return this;
        }

        /** Switch to normal-goal mode (goals added to {@code mob.tasks}). Default. */
        @Doc("Switches to normal-goal mode (mob.tasks); the default mode.")
        @Return("this builder, for chaining")
        public GoalBuilderJS goal() {
            this.targetMode = false;
            return this;
        }

        /** Set the priority for the next appended goal(s). */
        @Doc("Sets the priority used for the goals appended next; lower runs first.")
        @Param(name = "p", value = "task priority; lower values take precedence")
        @Return("this builder, for chaining")
        public GoalBuilderJS priority(int p) {
            this.priority = p;
            return this;
        }

        /** Adds a swimming goal. */
        @Doc("Adds a swimming goal so the mob floats in water.")
        @Return("this builder, for chaining")
        public GoalBuilderJS swim() {
            return add(new GoalFactory(priority, mob -> new EntityAISwimming((EntityLiving) mob)));
        }

        /** Adds a wandering goal. */
        @Doc("Adds a wandering goal.")
        @Param(name = "speed", value = "movement speed while wandering, e.g. 1.0")
        @Return("this builder, for chaining")
        public GoalBuilderJS wander(double speed) {
            return add(new GoalFactory(priority, mob -> new EntityAIWander(mob, speed)));
        }

        /** Adds a melee attack goal. */
        @Doc("Adds a melee attack goal.")
        @Param(name = "speed", value = "movement speed while attacking")
        @Param(name = "longMemory", value = "true to keep chasing the target longer")
        @Return("this builder, for chaining")
        public GoalBuilderJS meleeAttack(double speed, boolean longMemory) {
            return add(new GoalFactory(priority,
                    mob -> new EntityAIAttackMelee(mob, speed, longMemory)));
        }

        /** Adds a panic goal. */
        @Doc("Adds a panic goal triggered when the mob is hurt.")
        @Param(name = "speed", value = "movement speed while panicking")
        @Return("this builder, for chaining")
        public GoalBuilderJS panic(double speed) {
            return add(new GoalFactory(priority, mob -> new EntityAIPanic(mob, speed)));
        }

        /** Adds a look-idle goal. */
        @Doc("Adds an idle look-around goal.")
        @Return("this builder, for chaining")
        public GoalBuilderJS lookIdle() {
            return add(new GoalFactory(priority, mob -> new EntityAILookIdle((EntityLiving) mob)));
        }

        /** Adds a leap-at-target goal. */
        @Doc("Adds a leap-at-target goal.")
        @Param(name = "leapMotionY", value = "vertical leap strength, e.g. 0.5")
        @Return("this builder, for chaining")
        public GoalBuilderJS leapAtTarget(float leapMotionY) {
            return add(new GoalFactory(priority,
                    mob -> new EntityAILeapAtTarget((EntityLiving) mob, leapMotionY)));
        }

        /** Adds a hurt-by-target goal. */
        @Doc("Adds a targeting goal that attacks whoever hurt the mob.")
        @Param(name = "callsForHelp", value = "true to make nearby mobs of the same type also aggro")
        @Return("this builder, for chaining")
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
        @Doc("Adds a targeting goal attacking the nearest entity of the given class.")
        @Param(name = "targetClassName", value = "entity class name, FQN or simple like 'EntityPlayer'; must extend EntityLivingBase")
        @Return("this builder, for chaining")
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

        /** Whether no goals were collected. */
        @Doc("Checks whether no goals were collected.")
        @Return("true if neither normal nor targeting goals were added")
        public boolean isEmpty() {
            return goals.isEmpty() && targetGoals.isEmpty();
        }

        /** Commit this builder into the registry under its resolved id. */
        @Doc("Commits the collected goals into the registry under the bound entity id.")
        @Doc("Not needed with the Consumer overloads of forType(), which register automatically.")
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
