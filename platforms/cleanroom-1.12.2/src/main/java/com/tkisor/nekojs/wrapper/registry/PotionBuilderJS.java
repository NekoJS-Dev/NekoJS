package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.12.2 药水注册器（{@code StartupEvents.registry('potion')}）。
 *
 * <p>1.12.2 的「药水」是 {@link PotionType}（效果组合，注册到 POTION_TYPES），
 * 与「状态效果」（{@link Potion}，见 MobEffectBuilderJS）区分。
 * 脚本通过 {@code effect(mobEffect, duration, amplifier, ambient, visible)} 添加
 * {@link PotionEffect}；{@code effect} 为效果 id 字符串（引用已注册的 Potion）。
 */
public class PotionBuilderJS {

    private final String registryName;
    private String baseName;
    private final List<PotionEffect> effects = new ArrayList<>();

    public PotionBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** 基名（如 {@code 'swiftness'}，决定 {@code potion.<base>} 翻译键前缀）。 */
    public PotionBuilderJS baseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    /** 添加效果实例（effect 为效果 id 字符串，如 {@code 'minecraft:speed'}）。 */
    public PotionBuilderJS effect(String effectId, int durationTicks, int amplifier) {
        return effect(effectId, durationTicks, amplifier, false, true);
    }

    public PotionBuilderJS effect(String effectId, int durationTicks, int amplifier, boolean ambient, boolean showParticles) {
        Potion potion = resolvePotion(effectId);
        if (potion != null) {
            effects.add(new PotionEffect(potion, durationTicks, amplifier, ambient, showParticles));
        }
        return this;
    }

    public String getRegistryName() {
        return registryName;
    }

    @SuppressWarnings("deprecation")
    public PotionType build() {
        PotionEffect[] array = effects.toArray(new PotionEffect[0]);
        return baseName != null ? new PotionType(baseName, array) : new PotionType(array);
    }

    private static Potion resolvePotion(String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("minecraft", id);
        return ForgeRegistries.POTIONS.getValue(rl);
    }
}
