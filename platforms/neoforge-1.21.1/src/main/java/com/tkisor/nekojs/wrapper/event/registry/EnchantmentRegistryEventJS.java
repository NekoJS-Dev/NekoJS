package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.EnchantmentBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 附魔注册事件（{@code StartupEvents.registry('enchantment')}）。
 *
 * <p>与大多数注册事件不同：{@code registerAll} 需要把 {@link RegisterEvent} 透传给
 * {@link EnchantmentBuilderJS#create(RegisterEvent)}，因为附魔组装要从 item 注册表
 * 解析物品标签为 {@code HolderSet}。
 */
public class EnchantmentRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<EnchantmentBuilderJS> builders = new ArrayList<>();

    public EnchantmentRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public EnchantmentBuilderJS create(ResourceLocation id) {
        EnchantmentBuilderJS builder = new EnchantmentBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void registerAll() {
        for (EnchantmentBuilderJS builder : builders) {
            rawEvent.register(Registries.ENCHANTMENT, builder.getLocation(), () -> builder.create(rawEvent));
        }
    }
}
