package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** 包装原始 Forge 注册事件。 */
    public PotionRegistryEventJS(RegistryEvent.Register<PotionType> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** 创建一个药水 builder。 */
    @Doc("Creates a new potion type builder.")
    @Param(name = "id", value = "registry id like 'my_potion' or 'mymod:my_potion'")
    @Return("a new PotionBuilderJS for chaining; the potion type is registered when the event completes")
    public PotionBuilderJS create(String id) {
        PotionBuilderJS builder = new PotionBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** 创建并一步配置药水 builder。 */
    @Doc("Creates a new potion type builder and configures it in one call.")
    @Param(name = "id", value = "registry id like 'my_potion' or 'mymod:my_potion'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<PotionBuilderJS> consumer) {
        PotionBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** 注册全部药水类型。 */
    @Doc("Registers all potion types created in this event into the Forge registry.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
    public void registerAll() {
        for (PotionBuilderJS builder : builders) {
            PotionType potionType = builder.build();
            potionType.setRegistryName(new ResourceLocation(builder.getRegistryName()));
            rawEvent.getRegistry().register(potionType);
        }
        builders.clear();
    }
}
