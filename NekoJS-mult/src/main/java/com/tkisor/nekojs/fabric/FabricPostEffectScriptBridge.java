//? if fabric {
package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.posteffect.PostEffectCapabilities;
import com.tkisor.nekojs.api.posteffect.PostEffectCatalog;
import com.tkisor.nekojs.api.posteffect.PostEffectCommandResult;
import com.tkisor.nekojs.api.posteffect.PostEffectDefinition;
import com.tkisor.nekojs.api.posteffect.PostEffectRendererAdapter;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import com.tkisor.nekojs.core.contribution.CommittedContributionLifecycle;
import com.tkisor.nekojs.wrapper.event.client.PostEffectEventJS;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Fabric resource-backed post-effect adapter; runtime pipelines remain unsupported. */
public final class FabricPostEffectScriptBridge {
    private static final Adapter ADAPTER = new Adapter();
    private static final CommittedContributionLifecycle<PostEffectEventJS, PostEffectCatalog, Boolean> LIFECYCLE =
            new CommittedContributionLifecycle<>(PostEffectEventJS::new, PostEffectEventJS::catalog,
                    (catalog, ignored) -> ADAPTER.reconcile(catalog));

    private FabricPostEffectScriptBridge() {}
    public static void beginReload() { LIFECYCLE.begin(); }
    public static PostEffectCatalog reconcile() {
        if (!LIFECYCLE.isCollecting()) LIFECYCLE.begin();
        FabricClientEventBindings.POST_EFFECTS.post(LIFECYCLE.collect());
        LIFECYCLE.seal();
        LIFECYCLE.registryReady(Boolean.TRUE);
        return LIFECYCLE.committedPlan();
    }
    public static void stop() { LIFECYCLE.stop(); }
    public static PostEffectRendererAdapter adapter() { return ADAPTER; }

    private static final class Adapter implements PostEffectRendererAdapter {
        private PostEffectCatalog catalog = PostEffectCatalog.empty();
        private String current;
        @Override public PostEffectCapabilities capabilities() { return PostEffectCapabilities.resourceOnly(); }
        @Override public PostEffectCommandResult reconcile(PostEffectCatalog next) {
            catalog = next;
            clear();
            PostEffectDefinition desired = next.desiredActive();
            return desired == null ? PostEffectCommandResult.alreadyClear() : activate(desired.id());
        }
        @Override public PostEffectCommandResult activate(String id) {
            PostEffectDefinition definition = catalog.definition(id);
            if (definition == null) return PostEffectCommandResult.notFound(id);
            if (definition.isRuntime()) return PostEffectCommandResult.unsupportedRuntime(id);
            Identifier identifier = Identifier.parse(id);
            if (Minecraft.getInstance().getResourceManager().getResource(identifier.withPath(path -> "post_effect/" + path + ".json")).isEmpty()) {
                return PostEffectCommandResult.failed(id, "Resource post effect is missing: " + id);
            }
            try {
                Minecraft.getInstance().execute(() -> ((com.tkisor.nekojs.fabric.mixin.GameRendererAccessor) (Object) Minecraft.getInstance().gameRenderer).nekojs$setPostEffect(identifier));
                current = id;
                return PostEffectCommandResult.activated(id);
            } catch (RuntimeException e) {
                return PostEffectCommandResult.failed(id, e.getMessage() == null ? "Renderer activation failed" : e.getMessage());
            }
        }
        @Override public PostEffectCommandResult clear() {
            if (current == null) return PostEffectCommandResult.alreadyClear();
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gameRenderer.clearPostEffect());
            current = null;
            return PostEffectCommandResult.cleared();
        }
        @Override public PostEffectCommandResult toggle(String id) { return id.equals(current) ? clear() : activate(id); }
        @Override public String current() { return current; }
        @Override public boolean isActive() { return current != null; }
        @Override public void invalidate() { clear(); }
    }
}
//?}
