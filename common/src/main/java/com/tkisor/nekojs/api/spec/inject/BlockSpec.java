package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Cross-platform extension interface for Block.
 *
 * <p>Each platform's {@code BlockExtension} must {@code extends BlockSpec}.
 *
 * <p>{@code getId()} is safe to put in a spec: on NF the neko version is a no-arg instance method
 * while the native one is a static method taking one argument (different arity), and on CR there
 * is no instance {@code getId()} (that goes through {@code ForgeRegistries}).
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface BlockSpec {

    /** Block registry id (for example "minecraft:stone"). */
    default String neko$getId() {
        throw new UnsupportedOperationException("BlockSpec.neko$getId not implemented");
    }
}
