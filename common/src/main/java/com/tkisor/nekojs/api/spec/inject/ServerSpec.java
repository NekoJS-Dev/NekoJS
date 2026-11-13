package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension specification for MinecraftServer. NF_ONLY: CR 1.12.2 has no such
 * extension yet.
 *
 * <p>Every NF platform's {@code ServerExtension} must {@code extends ServerSpec}.
 *
 * <p>{@link #neko$data()} returns {@code Object} (NF returns
 * {@code AttachedData<MinecraftServer>}); each platform overrides it with a covariant return type,
 * and the spec names no concrete type to stay within the common-api constraints.
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.NF_ONLY)
public interface ServerSpec {

    /** In-memory data container attached to this server. */
    default Object neko$data() {
        throw new UnsupportedOperationException("ServerSpec.neko$data not implemented");
    }
}
