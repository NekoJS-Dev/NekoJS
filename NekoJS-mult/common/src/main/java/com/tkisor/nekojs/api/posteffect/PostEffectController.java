package com.tkisor.nekojs.api.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Narrow state-only post-effect API. Definitions enter through ClientEvents.postEffects; this
 * interface only issues activate/clear/toggle commands and exposes current state/capabilities.
 */
@Doc("State commands for post effects; definitions are declared separately by ClientEvents.postEffects.")
public interface PostEffectController {
    /** Activates an id from the reconciled catalog. */
    PostEffectCommandResult activate(String id);

    /** Clears the active effect. */
    PostEffectCommandResult clear();

    /** Activates an id unless it is already active, in which case it clears. */
    PostEffectCommandResult toggle(String id);

    /** Returns the currently active id, or null. */
    @Nullable
    String current();

    /** Returns whether a renderer effect is currently active. */
    boolean isActive();

    /** Returns explicit platform capabilities. */
    PostEffectCapabilities capabilities();

    default boolean supportsResourceBacked() {
        return capabilities().supportsResourceBacked();
    }

    default boolean supportsRuntimePipeline() {
        return capabilities().supportsRuntimePipeline();
    }

    /** Creates a state controller backed by a platform adapter. */
    static PostEffectController backedBy(PostEffectRendererAdapter adapter) {
        return new DefaultPostEffectController(Objects.requireNonNull(adapter, "adapter"));
    }
}
