package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for Item.
 *
 * <p>Each platform's {@code ItemExtension} must {@code extends ItemSpec}.
 *
 * <p>{@code getId()} is safe to put in a spec: on NF the neko version is a no-arg instance method
 * while the native one is a static method taking one argument (different arity), and CR has no
 * instance {@code getId()} at all.
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface ItemSpec {

    /** Item registry id (for example "minecraft:stone"). */
    default String neko$getId() {
        throw new UnsupportedOperationException("ItemSpec.neko$getId not implemented");
    }
}
