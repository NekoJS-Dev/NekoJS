package com.tkisor.nekojs.wrapper.event.entity;

import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collection;

/**
 * 统一的实体事件 wrapper（跨平台字段名一致）。
 *
 * <p>脚本侧 {@code event.entity}、{@code event.source}、{@code event.amount}、{@code event.item} 等
 * 在 NeoForge 与 Cleanroom 上一致。事件特定字段仅在对应事件上非 null。
 */
@Getter
public class EntityEventJS {
    private final Entity entity;
    // 事件特定字段（nullable）
    private Level level;
    private DamageSource source;
    private Float amount;
    private Collection<ItemEntity> drops;
    private Integer lootingLevel;
    private ItemStack item;
    private Integer duration;
    private ItemStack result;

    public EntityEventJS(Entity entity) {
        this.entity = entity;
    }

    public EntityEventJS withLevel(Level level) {
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

    public EntityEventJS withDrops(Collection<ItemEntity> drops) {
        this.drops = drops;
        return this;
    }

    public EntityEventJS withLootingLevel(int lootingLevel) {
        this.lootingLevel = lootingLevel;
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
