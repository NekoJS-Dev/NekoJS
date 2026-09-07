// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.VillagerTradesEvents;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
//? if <26 {
/*import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.bindings.static_access.VillagerTradesJS;
import java.util.List;
*///?}

/**
 * Registers the {@code VillagerTrades} binding for SERVER scripts (runtime villager trade
 * modification). Standalone plugin so NekoJSCorePlugin stays untouched.
 */
@RegisterNekoJSPlugin
public class VillagerTradesPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor, com.tkisor.nekojs.core.plugin.TypeDocsPoint.Contributor, com.tkisor.nekojs.core.plugin.EventsPoint.Contributor {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(VillagerTradesEvents.GROUP);
    }

    @Override
    public void registerBinding(BindingRegistry registry) {
//? if <26 {
/*        registry.register(ScriptType.SERVER, "VillagerTrades", new VillagerTradesJS());
*///?}
    }

    @Override
    public void registerTypeDocs(TypeDocsRegister registry) {
//? if <26 {
/*        registry.register(TypeDocCatalogEntry.binding(
                ScriptType.SERVER,
                "VillagerTrades",
                null,
                "Server-side villager / wandering trader trade additions. Legacy static trade adapter.",
                List.of("VillagerTrades.add('minecraft:farmer/level_1', { cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 })")));
*///?}
    }
}
//?}
