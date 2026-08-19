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
 * Registers the {@code VillagerTrades} binding for SERVER scripts (1.21.1 static
 * trade-map flavor). Standalone plugin so NekoJSCorePlugin stays untouched.
 */
@RegisterNekoJSPlugin
public class VillagerTradesPlugin implements NekoJSPlugin {

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
                "Server-side villager / wandering trader trade additions. Trades are staged during script load and flushed when the reload cycle finishes; villagers offer them on their next restock.",
                List.of("VillagerTrades.add('minecraft:farmer/level_1', { cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 })")));
    }
}
