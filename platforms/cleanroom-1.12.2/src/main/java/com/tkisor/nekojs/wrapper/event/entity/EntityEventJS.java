package com.tkisor.nekojs.wrapper.event.entity;

import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.Collection;

/**
 * 统一的实体事件 wrapper（跨平台字段名一致）。
 *
 * <p>1.12.2 版本：字段类型用 1.12.2 原生类（{@code Entity}/{@code DamageSource}/{@code EntityItem}），
 * 但 getter 名与 NeoForge 一致。
 */
@Getter
public class EntityEventJS {
    private final Entity entity;
    // 事件特定字段（nullable）
    private World level;
    private DamageSource source;
    private Float amount;
    private Collection<EntityItem> drops;
    private ItemStack item;
    private Integer duration;
    private ItemStack result;

    public EntityEventJS(Entity entity) {
        this.entity = entity;
    }

    public EntityEventJS withLevel(World level) {
        this.level = level;
        return this;
    }

    public EntityEventJS withSource(DamageSource source) {
        this.source = source;
        return this;
    }

    public EntityEventJS withAmount(float amount) {
        this.amount = amount;
        return this;
    }

    public EntityEventJS withDrops(Collection<EntityItem> drops) {
        this.drops = drops;
        return this;
    }

    public EntityEventJS withItem(ItemStack item) {
        this.item = item;
        return this;
    }

    public EntityEventJS withDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public EntityEventJS withResult(ItemStack result) {
        this.result = result;
        return this;
    }
}
