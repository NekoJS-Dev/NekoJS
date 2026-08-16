package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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
@Doc("Builder for registering a new potion type (an effect combination); obtain it from RegistryEvents.potion.create(id).")
@Doc("Distinct from mob effects (Potion): a PotionType bundles registered effects into a potion item.")
public class PotionBuilderJS {

    private final String registryName;
    private String baseName;
    private final List<PotionEffect> effects = new ArrayList<>();

    public PotionBuilderJS(String registryName) {
        this.registryName = registryName;
    }

    /** 基名（如 {@code 'swiftness'}，决定 {@code potion.<base>} 翻译键前缀）。 */
    @Doc("Sets the potion base name, controlling the 'potion.<base>' translation key prefix.")
    @Param(name = "baseName", value = "base name like 'swiftness' or 'healing'")
    @Return("this builder, for chaining")
    public PotionBuilderJS baseName(String baseName) {
        this.baseName = baseName;
        return this;
    }

    /** 添加效果实例（effect 为效果 id 字符串，如 {@code 'minecraft:speed'}）。 */
    @Doc("Adds a mob effect to the potion (non-ambient, with particles).")
    @Param(name = "effectId", value = "effect id like 'minecraft:speed'; unknown ids are ignored")
    @Param(name = "durationTicks", value = "effect duration in ticks (20 ticks = 1 second)")
    @Param(name = "amplifier", value = "effect level; 0 is level I")
    @Return("this builder, for chaining")
    public PotionBuilderJS effect(String effectId, int durationTicks, int amplifier) {
        return effect(effectId, durationTicks, amplifier, false, true);
    }

    /** 添加效果实例（完整参数）。 */
    @Doc("Adds a mob effect to the potion with full options.")
    @Param(name = "effectId", value = "effect id like 'minecraft:speed'; unknown ids are ignored")
    @Param(name = "durationTicks", value = "effect duration in ticks (20 ticks = 1 second)")
    @Param(name = "amplifier", value = "effect level; 0 is level I")
    @Param(name = "ambient", value = "true for an ambient effect (dimmer particles, like a beacon)")
    @Param(name = "showParticles", value = "false hides the effect particles")
    @Return("this builder, for chaining")
    public PotionBuilderJS effect(String effectId, int durationTicks, int amplifier, boolean ambient, boolean showParticles) {
        Potion potion = resolvePotion(effectId);
        if (potion != null) {
            effects.add(new PotionEffect(potion, durationTicks, amplifier, ambient, showParticles));
        }
        return this;
    }

    /** 注册名。 */
    @Doc("Gets the registry name of the potion type being built.")
    @Return("the registry name string")
    public String getRegistryName() {
        return registryName;
    }

    /** 构建 PotionType 实例（不注册）。 */
    @Doc("Builds the PotionType combining the added effects; registration happens when the event completes.")
    @Return("the configured potion type")
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
