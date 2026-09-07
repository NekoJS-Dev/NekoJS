package com.tkisor.nekojs.api.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
import org.jetbrains.annotations.Nullable;

/**
 * Platform seam for post-effect resources and runtime pipelines.
 *
 * <p>An implementation owns native renderer objects and cached GPU resources. Reconcile receives
 * the complete catalog for one CLIENT script revision; it must remove definitions absent from the
 * new catalog and invalidate cached runtime resources before clearing state. A platform without a
 * runtime pipeline must return {@link PostEffectCommandResult.Status#UNSUPPORTED} with
 * {@link PostEffectCommandResult#RUNTIME_PIPELINE_UNSUPPORTED} for runtime activation.
 */
@Doc("Platform adapter hiding resource and runtime post-effect renderer details.")
public interface PostEffectRendererAdapter {
    PostEffectCapabilities capabilities();

    /** Replaces the adapter's catalog with a complete immutable declaration product. */
    PostEffectCommandResult reconcile(PostEffectCatalog catalog);

    /** Activates an id from the currently reconciled catalog. */
    PostEffectCommandResult activate(String id);

    /** Clears the current native renderer state. */
    PostEffectCommandResult clear();

    /** Toggles an id from the currently reconciled catalog. */
    PostEffectCommandResult toggle(String id);

    /** Currently active canonical id, or null. */
    @Nullable
    String current();

    boolean isActive();

    /** Invalidates cached runtime renderer resources, including native GPU objects. */
    void invalidate();

    /** Explicit alias for lifecycle implementations that name the operation by its effect. */
    default void invalidateCachedResources() {
        invalidate();
    }
}
