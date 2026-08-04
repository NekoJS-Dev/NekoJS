package com.tkisor.nekojs.wrapper.event.player;

import lombok.Getter;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 统一的玩家事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.player} 在 NeoForge（21.1/26.x）与 Cleanroom 上一致可用。
 * 事件特定字段（如 {@code message}、{@code from}/{@code to}、{@code container}、
 * {@code advancement}、{@code original}、{@code wasDeath}）仅在对应事件上非 null。
 *
 * <p>跨平台可移植性说明：
 * <ul>
 *   <li>{@code from}/{@code to}（changedDimension）统一为 {@code String}：
 *       NeoForge 的 {@code ResourceKey<Level>} 取 {@code .identifier()/.location().toString()}，
 *       Cleanroom 的 int dim id 取 {@code String.valueOf}。</li>
 *   <li>{@code original} 统一为 {@code Object}：{@code destroyed} 事件为 {@code ItemStack}，
 *       {@code cloned} 事件为原 {@code Player}/{@code EntityPlayer}。</li>
 * </ul>
 */
@Getter
public class PlayerEventJS {
    private final Player player;
    // 事件特定字段（nullable）
    private String message;
    private String from;
    private String to;
    private AbstractContainerMenu container;
    private AdvancementHolder advancement;
    private Object original;
    private Boolean wasDeath;
    private ItemStack crafting;
    private ItemStack smelting;
    private Entity target;

    public PlayerEventJS(Player player) {
        this.player = player;
    }

    public PlayerEventJS withMessage(String message) {
        this.message = message;
        return this;
    }

    public PlayerEventJS withDimension(String from, String to) {
        this.from = from;
        this.to = to;
        return this;
    }

    public PlayerEventJS withContainer(AbstractContainerMenu container) {
        this.container = container;
        return this;
    }

    public PlayerEventJS withAdvancement(AdvancementHolder advancement) {
        this.advancement = advancement;
        return this;
    }

    public PlayerEventJS withOriginal(Object original) {
        this.original = original;
        return this;
    }

    public PlayerEventJS withWasDeath(boolean wasDeath) {
        this.wasDeath = wasDeath;
        return this;
    }

    public PlayerEventJS withCrafting(ItemStack crafting) {
        this.crafting = crafting;
        return this;
    }

    public PlayerEventJS withSmelting(ItemStack smelting) {
        this.smelting = smelting;
        return this;
    }

    public PlayerEventJS withTarget(Entity target) {
        this.target = target;
        return this;
    }

    /**
     * 用于 {@code destroyed} 事件的 dispatch key 提取：仅在该事件上 {@code original} 为
     * {@code ItemStack}，返回其 {@code Item}。
     */
    public ItemStack getDestroyedItem() {
        return original instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }
}
