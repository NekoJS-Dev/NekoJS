//? if neoforge && >=26 {
package com.tkisor.nekojs.client.posteffect;

import com.tkisor.nekojs.api.posteffect.PostEffectCapabilities;
import com.tkisor.nekojs.api.posteffect.PostEffectCatalog;
import com.tkisor.nekojs.api.posteffect.PostEffectCommandResult;
import com.tkisor.nekojs.api.posteffect.PostEffectDefinition;
import com.tkisor.nekojs.api.posteffect.PostEffectRendererAdapter;
import com.tkisor.nekojs.core.contribution.CommittedContributionLifecycle;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.wrapper.event.client.PostEffectEventJS;
import net.minecraft.resources.Identifier;

/** NeoForge composition root for the declarative post-effect catalog. */
public final class PostEffectScriptBridge {
    private static final Adapter ADAPTER = new Adapter();
    private static final CommittedContributionLifecycle<PostEffectEventJS, PostEffectCatalog, Boolean> LIFECYCLE =
            new CommittedContributionLifecycle<>(PostEffectEventJS::new, PostEffectEventJS::catalog,
                    (catalog, ignored) -> ADAPTER.reconcile(catalog));

    private PostEffectScriptBridge() {}

    /** Starts a candidate before a full CLIENT script reload. */
    public static void beginReload() {
        LIFECYCLE.begin();
    }

    /** Posts and commits the catalog after a successful CLIENT script reload. */
    public static PostEffectCatalog reconcile() {
        if (!LIFECYCLE.isCollecting()) LIFECYCLE.begin();
        ClientEvents.POST_EFFECTS.post(LIFECYCLE.collect());
        LIFECYCLE.seal();
        LIFECYCLE.registryReady(Boolean.TRUE);
        return LIFECYCLE.committedPlan();
    }

    public static void stop() {
        LIFECYCLE.stop();
    }

    public static PostEffectRendererAdapter adapter() {
        return ADAPTER;
    }

    private static final class Adapter implements PostEffectRendererAdapter {
        private PostEffectCatalog catalog = PostEffectCatalog.empty();

        @Override
        public PostEffectCapabilities capabilities() {
            return PostEffectCapabilities.resourceOnly();
        }

        @Override
        public PostEffectCommandResult reconcile(PostEffectCatalog next) {
            PostEffectManager.invalidatePostChainCache();
            PostEffectManager.clearRegistered();
            catalog = next;
            for (PostEffectDefinition definition : next.values()) {
                if (definition.isRuntime()) {
                    PostEffectManager.register(Identifier.parse(definition.id()), definition.chainJson(),
                            toIdentifiers(definition.fragmentShaders()), toIdentifiers(definition.vertexShaders()));
                }
            }
            for (PostEffectDefinition definition : next.values()) {
                if (definition.desiredActive()) return activate(definition.id());
            }
            return clear();
        }

        @Override
        public PostEffectCommandResult activate(String id) {
            PostEffectDefinition definition = catalog.definition(id);
            if (definition == null) return PostEffectCommandResult.notFound(id);
            if (definition.isRuntime()) return PostEffectCommandResult.unsupportedRuntime(id);
            return PostEffectManager.set(Identifier.parse(id))
                    ? PostEffectCommandResult.activated(id)
                    : PostEffectCommandResult.failed(id, "Resource post effect could not be activated: " + id);
        }

        @Override
        public PostEffectCommandResult clear() {
            return PostEffectManager.clear() ? PostEffectCommandResult.cleared() : PostEffectCommandResult.alreadyClear();
        }

        @Override
        public PostEffectCommandResult toggle(String id) {
            PostEffectDefinition definition = catalog.definition(id);
            if (definition == null) return PostEffectCommandResult.notFound(id);
            if (definition.isRuntime()) return PostEffectCommandResult.unsupportedRuntime(id);
            return PostEffectManager.toggle(Identifier.parse(id))
                    ? PostEffectCommandResult.activated(id) : PostEffectCommandResult.cleared();
        }

        @Override public String current() {
            Identifier current = PostEffectManager.current();
            return current == null ? null : current.toString();
        }

        @Override public boolean isActive() { return PostEffectManager.isActive(); }
        @Override public void invalidate() { PostEffectManager.invalidatePostChainCache(); }

        private static java.util.Map<Identifier, String> toIdentifiers(java.util.Map<String, String> sources) {
            java.util.Map<Identifier, String> result = new java.util.LinkedHashMap<>();
            sources.forEach((id, source) -> result.put(Identifier.parse(id), source));
            return result;
        }
    }
}
//?}
