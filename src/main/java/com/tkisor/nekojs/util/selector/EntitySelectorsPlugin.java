package com.tkisor.nekojs.util.selector;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

/**
 * Registers the global {@code EntitySelectors} binding: exposes the programmatic entity selector
 * entry point ({@link EntitySelectorsJS}) to SERVER and TEST scripts. Queries need a
 * {@code ServerLevel}, so client and startup environments get no binding; build selectors ahead
 * of time inside a server script instead.
 */
@RegisterNekoJSPlugin
public class EntitySelectorsPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor {

    @Override
    public void registerBinding(BindingRegistry registry) {
        if (registry.scriptType() == ScriptType.SERVER || registry.scriptType() == ScriptType.TEST) {
            registry.register("EntitySelectors", new EntitySelectorsJS());
        }
    }
}
