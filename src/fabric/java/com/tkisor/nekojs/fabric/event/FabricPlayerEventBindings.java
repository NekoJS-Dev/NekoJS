package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.bindings.event.ServerLevelEventThread;
import com.tkisor.nekojs.listener.InventoryChangeListener;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerTickEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fabric bridge v1 for the mixin-driven player events: container open/close
 * (containerOpened/containerClosed plus the deprecated aliases), crafting/smelting/destroying
 * (crafted/smelted/destroyed, dispatched by item id), advancements, entity interaction
 * (entityInteract, cancellable) and dimension change (changedDimension).
 *
 * <p>fabric-api has no equivalent callback for any of these six, so mixins under the node's
 * fabric/mixin directory fire them (see the comment on each mixin). This class only supplies
 * static post entry points; it needs no register() wiring. The buses are declared in the node
 * twin {@link PlayerEvents}, whose "PlayerEvents" group is merged with
 * {@code FabricServerEventBindings.PLAYER_EVENTS} by EventGroupRegistry.
 */
public final class FabricPlayerEventBindings {

    private FabricPlayerEventBindings() {}

    public static void postTickPre(ServerPlayer player) {
        var bus = FabricServerEventBindings.PLAYER_TICK_PRE;
        if (bus.hasListeners()) {
            bus.post(new PlayerTickEventJS(player));
        }
    }

    public static void postTickPost(ServerPlayer player) {
        var bus = FabricServerEventBindings.PLAYER_TICK_POST;
        if (bus.hasListeners()) {
            bus.post(new PlayerTickEventJS(player));
        }
    }

    /** Hook entry for {@code ServerPlayerContainerMixin}: the call site that opens a menu. */
    public static void postContainerOpened(ServerPlayer player, AbstractContainerMenu menu) {
        PlayerEvents.CONTAINER_OPENED.post(new PlayerContainerEventJS(player, menu));
        PlayerEvents.INVENTORY_OPENED.post(new PlayerContainerEventJS(player, menu));
    }

    /** Hook entry for {@code ServerPlayerContainerMixin}: {@code doCloseContainer}. */
    public static void postContainerClosed(ServerPlayer player, AbstractContainerMenu menu) {
        if (menu != null && menu != player.inventoryMenu) {
            PlayerEvents.CONTAINER_CLOSED.post(new PlayerContainerEventJS(player, menu));
            PlayerEvents.INVENTORY_CLOSED.post(new PlayerContainerEventJS(player, menu));
        }
    }

    public static void postStableContainerClosed(ServerPlayer player, AbstractContainerMenu menu) {
        if (PlayerEvents.CONTAINER_CLOSED.hasStableModuleListeners(ScriptType.SERVER)
                && ServerLevelEventThread.isCurrent(player.level())) {
            PlayerEvents.CONTAINER_CLOSED.postStableModuleNotification(new StableContainerClose(player, menu), ScriptType.SERVER);
        }
    }

    public static boolean isStableContainerClose(PlayerContainerEventJS event) {
        return event instanceof StableContainerClose;
    }

    private static final class StableContainerClose extends PlayerContainerEventJS {
        private StableContainerClose(ServerPlayer player, AbstractContainerMenu menu) {
            super(player, menu);
        }
    }

