//? if fabric {
package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.posteffect.PostEffectCapabilities;
import com.tkisor.nekojs.api.posteffect.PostEffectCatalog;
import com.tkisor.nekojs.api.posteffect.PostEffectCommandResult;
import com.tkisor.nekojs.api.posteffect.PostEffectRendererAdapter;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;

/** Fabric composition root; renderer activation remains explicit unsupported until the 26.x hook lands. */
public final class FabricPostEffectScriptBridge {
    private static final Adapter ADAPTER = new Adapter();

    private FabricPostEffectScriptBridge() {}

    public static PostEffectCatalog reconcile() {
        PostEffectCatalog catalog = FabricClientEventBindings.collectPostEffects();
        ADAPTER.reconcile(catalog);
        return catalog;
    }

    public static PostEffectRendererAdapter adapter() {
        return ADAPTER;
    }

    private static final class Adapter implements PostEffectRendererAdapter {
        @Override
        public PostEffectCapabilities capabilities() {
            return PostEffectCapabilities.unsupported();
        }

        @Override
        public PostEffectCommandResult reconcile(PostEffectCatalog catalog) {
            return PostEffectCommandResult.cleared();
        }

        @Override
        public PostEffectCommandResult activate(String id) {
            return PostEffectCommandResult.unsupportedResource(id);
        }

        @Override
        public PostEffectCommandResult clear() {
            return PostEffectCommandResult.alreadyClear();
        }

        @Override
        public PostEffectCommandResult toggle(String id) {
            return PostEffectCommandResult.unsupportedResource(id);
        }

        @Override
        public String current() {
            return null;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void invalidate() {
        }
    }
}
//?}
