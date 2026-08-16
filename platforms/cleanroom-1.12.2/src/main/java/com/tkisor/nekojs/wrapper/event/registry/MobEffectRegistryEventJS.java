package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

    /** 包装原始 Forge 注册事件。 */
    public MobEffectRegistryEventJS(RegistryEvent.Register<Potion> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** 创建一个状态效果 builder。 */
    @Doc("Creates a new mob effect builder.")
    @Param(name = "id", value = "registry id like 'my_effect' or 'mymod:my_effect'")
    @Return("a new MobEffectBuilderJS for chaining; the effect is registered when the event completes")
    public MobEffectBuilderJS create(String id) {
        MobEffectBuilderJS builder = new MobEffectBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** 创建并一步配置状态效果 builder。 */
    @Doc("Creates a new mob effect builder and configures it in one call.")
    @Param(name = "id", value = "registry id like 'my_effect' or 'mymod:my_effect'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<MobEffectBuilderJS> consumer) {
        MobEffectBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** 注册全部状态效果。 */
    @Doc("Registers all mob effects created in this event into the Forge registry.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
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
