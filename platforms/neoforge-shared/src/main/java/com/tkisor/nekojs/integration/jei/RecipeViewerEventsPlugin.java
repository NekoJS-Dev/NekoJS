package com.tkisor.nekojs.integration.jei;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.RecipeViewerEvents;

/**
 * {@link RecipeViewerEvents} 组的门控注册插件：仅当客户端且 JEI 在场时注册，
 * 保证脚本看到的 {@code RecipeViewerEvents} 总是真正会触发——事件由
 * {@code RecipeViewerJeiPlugin}（JEI 侧插件）在 JEI 运行时重建时 post。
 *
 * <p>本类不引用任何 JEI 类型，无 JEI 环境也可安全加载（由 {@code requiredMods}
 * 过滤掉，组不注册，脚本侧不出现永不触发的空壳事件面）。
 */
@RegisterNekoJSPlugin(clientOnly = true, requiredMods = "jei")
public class RecipeViewerEventsPlugin implements NekoJSPlugin {

    @Override
    public void registerClientEvents(EventGroupRegistry registry) {
        registry.register(RecipeViewerEvents.GROUP);
    }
}
