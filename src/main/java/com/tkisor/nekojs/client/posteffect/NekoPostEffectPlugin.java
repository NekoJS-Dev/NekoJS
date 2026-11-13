// TODO(fabric): no equivalent implementation on the fabric side yet; remove the whole-file guard once the port is done
//? if neoforge {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

/**
 * Client-only plugin registering the {@code PostEffects} runtime binding for CLIENT scripts.
 * Declaration ownership and replacement belong to {@code PostEffectDomainOwner}; closing
 * the binding does not clear the current runtime picture.
 */
@RegisterNekoJSPlugin(clientOnly = true)
public class NekoPostEffectPlugin implements NekoJSPlugin, com.tkisor.nekojs.core.plugin.BindingsPoint.Contributor {

    @Override
    public void registerBinding(BindingRegistry registry) {
        if (registry.scriptType() == ScriptType.CLIENT) {
            registry.register(new PostEffectsJS());
        }
    }
}
//?}
