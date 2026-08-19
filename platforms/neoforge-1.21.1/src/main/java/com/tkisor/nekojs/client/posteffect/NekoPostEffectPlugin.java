package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

/**
 * Client-only plugin (feature 8b), 1.21.1 mirror: registers the {@code PostEffects} binding
 * for CLIENT scripts. {@code PostEffectsJS} implements {@code Binding}, so its
 * {@code close()} drops runtime-registered definitions on CLIENT reload.
 */
@RegisterNekoJSPlugin(clientOnly = true)
public class NekoPostEffectPlugin implements NekoJSPlugin {

    @Override
    public void registerBinding(BindingRegistry registry) {
        if (registry.scriptType() == ScriptType.CLIENT) {
            registry.register(new PostEffectsJS());
        }
    }
}
