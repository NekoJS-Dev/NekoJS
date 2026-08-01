package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.MobEffectBuilderJS;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 状态效果注册事件对象（{@code StartupEvents.registry('mobEffect')}）。
 */
public class MobEffectRegistryEventJS {

    private final RegistryEvent.Register<Potion> rawEvent;
    private final List<MobEffectBuilderJS> builders = new ArrayList<>();

    public MobEffectRegistryEventJS(RegistryEvent.Register<Potion> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public MobEffectBuilderJS create(String id) {
        MobEffectBuilderJS builder = new MobEffectBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<MobEffectBuilderJS> consumer) {
        MobEffectBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (MobEffectBuilderJS builder : builders) {
            Potion potion = builder.build();
            potion.setRegistryName(new ResourceLocation(builder.getRegistryName()));
            potion.setPotionName(builder.getRegistryName());
            rawEvent.getRegistry().register(potion);
        }
        builders.clear();
    }
}
