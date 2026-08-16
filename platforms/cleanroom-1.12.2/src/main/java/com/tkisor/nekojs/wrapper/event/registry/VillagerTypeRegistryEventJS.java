package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.registry.VillagerTypeBuilderJS;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 村民职业注册事件对象（{@code StartupEvents.registry('villagerType')}）。
 */
public class VillagerTypeRegistryEventJS {

    private final RegistryEvent.Register<VillagerRegistry.VillagerProfession> rawEvent;
    private final List<VillagerTypeBuilderJS> builders = new ArrayList<>();

    /** 包装原始 Forge 注册事件。 */
    public VillagerTypeRegistryEventJS(RegistryEvent.Register<VillagerRegistry.VillagerProfession> rawEvent) {
        this.rawEvent = rawEvent;
    }

    /** 创建一个村民职业 builder。 */
    @Doc("Creates a new villager profession builder.")
    @Param(name = "id", value = "profession id like 'my_profession' or 'mymod:my_profession'")
    @Return("a new VillagerTypeBuilderJS for chaining; the profession is registered when the event completes")
    public VillagerTypeBuilderJS create(String id) {
        VillagerTypeBuilderJS builder = new VillagerTypeBuilderJS(id);
        builders.add(builder);
        return builder;
    }

    /** 创建并一步配置村民职业 builder。 */
    @Doc("Creates a new villager profession builder and configures it in one call.")
    @Param(name = "id", value = "profession id like 'my_profession' or 'mymod:my_profession'")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String id, Consumer<VillagerTypeBuilderJS> consumer) {
        VillagerTypeBuilderJS builder = create(id);
        consumer.accept(builder);
    }

    /** 注册全部村民职业。 */
    @Doc("Registers all villager professions created in this event into the Forge registry.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
    public void registerAll() {
        for (VillagerTypeBuilderJS builder : builders) {
            rawEvent.getRegistry().register(builder.build());
        }
        builders.clear();
    }
}
