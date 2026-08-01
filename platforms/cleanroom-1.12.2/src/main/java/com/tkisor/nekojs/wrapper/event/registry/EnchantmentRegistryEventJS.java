package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.EnchantmentBuilderJS;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 附魔注册事件对象（{@code StartupEvents.registry('enchantment')}）。
 */
public class EnchantmentRegistryEventJS {

    private final RegistryEvent.Register<Enchantment> rawEvent;
    private final List<EnchantmentBuilderJS> builders = new ArrayList<>();

    public EnchantmentRegistryEventJS(RegistryEvent.Register<Enchantment> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public EnchantmentBuilderJS create(String id) {
        EnchantmentBuilderJS builder = new EnchantmentBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<EnchantmentBuilderJS> consumer) {
        EnchantmentBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (EnchantmentBuilderJS builder : builders) {
            Enchantment enchantment = builder.build();
            enchantment.setRegistryName(new ResourceLocation(builder.getRegistryName()));
            enchantment.setName(builder.getRegistryName());
            rawEvent.getRegistry().register(enchantment);
        }
        builders.clear();
    }
}
