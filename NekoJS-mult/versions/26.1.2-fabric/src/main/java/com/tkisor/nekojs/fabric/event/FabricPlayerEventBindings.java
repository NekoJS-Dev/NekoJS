package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.bindings.event.PlayerEvents;
import com.tkisor.nekojs.wrapper.event.player.PlayerAdvancementEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerChangedDimensionEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerContainerEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerCraftedEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerDestroyItemEventJS;
import com.tkisor.nekojs.wrapper.event.player.PlayerEntityInteractEventJS;
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
 * 玩家事件面「mixin 面」的 fabric 桥 v1：容器开合（containerOpened/containerClosed +
 * 弃用别名）、合成/烧炼/损毁（crafted/smelted/destroyed，按物品 id dispatch）、
 * 进度（advancement）、实体交互（entityInteract，可取消）、维度切换（changedDimension）。
 *
 * <p>这 6 类事件 fabric-api 均无等价回调，全部由节点目录 fabric/mixin 内的 mixin
 * 钩子触发（见各 mixin 注释）；本类只提供静态 post 入口，无需 register() 接线。
 * 总线声明在节点孪生 {@link PlayerEvents}（同名组 "PlayerEvents" 与
 * {@code FabricServerEventBindings.PLAYER_EVENTS} 经 EventGroupRegistry 合并）。
 */
public final class FabricPlayerEventBindings {

    private FabricPlayerEventBindings() {}

    /** ServerPlayerContainerMixin 钩子入口：打开/关闭菜单的调用点。 */
    public static void postContainerOpened(ServerPlayer player, AbstractContainerMenu menu) {
        PlayerEvents.CONTAINER_OPENED.post(new PlayerContainerEventJS(player, menu));
        PlayerEvents.INVENTORY_OPENED.post(new PlayerContainerEventJS(player, menu));
    }

    /** ServerPlayerContainerMixin 钩子入口：{@code doCloseContainer}。 */
    public static void postContainerClosed(ServerPlayer player, AbstractContainerMenu menu) {
        PlayerEvents.CONTAINER_CLOSED.post(new PlayerContainerEventJS(player, menu));
        PlayerEvents.INVENTORY_CLOSED.post(new PlayerContainerEventJS(player, menu));
    }

    /** CraftingResultSlotMixin 钩子入口：{@code ResultSlot#checkTakeAchievements}（合成取件）。 */
    public static void postCrafted(Player player, ItemStack result, Container container) {
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerEvents.CRAFTED.post(
                    new PlayerCraftedEventJS(serverPlayer, result, container), result.getItem());
        }
    }

    /** FurnaceResultSlotMixin 钩子入口：{@code FurnaceResultSlot#checkTakeAchievements}（烧炼取件）。 */
    public static void postSmelted(Player player, ItemStack result) {
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerEvents.SMELTED.post(
                    new PlayerCraftedEventJS(serverPlayer, result, null), result.getItem());
        }
    }

    /** ItemStackDestroyMixin 钩子入口：{@code ItemStack#applyDamage} 的碎裂分支。 */
    public static void postDestroyed(ServerPlayer player, ItemStack stack) {
        PlayerEvents.DESTROYED.post(
                new PlayerDestroyItemEventJS(player, stack), stack.getItem());
    }

    /** PlayerAdvancementsMixin 钩子入口：{@code PlayerAdvancements} 完成进度的 display 段。 */
    public static void postAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        PlayerEvents.ADVANCEMENT.post(new PlayerAdvancementEventJS(player, advancement));
    }

    /**
     * ServerEntityInteractMixin 钩子入口：{@code handleInteract} 的 interactOn 调用点。
     * @return 监听器是否取消本次交互（true = 取消包处理）
     */
    public static boolean postEntityInteract(ServerPlayer player, Entity target, InteractionHand hand) {
        return PlayerEvents.ENTITY_INTERACT.post(
                new PlayerEntityInteractEventJS(player, target, hand));
    }

    /** ServerPlayerDimensionMixin 钩子入口：{@code ServerPlayer#teleport(TeleportTransition)} 成功收尾。 */
    public static void postChangedDimension(ServerPlayer player,
                                            ResourceKey<Level> from, ResourceKey<Level> to) {
        PlayerEvents.CHANGED_DIMENSION.post(new PlayerChangedDimensionEventJS(player, from, to));
    }
}
