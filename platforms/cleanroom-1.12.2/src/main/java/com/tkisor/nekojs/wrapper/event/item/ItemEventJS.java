package com.tkisor.nekojs.wrapper.event.item;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

/**
 * 统一的物品事件 wrapper（跨平台字段名一致）。
 *
 * <p>1.12.2 版本：字段类型用 1.12.2 原生类（{@code EntityPlayer}/{@code EnumHand}/{@code EntityItem}/{@code World}），
 * 但 getter 名与 NeoForge 一致（{@code player}、{@code item}、{@code level}、{@code hand}、{@code target}、
 * {@code entityItem}、{@code resultItem}）。
 *
 * <p>{@code item}（ItemStack）同时作为按 Item 分发事件的 dispatch key 来源。
 */
@Getter
public class ItemEventJS {
    private final EntityPlayer player;
    private final ItemStack item;
    private final World level;
    // 事件特定字段（nullable）
    private EnumHand hand;
    private Entity target;
    private EntityItem entityItem;
    private ItemStack resultItem;

    public ItemEventJS(EntityPlayer player, ItemStack item, World level) {
        this.player = player;
        this.item = item;
        this.level = level;
    }

    public ItemEventJS withHand(EnumHand hand) {
        this.hand = hand;
        return this;
    }

    public ItemEventJS withTarget(Entity target) {
        this.target = target;
        return this;
    }

    public ItemEventJS withEntityItem(EntityItem entityItem) {
        this.entityItem = entityItem;
        return this;
    }

    public ItemEventJS withResultItem(ItemStack resultItem) {
        this.resultItem = resultItem;
        return this;
    }
}
