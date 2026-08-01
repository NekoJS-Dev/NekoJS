package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.PotionBuilderJS;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 药水注册事件对象（{@code StartupEvents.registry('potion')}）。
 *
 * <p>注意：1.12.2 的 POTION_TYPES 注册事件在 POTIONS（状态效果）之后触发，
 * 因此脚本可在 builder 里引用同批次注册的 mobEffect。
 */
public class PotionRegistryEventJS {

    private final RegistryEvent.Register<PotionType> rawEvent;
    private final List<PotionBuilderJS> builders = new ArrayList<>();

    public PotionRegistryEventJS(RegistryEvent.Register<PotionType> rawEvent) {
        this.rawEvent = rawEvent;
    }

    public PotionBuilderJS create(String id) {
        PotionBuilderJS builder = new PotionBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    public void create(String id, Consumer<PotionBuilderJS> consumer) {
        PotionBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    public void registerAll() {
        for (PotionBuilderJS builder : builders) {
            PotionType potionType = builder.build();
            potionType.setRegistryName(new ResourceLocation(builder.getRegistryName()));
            rawEvent.getRegistry().register(potionType);
        }
        builders.clear();
    }
}
