// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.entity;

// 脚本实体注册面（EntityTypeBuilder）是 neoforge 面；fabric 上此依赖不存在
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;

public final class GoalRegistry {
    // 注册阶段写、gameplay 阶段读，用 ConcurrentHashMap 保证 safe-publication
    private static final Map<EntityType<?>, List<GoalFactory>> GOALS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<Mob> APPLIED_JOIN_GOALS = java.util.Collections.newSetFromMap(
            new com.google.common.collect.MapMaker().weakKeys().<Mob, Boolean>makeMap());

    private GoalRegistry() {}

    public static GoalBuilderJS builder() {
        return new GoalBuilderJS();
    }

    public static void register(EntityType<?> type, List<GoalFactory> goals) {
        if (type == null || goals.isEmpty()) {
            return;
        }
        GOALS.compute(type, (ignored, previous) -> {
            List<GoalFactory> combined = new ArrayList<>();
            if (previous != null) combined.addAll(previous);
            combined.addAll(goals);
            return List.copyOf(combined);
        });
    }

    public static void applyBuiltInGoals(Mob mob) {
        List<GoalFactory> goals = GOALS.get(mob.getType());
        if (goals == null) {
            return;
        }
        apply(mob, goals);
    }

    public static void onEntityJoinLevel(Entity entity, Level level) {
        if (GOALS.isEmpty()) return;
        if (level.isClientSide() || !(entity instanceof Mob mob) || mob instanceof NekoScriptMob) {
            return;
        }
        List<GoalFactory> goals = GOALS.get(mob.getType());
        if (goals == null || APPLIED_JOIN_GOALS.contains(mob)) return;
        apply(mob, goals);
        APPLIED_JOIN_GOALS.add(mob);
    }

    private static void apply(Mob mob, List<GoalFactory> goals) {
        List<Runnable> additions = new ArrayList<>(goals.size());
        for (GoalFactory factory : goals) {
            Goal goal = factory.create(mob);
            if (goal != null) {
                additions.add(() -> (factory.target() ? mob.targetSelector : mob.goalSelector)
                        .addGoal(factory.priority(), goal));
            }
        }
        additions.forEach(Runnable::run);
    }

    public record GoalFactory(int priority, boolean target, Function<Mob, Goal> factory) {
        public GoalFactory {
            factory = GoalBuilderJS.requireGoalFactory(factory);
        }

        Goal create(Mob mob) {
            return factory.apply(mob);
        }
    }

    /**
     * 常用目标实体 id（无命名空间前缀）→ 实体类。
     *
     * <p>NeoForge 自 1.21.x 起 {@code EntityType.getBaseClass()} 硬编码返回
     * {@code Entity.class}，无法从注册表推断具体类，故内置常用映射兜底；
     * 未覆盖的实体请用 {@code Java.type('net.minecraft...')} 传 Java 类。
     */
    private static final Map<String, Class<? extends LivingEntity>> TARGET_CLASSES = new HashMap<>();

    static {
        TARGET_CLASSES.put("player", Player.class);
        TARGET_CLASSES.put("zombie", Zombie.class);
        TARGET_CLASSES.put("skeleton", Skeleton.class);
        TARGET_CLASSES.put("creeper", Creeper.class);
        TARGET_CLASSES.put("spider", Spider.class);
        TARGET_CLASSES.put("enderman", EnderMan.class);
        TARGET_CLASSES.put("witch", Witch.class);
        TARGET_CLASSES.put("blaze", Blaze.class);
        // Slime 在 26.1 仍是 monster 包，26.2 起拆到 cubemob 子包——按版本取可用类
        TARGET_CLASSES.put("slime", Slime.class);
        TARGET_CLASSES.put("phantom", Phantom.class);
        TARGET_CLASSES.put("husk", Husk.class);
        TARGET_CLASSES.put("drowned", Drowned.class);
        TARGET_CLASSES.put("stray", Stray.class);
        TARGET_CLASSES.put("wither_skeleton", WitherSkeleton.class);
        TARGET_CLASSES.put("piglin", Piglin.class);
        TARGET_CLASSES.put("zombified_piglin", ZombifiedPiglin.class);
        TARGET_CLASSES.put("guardian", Guardian.class);
        TARGET_CLASSES.put("elder_guardian", ElderGuardian.class);
        TARGET_CLASSES.put("shulker", Shulker.class);
        TARGET_CLASSES.put("villager", Villager.class);
        TARGET_CLASSES.put("iron_golem", IronGolem.class);
        TARGET_CLASSES.put("snow_golem", SnowGolem.class);
        TARGET_CLASSES.put("wolf", Wolf.class);
        TARGET_CLASSES.put("cat", Cat.class);
        TARGET_CLASSES.put("cow", Cow.class);
        TARGET_CLASSES.put("pig", Pig.class);
        TARGET_CLASSES.put("sheep", Sheep.class);
        TARGET_CLASSES.put("chicken", Chicken.class);
        TARGET_CLASSES.put("bee", Bee.class);
        TARGET_CLASSES.put("fox", Fox.class);
        TARGET_CLASSES.put("goat", Goat.class);
        TARGET_CLASSES.put("llama", Llama.class);
        TARGET_CLASSES.put("rabbit", Rabbit.class);
        TARGET_CLASSES.put("turtle", Turtle.class);
        TARGET_CLASSES.put("bat", Bat.class);
    }

