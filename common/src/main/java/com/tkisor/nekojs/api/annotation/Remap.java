package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java member to a different JS-visible name in GraalVM interop.
 * Processed by {@code MemberVisibilityQuery} during host access resolution.
 *
 * <p>Example: {@code @Remap("jsName") void javaName()} exposes the method
 * as {@code obj.jsName()} in scripts instead of {@code obj.javaName()}.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Remap {

    /** JS-visible name for the annotated element. */
    String value();
}
