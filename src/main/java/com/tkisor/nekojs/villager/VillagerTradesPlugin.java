// TODO(loader-port): deferred to the LoaderBridge fabric port
//? if neoforge {
package com.tkisor.nekojs.villager;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import com.tkisor.nekojs.bindings.static_access.VillagerTradesJS;
import java.util.List;

/**
 * Registers the {@code VillagerTrades} binding for SERVER scripts (runtime villager trade
 * modification). Standalone plugin so NekoJSCorePlugin stays untouched.
 */
@RegisterNekoJSPlugin
public class VillagerTradesPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor, com.tkisor.nekojs.core.plugin.TypeDocsPoint.Contributor {

    @Override
    public void registerBinding(BindingRegistry registry) {
        registry.register(ScriptType.SERVER, "VillagerTrades", new VillagerTradesJS());
    }

    @Override
    public void registerTypeDocs(TypeDocsRegister registry) {
        registry.register(TypeDocCatalogEntry.binding(
                ScriptType.SERVER,
                "VillagerTrades",
                null,
//? if >=26 {
                "Server-side villager / wandering trader trade additions. Trades are staged during script load and flushed into the trade registries when the reload cycle finishes.",
//?} else {
/*                "Server-side villager / wandering trader trade additions. Trades are staged during script load and flushed when the reload cycle finishes; villagers offer them on their next restock.",
*///?}
                List.of("VillagerTrades.add('minecraft:farmer/level_1', { cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 })")));
    }
}
//?}
