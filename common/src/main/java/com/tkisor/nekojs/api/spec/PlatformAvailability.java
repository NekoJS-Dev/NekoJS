package com.tkisor.nekojs.api.spec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which platforms a spec interface or method is available on.
 *
 * <p>On a spec interface it gives the platform range the spec covers. SpecCoverageProcessor reads
 * the annotation to decide which platform implementations to check.
 *
 * <p>On a single method it gives that method's availability, documented for use outside the spec.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PlatformAvailability {
    /** Platform range this spec or method targets. */
    Scope value() default Scope.ALL;

    /**
     * Platform range.
     *
     * <p>{@link #ALL} means every target platform (NF26 / NF121 / CR / Future Fabric) must implement
     * the spec. Only methods that no platform's native classes provide and that need a mixin belong
     * in an {@code ALL} spec.
     */
    enum Scope {
        /** Every target platform must implement it (NF26 / NF121 / CR / Future Fabric) */
        ALL,
        /** NeoForge releases only (26.x + 1.21.1) */
        NF_ONLY,
        /** NeoForge 26.x only (the 26-shared source set, 26.1/26.2) */
        NF26_ONLY,
        /** Cleanroom 1.12.2 only */
        CR_ONLY
    }
}