    /** 按候选类名逐个加载（跨小版本拆包差异兜底），全部失败则跳过该映射。 */

    /**
     * 目标实体 → 实体类。支持 Java 类对象（{@code Java.type(...)}）与实体 id 字符串
     * （内置常用映射；NekoJS 注册的脚本实体统一为 {@link NekoScriptMob}）。
     */
    @SuppressWarnings("unchecked") // 已用 isAssignableFrom 校验，转换安全
    private static Class<? extends LivingEntity> resolveTarget(Object target) {
        if (target instanceof Class<?> clazz) {
            if (LivingEntity.class.isAssignableFrom(clazz)) {
                return (Class<? extends LivingEntity>) clazz;
            }
            throw new IllegalArgumentException("[NEKO-4002] 目标类型必须是 LivingEntity — goal target must be a LivingEntity: " + clazz.getName());
        }
        if (target instanceof EntityType<?> type) {
            throw new IllegalArgumentException(
                    "[NEKO-4003] 无法从 EntityType 推断目标类（NeoForge 不暴露实体类），请传实体 id 字符串或 Java 类 — cannot infer target class from EntityType: " + type);
        }
        if (target instanceof String id) {
            String normalized = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            Class<? extends LivingEntity> mapped = !id.contains(":") || id.startsWith("minecraft:")
                    ? TARGET_CLASSES.get(normalized) : null;
            if (mapped != null) {
                return mapped;
            }
            ResourceLocation location = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("nekojs", id);
            Class<? extends LivingEntity> registered = EntityTypeBuilder.registeredEntityClass(location);
            if (registered != null) {
                return registered;
            }
            if (EntityTypeBuilder.getEntityType(location) != null) {
                throw new IllegalArgumentException("[NEKO-4004] 无法从 opaque entity factory 推断目标类，请传 Java 类 — cannot infer target class from opaque entity factory: " + id);
            }
            throw new IllegalArgumentException("[NEKO-4004] 未知目标实体（无内置映射，可用 Java.type(...) 传类） — unknown target entity: " + id);
        }
        throw new IllegalArgumentException("[NEKO-4005] 无法解析目标 — could not resolve goal target: " + target);
    }

    public static class GoalBuilderJS {
        private EntityType<?> type;
        private final List<GoalFactory> goals = new ArrayList<>();

        public GoalBuilderJS forType(EntityType<?> type) {
            this.type = type;
            return this;
        }

        public GoalBuilderJS forType(String id) {
            ResourceLocation location = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("minecraft", id);
            this.type = BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElseThrow(() -> new IllegalArgumentException("Unknown entity type: " + id));
            return this;
        }

        public GoalBuilderJS floatInWater() {
            return floatInWater(0);
        }

        public GoalBuilderJS floatInWater(int priority) {
            goals.add(new GoalFactory(priority, false, mob -> new FloatGoal(mob)));
            return this;
        }

        public GoalBuilderJS randomStroll(double speed) {
            return randomStroll(5, speed);
        }

        public GoalBuilderJS randomStroll(int priority, double speed) {
            goals.add(new GoalFactory(priority, false, mob -> mob instanceof PathfinderMob pathfinderMob ? new RandomStrollGoal(pathfinderMob, speed) : null));
            return this;
        }

        public GoalBuilderJS meleeAttack(double speed, boolean longMemory) {
            return meleeAttack(4, speed, longMemory);
        }

        public GoalBuilderJS meleeAttack(int priority, double speed, boolean longMemory) {
            goals.add(new GoalFactory(priority, false, mob -> mob instanceof PathfinderMob pathfinderMob ? new MeleeAttackGoal(pathfinderMob, speed, longMemory) : null));
            return this;
        }

        public GoalBuilderJS panic(double speed) {
            return panic(1, speed);
        }

        public GoalBuilderJS panic(int priority, double speed) {
            goals.add(new GoalFactory(priority, false, mob -> mob instanceof PathfinderMob pathfinderMob ? new PanicGoal(pathfinderMob, speed) : null));
            return this;
        }

        // ===== 目标类 goal（加入 targetSelector）=====

