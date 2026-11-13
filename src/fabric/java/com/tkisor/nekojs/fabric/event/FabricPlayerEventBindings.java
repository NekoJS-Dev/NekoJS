package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.listener.InventoryChangeListener;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
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

    /** Hook entry for {@code ServerPlayerContainerMixin}: the call site that opens a menu. */
    public static void postContainerOpened(ServerPlayer player, AbstractContainerMenu menu) {
        PlayerEvents.CONTAINER_OPENED.post(new PlayerContainerEventJS(player, menu));
        PlayerEvents.INVENTORY_OPENED.post(new PlayerContainerEventJS(player, menu));
    }

    /** Hook entry for {@code ServerPlayerContainerMixin}: {@code doCloseContainer}. */
    public static void postContainerClosed(ServerPlayer player, AbstractContainerMenu menu) {
        PlayerEvents.CONTAINER_CLOSED.post(new PlayerContainerEventJS(player, menu));
        PlayerEvents.INVENTORY_CLOSED.post(new PlayerContainerEventJS(player, menu));
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

    /** Hook entry for {@code ItemStackDestroyMixin}: the breaking branch of {@code ItemStack#applyDamage}. */
    public static void postDestroyed(ServerPlayer player, ItemStack stack) {
        PlayerEvents.DESTROYED.post(
                new PlayerDestroyItemEventJS(player, stack), stack.getItem());
    }

    /** Hook entry for {@code PlayerAdvancementsMixin}: the display section where
     *  {@code PlayerAdvancements} completes an advancement. */
    public static void postAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        PlayerEvents.ADVANCEMENT.post(new PlayerAdvancementEventJS(player, advancement));
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
