package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

import java.util.ArrayList;
import java.util.List;

/**
 * 药水注册器（{@code StartupEvents.registry('potion')}）。
 *
 * <p>脚本通过 {@code effect(mobEffect, duration, amplifier, ambient, visible)}
 * 添加效果实例；默认空效果（用于酿造基料等）。
 */
public class PotionBuilderJS {
    @Getter
    private final Identifier location;

    private final List<MobEffectInstance> effects = new ArrayList<>();

    public PotionBuilderJS(Identifier location) {
        this.location = location;
    }

    /**
     * 添加一个效果实例。{@code effect} 为效果 id 字符串或 Holder。
     *
     * @param effect        效果（id 字符串如 {@code 'minecraft:speed'}）
     * @param durationTicks 持续时间（tick）
     * @param amplifier     增幅等级（0 起）
     * @param ambient       是否环境效果（HUD 半透明图标）
     * @param visible       是否显示粒子 / 图标
     */
    public PotionBuilderJS effect(Object effect, int durationTicks, int amplifier, boolean ambient, boolean visible) {
        Holder<MobEffect> holder = resolveEffect(effect);
        if (holder != null) {
            effects.add(new MobEffectInstance(holder, durationTicks, amplifier, ambient, visible));
        }
        return this;
    }

    /** 简便重载：默认非环境、可见。 */
    public PotionBuilderJS effect(Object effect, int durationTicks, int amplifier) {
        return effect(effect, durationTicks, amplifier, false, true);
    }

    public Potion create() {
        // 用 namespace:path 作为 Potion 的内部名（用于酿造 / 语言 key 引用）
        return new Potion(location.getNamespace() + "." + location.getPath(), effects.toArray(new MobEffectInstance[0]));
    }

    private static Holder<MobEffect> resolveEffect(Object value) {
        if (value instanceof Holder<?> holder && holder.value() instanceof MobEffect mobEffect) {
            return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
        }
        if (value instanceof String id) {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, Identifier.parse(id));
            // 26.x 的 Registry#get(ResourceKey) 返回 Optional<Reference>，直接取 Holder。
            return BuiltInRegistries.MOB_EFFECT.get(key).map(ref -> (Holder<MobEffect>) ref).orElse(null);
        }
        return null;
    }
}