        /** 追击最近的目标（{@code NearestAttackableTargetGoal}，默认必须看见）。 */
        public GoalBuilderJS target(Object target) {
            return target(4, target, true);
        }

        public GoalBuilderJS target(int priority, Object target) {
            return target(priority, target, true);
        }

        public GoalBuilderJS target(int priority, Object target, boolean mustSee) {
            Class<? extends LivingEntity> clazz = resolveTarget(target);
            goals.add(new GoalFactory(priority, true, mob -> new NearestAttackableTargetGoal<>(mob, clazz, mustSee)));
            return this;
        }

        /** 被攻击后反击（{@code HurtByTargetGoal}）。 */
        public GoalBuilderJS hurtByTarget() {
            return hurtByTarget(3);
        }

        public GoalBuilderJS hurtByTarget(int priority) {
            goals.add(new GoalFactory(priority, true, mob -> mob instanceof PathfinderMob pathfinderMob ? new HurtByTargetGoal(pathfinderMob) : null));
            return this;
        }

        // ===== 观察 / 躲避 =====

        /** 看向附近目标（{@code LookAtPlayerGoal}）。 */
        public GoalBuilderJS lookAt(Object target, float radius) {
            return lookAt(3, target, radius);
        }

        public GoalBuilderJS lookAt(int priority, Object target, float radius) {
            Class<? extends LivingEntity> clazz = resolveTarget(target);
            goals.add(new GoalFactory(priority, false, mob -> new LookAtPlayerGoal(mob, clazz, radius)));
            return this;
        }

        /** 逃离附近目标（{@code AvoidEntityGoal}，走/跑同一速度）。 */
        public GoalBuilderJS avoid(Object target, float radius, double speed) {
            return avoid(3, target, radius, speed);
        }

        public GoalBuilderJS avoid(int priority, Object target, float radius, double speed) {
            Class<? extends LivingEntity> clazz = resolveTarget(target);
            goals.add(new GoalFactory(priority, false, mob -> mob instanceof PathfinderMob pathfinderMob
                    ? new AvoidEntityGoal<>(pathfinderMob, clazz, radius, speed, speed)
                    : null));
            return this;
        }

        /** Registers a regular goal created by a native Java factory. */
        public GoalBuilderJS custom(int priority, Function<Mob, Goal> factory) {
            goals.add(new GoalFactory(priority, false, requireGoalFactory(factory)));
            return this;
        }

        /** Registers a target goal created by a native Java factory. */
        public GoalBuilderJS customTarget(int priority, Function<Mob, Goal> factory) {
            goals.add(new GoalFactory(priority, true, requireGoalFactory(factory)));
            return this;
        }

        /** Registers a native goal with a public (Mob) constructor. */
        public GoalBuilderJS customClass(int priority, Class<? extends Goal> goalClass) {
            return custom(priority, nativeGoalFactory(goalClass));
        }

        /** Registers a native target goal with a public (Mob) constructor. */
        public GoalBuilderJS customTargetClass(int priority, Class<? extends Goal> goalClass) {
            return customTarget(priority, nativeGoalFactory(goalClass));
        }

        private static Function<Mob, Goal> nativeGoalFactory(Class<? extends Goal> goalClass) {
            if (goalClass == null || !Goal.class.isAssignableFrom(goalClass)
                    || !java.lang.reflect.Modifier.isPublic(goalClass.getModifiers())
                    || java.lang.reflect.Modifier.isAbstract(goalClass.getModifiers())) {
                throw new IllegalArgumentException("[NEKO-4017] Goal class must be public and concrete");
            }
            java.lang.reflect.Constructor<? extends Goal> constructor;
            try {
                constructor = goalClass.getConstructor(Mob.class);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("[NEKO-4017] Goal class must have a public constructor(Mob): "
                        + goalClass.getName(), failure);
            }
            return mob -> {
                try {
                    return constructor.newInstance(mob);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("[NEKO-4017] Native goal construction failed: "
                            + goalClass.getName(), failure);
                }
            };
        }

        private static Function<Mob, Goal> requireGoalFactory(Function<Mob, Goal> factory) {
            if (factory == null) {
                throw new IllegalArgumentException("[NEKO-4017] Goal factory must not be null");
            }
            String className = factory.getClass().getName();
            if (java.lang.reflect.Proxy.isProxyClass(factory.getClass()) || className.startsWith("graal.")
                    || className.startsWith("com.oracle.truffle.") || className.startsWith("org.graalvm.polyglot.")) {
                throw new IllegalArgumentException("[NEKO-4017] Persistent goal factories must be native Java implementations");
            }
            return factory;
        }

        public void register() {
            if (type == null) {
                throw new IllegalStateException("Goal builder requires forType(...) before register()");
            }
            GoalRegistry.register(type, goals);
        }
    }
}
