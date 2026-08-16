package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.wrapper.registry.CreativeTabBuilderJS;
import net.minecraft.creativetab.CreativeTabs;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.12.2 创造标签页注册事件对象（{@code StartupEvents.registry('creativeModeTab')}）。
 *
 * <p>1.12.2 的 {@link CreativeTabs} 无 registry 事件——实例构造即加入静态数组。
 * 本事件由 BLOCK 分支开头（同 FLUID 模式）post，{@code registerAll} 直接构造。
 * 构造出的标签页可通过 {@code ItemBuilderJS.creativeTab(...)} 挂到物品上。
 */
public class CreativeTabRegistryEventJS {

    private final List<CreativeTabBuilderJS> builders = new ArrayList<>();

    /** 创建一个创造标签页 builder。 */
    @Doc("Creates a new creative tab builder.")
    @Param(name = "label", value = "tab label shown in the creative menu, e.g. 'My Tab'")
    @Return("a new CreativeTabBuilderJS for chaining; the tab is created when the event completes")
    public CreativeTabBuilderJS create(String label) {
        CreativeTabBuilderJS builder = new CreativeTabBuilderJS(label);
        builders.add(builder);
        return builder;
    }

    /** 创建并一步配置创造标签页 builder。 */
    @Doc("Creates a new creative tab builder and configures it in one call.")
    @Param(name = "label", value = "tab label shown in the creative menu")
    @Param(name = "consumer", value = "callback receiving the builder for configuration")
    public void create(String label, Consumer<CreativeTabBuilderJS> consumer) {
        CreativeTabBuilderJS builder = create(label);
        consumer.accept(builder);
    }

    /** 构造全部标签页（加入静态数组）。 */
    @Doc("Creates all tabs registered in this event.")
    @Doc("Called automatically when the event completes; manual calls are normally unnecessary.")
    public void registerAll() {
        for (CreativeTabBuilderJS builder : builders) {
            builder.build();
        }
        builders.clear();
    }
}
