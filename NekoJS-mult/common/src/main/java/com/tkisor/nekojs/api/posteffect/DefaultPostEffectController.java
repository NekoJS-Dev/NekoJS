package com.tkisor.nekojs.api.posteffect;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Default delegation-only controller; all renderer details remain behind the adapter. */
public final class DefaultPostEffectController implements PostEffectController {
    private final PostEffectRendererAdapter adapter;

    public DefaultPostEffectController(PostEffectRendererAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    @Override
    public PostEffectCommandResult activate(String id) {
        return adapter.activate(id);
    }

    @Override
    public PostEffectCommandResult clear() {
        return adapter.clear();
    }

    @Override
    public PostEffectCommandResult toggle(String id) {
        return adapter.toggle(id);
    }

    @Override
    @Nullable
    public String current() {
        return adapter.current();
    }

    @Override
    public boolean isActive() {
        return adapter.isActive();
    }

    @Override
    public PostEffectCapabilities capabilities() {
        return adapter.capabilities();
    }

}
