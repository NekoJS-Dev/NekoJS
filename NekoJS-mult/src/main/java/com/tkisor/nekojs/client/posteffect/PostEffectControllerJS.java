//? if >=26 {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.posteffect.PostEffectCapabilities;
import com.tkisor.nekojs.api.posteffect.PostEffectCommandResult;
import com.tkisor.nekojs.api.posteffect.PostEffectRendererAdapter;

/**
 * Script-visible state controller for post effects. Definitions are collected by
 * ClientEvents.postEffects; this binding only exposes runtime state commands.
 */
public final class PostEffectControllerJS implements Binding {
    private final PostEffectRendererAdapter adapter;

    public PostEffectControllerJS(PostEffectRendererAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public String name() {
        return "PostEffects";
    }

    @Override
    public Object value() {
        return this;
    }

    public PostEffectCommandResult activate(String id) {
        return adapter.activate(id);
    }

    public PostEffectCommandResult clear() {
        return adapter.clear();
    }

    public PostEffectCommandResult toggle(String id) {
        return adapter.toggle(id);
    }

    public String current() {
        return adapter.current();
    }

    public boolean isActive() {
        return adapter.isActive();
    }

    public PostEffectCapabilities capabilities() {
        return adapter.capabilities();
    }

    @Override
    public void close(ScriptType scriptType) {
        // Preserve the previously committed renderer state if a candidate CLIENT reload fails.
        // The committed PostEffectScriptBridge reconciles only after successful reload.
    }
}
//?}
