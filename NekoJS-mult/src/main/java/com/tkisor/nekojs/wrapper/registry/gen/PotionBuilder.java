package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

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
 * 药水 builder：效果实例经 {@code effect(...)} 追加（additive 配置保留方法面）；
 * 默认空效果（用于酿造基料等）。
 * <pre>
 * event.potion('mymod:strong_health', b =&gt; { b.effect('minecraft:regeneration', 400, 1) })
 * </pre>
 */
public class PotionBuilder extends RegistryObjectBuilder<Potion> {

    private final List<MobEffectInstance> effects = new ArrayList<>();

    public PotionBuilder(Identifier id) {
        super(id);
    }

    /**
     * 追加一个效果实例。
     *
     * @param effect        效果（id 字符串如 {@code 'minecraft:speed'} 或 Holder）
     * @param durationTicks 持续时间（tick）
     * @param amplifier     增幅等级（0 起）
     * @param ambient       是否环境效果（HUD 半透明图标）
     * @param visible       是否显示粒子 / 图标
     */
    public void effect(Object effect, int durationTicks, int amplifier, boolean ambient, boolean visible) {
        Holder<MobEffect> holder = resolveEffect(effect);
        if (holder != null) {
            effects.add(new MobEffectInstance(holder, durationTicks, amplifier, ambient, visible));
        }
    }

    /** 简便重载：默认非环境、可见。 */
    public void effect(Object effect, int durationTicks, int amplifier) {
        effect(effect, durationTicks, amplifier, false, true);
    }

    @Override
    public Potion build() {
        // 用 namespace:path 作为 Potion 的内部名（用于酿造 / 语言 key 引用）
        return new Potion(id.getNamespace() + "." + id.getPath(), effects.toArray(new MobEffectInstance[0]));
    }

    private static Holder<MobEffect> resolveEffect(Object value) {
        if (value instanceof Holder<?> holder && holder.value() instanceof MobEffect mobEffect) {
            return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
        }
        if (value instanceof String effectId) {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, Identifier.parse(effectId));
            // 26.x 的 Registry#get(ResourceKey) 返回 Optional<Reference>，直接取 Holder。
//? if >=26 {
            return BuiltInRegistries.MOB_EFFECT.get(key).map(ref -> (Holder<MobEffect>) ref).orElse(null);
//?} else {
/*            MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.get(key);
            return mobEffect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(mobEffect);
*///?}
        }
        return null;
    }
}
