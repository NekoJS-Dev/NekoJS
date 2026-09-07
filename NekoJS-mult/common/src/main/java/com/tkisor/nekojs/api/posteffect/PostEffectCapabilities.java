package com.tkisor.nekojs.api.posteffect;

import com.tkisor.nekojs.api.annotation.Doc;

/** Explicit renderer capabilities exposed by a client post-effect adapter. */
@Doc("Capabilities reported by a post-effect renderer adapter.")
public record PostEffectCapabilities(boolean resourceBacked, boolean runtimePipeline) {
    public static PostEffectCapabilities unsupported() {
        return new PostEffectCapabilities(false, false);
    }

    public static PostEffectCapabilities resourceOnly() {
        return new PostEffectCapabilities(true, false);
    }

    public static PostEffectCapabilities all() {
        return new PostEffectCapabilities(true, true);
    }

    public boolean supportsResourceBacked() {
        return resourceBacked;
    }

    public boolean supportsRuntimePipeline() {
        return runtimePipeline;
    }

    public boolean supports(PostEffectDefinition definition) {
        return definition != null && (definition.isResourceBacked() ? resourceBacked : runtimePipeline);
    }
}
