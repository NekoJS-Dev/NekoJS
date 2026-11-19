package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.DispatchKey;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.StableEntityEvent;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.stage.StageGateEnforcer;
import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.event.entity.EntityJoinLevelEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDamageEventJS;
import com.tkisor.nekojs.wrapper.event.entity.LivingDeathEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Fabric bridge for entity events:
 * joinLevel (dispatched by entity type through {@code ServerLevelMixin}),
 * death (dispatched through {@code ServerLivingEntityEvents.ALLOW_DEATH}),
 * damagePre/damagePost (dispatched through {@code ALLOW_DAMAGE}/{@code AFTER_DAMAGE}),
 * plus stage gate enforcement on {@code AttackEntityCallback} and death-drop capture support for
 * {@code MixinLivingEntityDeathDrops}.
 *
 * <p>Bus names and dispatch behavior match the shared entity event group across loaders. The
 * payloads are neutral classes with members named after the vanilla event getters.
 */
public final class FabricEntityEventBindings {

    /** Shares the group name with bindings/event/EntityEvents (Fabric subset). */
    public static final EventGroup ENTITY_EVENTS = EventGroup.of("EntityEvents");

    public static final EventBusJS<EntityJoinLevelEventJS, EntityType<?>> JOIN_LEVEL =
            ENTITY_EVENTS.add("joinLevel", ScriptType.SERVER, EventBusJS.of(
                    EntityJoinLevelEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())), api -> {
                api.versioned().notificationOnly().when(event -> !(event instanceof StableJoin));
                api.stable()
                        .when(event -> event instanceof StableJoin)
                        .view(StableEntityEvent.class, FabricStableEventProjections::entityJoin)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    public static final EventBusJS<LivingDeathEventJS, EntityType<?>> DEATH =
            ENTITY_EVENTS.add("death", ScriptType.SERVER, EventBusJS.of(
                    LivingDeathEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())), api -> {
                api.versioned().when(event -> !(event instanceof StableDeath));
                api.stable()
                        .when(event -> event instanceof StableDeath
                                && ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class, FabricStableEventProjections::livingDeath)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    private static final EventBusJS<LivingDamageEventJS, EntityType<?>> DAMAGE_PRE =
            ENTITY_EVENTS.add("damagePre", ScriptType.SERVER, EventBusJS.of(
                    LivingDamageEventJS.class, true,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType())), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class, FabricStableEventProjections::livingDamage)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .cancellable()
                        .scriptTypes(ScriptType.SERVER);
            });

    private static final EventBusJS<LivingDamageEventJS, EntityType<?>> DAMAGE_POST =
            ENTITY_EVENTS.server("damagePost", LivingDamageEventJS.class,
                    DispatchKey.of(EntityType.class, event -> event.getEntity().getType()), api -> {
                api.versioned();
                api.stable()
                        .when(event -> ServerLevelEventThread.isCurrent(event.getEntity().level()))
                        .view(StableEntityEvent.class, FabricStableEventProjections::livingDamage)
                        .selector(String.class, FabricStableEventProjections::resolveEntityTypeSelector)
                        .scriptTypes(ScriptType.SERVER);
            });

    private static final ThreadLocal<Deque<DeathDropCapture>> DEATH_DROP_CAPTURES = new ThreadLocal<>();

    private record DeathDropCapture(LivingEntity owner, ServerLevel level, List<ItemEntity> drops) {}

    private static final ThreadLocal<DamageFrame> DAMAGE_FRAME = new ThreadLocal<>();

    private static final class DamageFrame {
        private final LivingEntity entity;
        private final DamageSource source;
        private boolean applied;
        private float amount;

        private DamageFrame(LivingEntity entity, DamageSource source) {
            this.entity = entity;
            this.source = source;
        }
    }

    private FabricEntityEventBindings() {}

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("NekoJS-Fabric");

    /** Registers Fabric event callbacks onto the corresponding NekoJS script event buses. */
    public static void register() {
        LOGGER.info("FabricEntityEventBindings registering");
        FabricEntityEventBindingsV2.ensureRegistered();
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && !player.isSpectator() && !StageGateEnforcer.allowAttackEntity(player, entity)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        // joinLevel is triggered by ServerLevelMixin through postJoinLevelPre / onEntityJoinedLevel.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damageAmount) ->
                !DEATH.post(new LivingDeathEventJS(entity, source), entity.getType()));
    }

    private static final class StableDeath extends LivingDeathEventJS {
        private StableDeath(LivingEntity entity, DamageSource source) {
            super(entity, source);
        }
    }

    public static boolean postStableDeath(LivingEntity entity, DamageSource source) {
        return DEATH.hasStableModuleListeners(ScriptType.SERVER)
                && DEATH.postStableModuleCancellable(new StableDeath(entity, source), entity.getType(), ScriptType.SERVER);
    }

    public static float filterDamage(LivingEntity entity, DamageSource source, float amount) {
        return DAMAGE_PRE.hasListeners() && DAMAGE_PRE.post(new LivingDamageEventJS(entity, source, amount), entity.getType())
                ? 0 : amount;
    }

    public static void captureDamage(LivingEntity entity, DamageSource source, float amount) {
        var frame = DAMAGE_FRAME.get();
        if (frame != null && frame.entity == entity && frame.source == source) {
            frame.applied = true;
            frame.amount = amount;
        }
    }

    public static boolean withDamagePost(LivingEntity entity, DamageSource source, BooleanSupplier apply) {
        if (!DAMAGE_POST.hasListeners()) return apply.getAsBoolean();
        var parent = DAMAGE_FRAME.get();
        var frame = new DamageFrame(entity, source);
        DAMAGE_FRAME.set(frame);
        boolean result;
        try {
            result = apply.getAsBoolean();
        } finally {
            if (parent == null) DAMAGE_FRAME.remove();
            else DAMAGE_FRAME.set(parent);
        }
        if (frame.applied) DAMAGE_POST.post(new LivingDamageEventJS(entity, source, frame.amount), entity.getType());
        return result;
    }

    /** Begins capturing {@link ItemEntity} spawns in {@code level} during {@code dropAllDeathLoot}. */
    public static void beginDeathDropCapture(LivingEntity owner, ServerLevel level) {
        Deque<DeathDropCapture> stack = DEATH_DROP_CAPTURES.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            DEATH_DROP_CAPTURES.set(stack);
        }
        stack.push(new DeathDropCapture(owner, level, new ArrayList<>()));
    }

    /** Ends the innermost death-drop capture scope and returns the captured {@link ItemEntity} list. */
    public static List<ItemEntity> endDeathDropCapture() {
        Deque<DeathDropCapture> stack = DEATH_DROP_CAPTURES.get();
        DeathDropCapture frame = stack.pop();
        if (stack.isEmpty()) DEATH_DROP_CAPTURES.remove();
        return frame.drops();
    }

    /**
     * Pre-join hook entry for {@code ServerLevelMixin}: called at {@code ServerLevel#addEntity}
     * HEAD. Returning {@code true} cancels the entity join.
     */
    public static boolean captureSpawnedDrop(Entity owner, Level level, Entity entity) {
        Deque<DeathDropCapture> stack = DEATH_DROP_CAPTURES.get();
        if (stack == null || !(entity instanceof ItemEntity drop)) return false;
        for (DeathDropCapture frame : stack) {
            if (frame.owner() == owner && frame.level() == level) {
                frame.drops().add(drop);
                return true;
            }
        }
        return false;
    }

    private static final class StableJoin extends EntityJoinLevelEventJS {
        private StableJoin(Entity entity, ServerLevel level) {
            super(entity, level);
        }
    }

    public static boolean postJoinLevelPre(Entity entity, ServerLevel level) {
        return level.getServer().isSameThread() && JOIN_LEVEL.hasStableModuleListeners(ScriptType.SERVER)
                && JOIN_LEVEL.postStableModuleCancellable(new StableJoin(entity, level), entity.getType(), ScriptType.SERVER);
    }

    public static void postVersionedJoinLevel(Entity entity, ServerLevel level) {
        if (JOIN_LEVEL.hasListeners()) {
            JOIN_LEVEL.postIgnoringCancellation(new EntityJoinLevelEventJS(entity, level), entity.getType());
        }
    }

    /**
     * Post-join hook entry for {@code ServerLevelMixin}: called at {@code ServerLevel#addEntity}
     * TAIL when {@code addEntity} accepted the entity, applying script-registered AI goals.
     */
    public static void onEntityJoinedLevel(Entity entity, ServerLevel level) {
        GoalRegistry.onEntityJoinLevel(entity, level);
    }

    /**
     * Combined join hook entry delegating to {@link #postJoinLevelPre(Entity, ServerLevel)} and,
     * if not cancelled, {@link #onEntityJoinedLevel(Entity, ServerLevel)}.
     */
    public static void postJoinLevel(Entity entity, ServerLevel level) {
        if (!postJoinLevelPre(entity, level)) {
            onEntityJoinedLevel(entity, level);
        }
    }
}
