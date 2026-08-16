package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** 包装原始 Forge 注册事件。 */
    public EnchantmentRegistryEventJS(RegistryEvent.Register<Enchantment> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** 创建一个附魔 builder。 */
    @Doc("Creates a new enchantment builder.")
    @Param(name = "id", value = "registry id like 'my_enchant' or 'mymod:my_enchant'")
    @Return("a new EnchantmentBuilderJS for chaining; the enchantment is registered when the event completes")
    public EnchantmentBuilderJS create(String id) {
        EnchantmentBuilderJS builder = new EnchantmentBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** 创建并一步配置附魔 builder。 */
    @Doc("Creates a new enchantment builder and configures it in one call.")
    @Param(name = "id", value = "registry id like 'my_enchant' or 'mymod:my_enchant'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<EnchantmentBuilderJS> consumer) {
        EnchantmentBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** 注册全部附魔。 */
    @Doc("Registers all enchantments created in this event into the Forge registry.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
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
