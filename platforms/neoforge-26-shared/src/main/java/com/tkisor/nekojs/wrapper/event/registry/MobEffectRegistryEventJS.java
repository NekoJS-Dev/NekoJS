package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.wrapper.registry.MobEffectBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态效果注册事件（{@code StartupEvents.registry('mobEffect')}）。
 */
public class MobEffectRegistryEventJS {

    private final RegisterEvent rawEvent;
    private final List<MobEffectBuilderJS> builders = new ArrayList<>();

    public MobEffectRegistryEventJS(RegisterEvent rawEvent) {
        this.rawEvent = rawEvent;
    }

    public MobEffectBuilderJS create(Identifier id) {
        MobEffectBuilderJS builder = new MobEffectBuilderJS(id);
        builders.add(builder);
        return builder;
    }
    public void create(String id, java.util.function.Consumer<MobEffectBuilderJS> consumer) {
        MobEffectBuilderJS builder = create(Identifier.parse(id));
        consumer.accept(builder);
    }

    public void registerAll() {
        for (MobEffectBuilderJS builder : builders) {
            rawEvent.register(Registries.MOB_EFFECT, builder.getLocation(), builder::create);
        }
    }
}
