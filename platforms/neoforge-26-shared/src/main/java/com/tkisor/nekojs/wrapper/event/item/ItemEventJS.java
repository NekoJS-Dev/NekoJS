package com.tkisor.nekojs.wrapper.event.item;

import lombok.Getter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

/**
 * 统一的物品事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.player}、{@code event.item}、{@code event.level} 在 NeoForge 与 Cleanroom 上一致可用。
 * 事件特定字段（如 {@code hand}、{@code target}、{@code entityItem}、{@code resultItem}）仅在对应事件上非 null。
 *
 * <p>{@code item}（ItemStack）同时作为按 Item 分发事件的 dispatch key 来源：脚本注册时传入的 key 是 Item，
 * 内部通过 {@code item.getItem()} 提取。
 */
@Getter
public class ItemEventJS {
    private final Player player;
    private final ItemStack item;
    private final LevelAccessor level;
    // 事件特定字段（nullable）
    private InteractionHand hand;
    private Entity target;
    private ItemEntity entityItem;
    private ItemStack resultItem;

    public ItemEventJS(Player player, ItemStack item, LevelAccessor level) {
        this.player = player;
        this.item = item;
        this.level = level;
    }

    public ItemEventJS withHand(InteractionHand hand) {
        this.hand = hand;
        return this;
    }

    public ItemEventJS withTarget(Entity target) {
        this.target = target;
        return this;
    }

    public ItemEventJS withEntityItem(ItemEntity entityItem) {
        this.entityItem = entityItem;
        return this;
    }

    public ItemEventJS withResultItem(ItemStack resultItem) {
        this.resultItem = resultItem;
        return this;
    }
}