    /** Hook entry for {@code CraftingResultSlotMixin}: {@code ResultSlot#checkTakeAchievements}
     *  (taking a crafting result). */
    public static void postCrafted(Player player, ItemStack result, Container container) {
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerEvents.CRAFTED.post(
                    new PlayerCraftedEventJS(serverPlayer, result, container), result.getItem());
        }
    }

    /** Hook entry for {@code FurnaceResultSlotMixin}: {@code FurnaceResultSlot#checkTakeAchievements}
     *  (taking a smelting result). */
    public static void postSmelted(Player player, ItemStack result) {
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerEvents.SMELTED.post(
                    new PlayerCraftedEventJS(serverPlayer, result, null), result.getItem());
        }
    }

    public static boolean isStableResultTake(PlayerCraftedEventJS event) {
        return event instanceof StableResultTake;
    }

    public static void postStableResultTake(Player player, ItemStack result, Container container) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var bus = container == null ? PlayerEvents.SMELTED : PlayerEvents.CRAFTED;
        if (bus.hasStableModuleListeners(ScriptType.SERVER)) {
            bus.postStableModuleNotification(
                    new StableResultTake(serverPlayer, result, container), result.getItem(), ScriptType.SERVER);
        }
    }

    private static final class StableResultTake extends PlayerCraftedEventJS {
        private StableResultTake(ServerPlayer player, ItemStack result, Container container) {
            super(player, result, container);
        }
    }

    private static final ThreadLocal<EquipmentDamage> EQUIPMENT_DAMAGE = new ThreadLocal<>();

    private record EquipmentDamage(ServerPlayer player, ItemStack stack) {}

    public static void withEquipmentDamage(ServerPlayer player, ItemStack stack, Runnable operation) {
        if (!PlayerEvents.DESTROYED.hasListeners()) {
            operation.run();
            return;
        }
        var previous = EQUIPMENT_DAMAGE.get();
        EQUIPMENT_DAMAGE.set(new EquipmentDamage(player, stack));
        try {
            operation.run();
        } finally {
            if (previous == null) EQUIPMENT_DAMAGE.remove();
            else EQUIPMENT_DAMAGE.set(previous);
        }
    }

    public static boolean takeEquipmentDamage(ServerPlayer player, ItemStack stack) {
        var damage = EQUIPMENT_DAMAGE.get();
        if (damage == null || damage.player != player || damage.stack != stack) return false;
        EQUIPMENT_DAMAGE.remove();
        return true;
    }

    /** Hook entry for {@code ItemStackDestroyMixin}: the breaking branch of {@code ItemStack#applyDamage}. */
    public static void postDestroyed(ServerPlayer player, ItemStack original, ItemStack stack, boolean equipmentDamage) {
        var capture = DESTROYED_CAPTURE.get();
        boolean versionedOnly = equipmentDamage;
        if (capture != null && capture.player == player && capture.original == original) {
            capture.reported = true;
            versionedOnly |= capture.stableAtEnd;
        }
        if (versionedOnly) {
            PlayerEvents.DESTROYED.post(new ProfiledDestruction(player, stack, false), stack.getItem());
        } else {
            postDestroyed(player, stack);
        }
    }

    private static final ThreadLocal<DestroyCapture> DESTROYED_CAPTURE = new ThreadLocal<>();

    private static final class DestroyCapture {
        private final ServerPlayer player;
        private final boolean stableAtEnd;
        private ItemStack original;
        private ItemStack snapshot;
        private boolean reported;
        private boolean reportedStable;

        private DestroyCapture(ServerPlayer player, boolean stableAtEnd) {
            this.player = player;
            this.stableAtEnd = stableAtEnd;
        }

        private void track(ItemStack original, ItemStack snapshot) {
            this.original = original;
            this.snapshot = snapshot;
        }
    }

    public static <T> T captureDestroyed(ServerPlayer player, ItemStack stack, java.util.function.Supplier<T> operation,
                                         java.util.function.Predicate<T> consumed) {
        return captureDestroyed(player, stack, operation, consumed, false);
    }

    public static InteractionResult captureUseOnDestruction(ServerPlayer player, ItemStack stack,
                                                          java.util.function.Supplier<InteractionResult> operation) {
        return captureDestroyed(player, stack, operation, result -> true, true);
    }

    public static void captureAttackDestruction(ServerPlayer player, ItemStack stack, Runnable attack) {
        if (!PlayerEvents.DESTROYED.hasListeners() || stack.isEmpty()) {
            attack.run();
            return;
        }
        var capture = new DestroyCapture(player, true);
        capture.track(stack, stack.copy());
        withDestroyCapture(capture, () -> {
            attack.run();
            return null;
        });
    }

    public static void postAttackDestruction(ServerPlayer player, ItemStack stack) {
        var capture = DESTROYED_CAPTURE.get();
        if (capture != null && capture.player == player && capture.original == stack && !capture.reportedStable) {
            capture.reportedStable = true;
            postStableDestruction(player, capture.snapshot);
        }
    }

    public static boolean captureMiningDestruction(ServerPlayer player, java.util.function.Supplier<Boolean> operation) {
        if (!PlayerEvents.DESTROYED.hasListeners()) return operation.get();
        return captureDestroyed(new DestroyCapture(player, true), operation, result -> true);
    }

    public static void trackMiningTool(ServerPlayer player, ItemStack stack, ItemStack snapshot) {
        var capture = DESTROYED_CAPTURE.get();
        if (capture != null && capture.player == player && capture.original == null && !snapshot.isEmpty()) {
            capture.track(stack, snapshot);
        }
    }

    private static <T> T captureDestroyed(ServerPlayer player, ItemStack stack, java.util.function.Supplier<T> operation,
                                          java.util.function.Predicate<T> consumed, boolean stableAtEnd) {
        if (!PlayerEvents.DESTROYED.hasListeners() || stack.isEmpty()) return operation.get();
        var capture = new DestroyCapture(player, stableAtEnd);
        capture.track(stack, stack.copy());
        return captureDestroyed(capture, operation, consumed);
    }

    private static <T> T captureDestroyed(DestroyCapture capture, java.util.function.Supplier<T> operation,
                                          java.util.function.Predicate<T> consumed) {
        T result = withDestroyCapture(capture, operation);
        if (capture.original != null && capture.original.isEmpty() && !capture.snapshot.isEmpty()) {
            if (capture.stableAtEnd) {
                postStableDestruction(capture.player, capture.snapshot);
            } else if (!capture.reported && consumed.test(result)) {
                postDestroyed(capture.player, capture.original, capture.snapshot, false);
            }
        }
        return result;
    }

    private static <T> T withDestroyCapture(DestroyCapture capture, java.util.function.Supplier<T> operation) {
        var parent = DESTROYED_CAPTURE.get();
        DESTROYED_CAPTURE.set(capture);
        try {
            return operation.get();
        } finally {
            if (parent == null) DESTROYED_CAPTURE.remove();
            else DESTROYED_CAPTURE.set(parent);
        }
    }

    private static void postStableDestruction(ServerPlayer player, ItemStack snapshot) {
        PlayerEvents.DESTROYED.postStableModuleNotification(
                new ProfiledDestruction(player, snapshot, true), snapshot.getItem(), ScriptType.SERVER);
    }

    public static boolean isDestructionPublished(PlayerDestroyItemEventJS event, boolean stable) {
        return !(event instanceof ProfiledDestruction profiled) || profiled.stable == stable;
    }

    private static final class ProfiledDestruction extends PlayerDestroyItemEventJS {
        private final boolean stable;

        private ProfiledDestruction(ServerPlayer player, ItemStack stack, boolean stable) {
            super(player, stack);
            this.stable = stable;
        }
    }

    public static void postDestroyed(ServerPlayer player, ItemStack stack) {
        if (!PlayerEvents.DESTROYED.hasListeners()) return;
        PlayerEvents.DESTROYED.post(
                new PlayerDestroyItemEventJS(player, stack), stack.getItem());
    }

    /** Hook entry for {@code PlayerAdvancementsMixin}: the display section where
     *  {@code PlayerAdvancements} completes an advancement. */
    public static void postAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        PlayerEvents.ADVANCEMENT.post(new PlayerAdvancementEventJS(player, advancement));
    }

    public static boolean isStableAdvancement(PlayerAdvancementEventJS event) {
        return event instanceof StableAdvancement;
    }

    public static void postStableAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        if (PlayerEvents.ADVANCEMENT.hasStableModuleListeners(ScriptType.SERVER)
                && ServerLevelEventThread.isCurrent(player.level())) {
            PlayerEvents.ADVANCEMENT.postStableModuleNotification(new StableAdvancement(player, advancement), ScriptType.SERVER);
        }
    }

    private static final class StableAdvancement extends PlayerAdvancementEventJS {
        private StableAdvancement(ServerPlayer player, AdvancementHolder advancement) {
            super(player, advancement);
        }
    }

    /**
     * Hook entry for {@code ServerEntityInteractMixin}: the interactOn call site in
     * {@code handleInteract}.
     *
     * @return whether a listener cancelled this interaction ({@code true} = cancel packet handling)
     */
    public static boolean postEntityInteract(ServerPlayer player, Entity target, InteractionHand hand) {
        return PlayerEvents.ENTITY_INTERACT.post(
                new PlayerEntityInteractEventJS(player, target, hand));
    }

    public static boolean isStableInteraction(PlayerEntityInteractEventJS event) {
        return event instanceof StableEntityInteraction;
    }

    public static boolean postStableEntityInteract(ServerPlayer player, Entity target, InteractionHand hand) {
        return PlayerEvents.ENTITY_INTERACT.hasStableModuleListeners(ScriptType.SERVER)
                && PlayerEvents.ENTITY_INTERACT.postStableModuleCancellable(
                        new StableEntityInteraction(player, target, hand), ScriptType.SERVER);
    }

    private static final class StableEntityInteraction extends PlayerEntityInteractEventJS {
        private StableEntityInteraction(ServerPlayer player, Entity target, InteractionHand hand) {
            super(player, target, hand);
        }
    }

    /** Hook entry for {@code ServerPlayerDimensionMixin}: the successful end of
     *  {@code ServerPlayer#teleport(TeleportTransition)}. */
    public static void postChangedDimension(ServerPlayer player,
                                            ResourceKey<Level> from, ResourceKey<Level> to) {
        PlayerEvents.CHANGED_DIMENSION.post(new PlayerChangedDimensionEventJS(player, from, to));
    }

    /**
     * Attaches the inventory listener lifecycle for {@code PlayerEvents.inventoryChanged}. It sits in
     * the same place as {@code PlayerEventListener} on NeoForge (PlayerLoggedInEvent and
     * PlayerEvent.Clone) but uses fabric-api callbacks, so it needs no mixin.
     * {@code InventoryChangeListener.getOrCreate} is idempotent (it caches in a WeakHashMap), and
     * COPY_FROM re-attaches the listener to the new entity after death, respawn or a dimension
     * change.
     *
     * <p>{@code NekoJSFabricMod.onInitialize} calls it. The callbacks register on both dedicated and
     * integrated servers, and the event itself only fires for ServerPlayer.
     */
    public static void registerLifecycle() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                InventoryChangeListener.getOrCreate(handler.player));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                InventoryChangeListener.getOrCreate(newPlayer));
    }
}
