// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
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
                "Read-only, generation-bound villager trade queries. Declare trades through ServerEvents.tradeDeclaration; the platform adapter applies the candidate batch only after successful preflight and commit.",
                List.of("ServerEvents.tradeDeclaration(event => { event.add('minecraft:farmer/level_1', { cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 }) })", "VillagerTrades.query().describe()")));
    }
}
//?}
