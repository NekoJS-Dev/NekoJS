package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bulk-remaps a class's members by prefix substitution.
 *
 * <p>When applied to a type, all members matching the prefix are remapped
 * to the replacement prefix. Processed by {@code MemberVisibilityQuery}.
 *
 * <p>Example: {@code @RemapByPrefix({"get", "is"})} on a record strips the
 * {@code get} / {@code is} prefixes from member names. The remainder keeps its
 * original casing: {@code getName()} is exposed as {@code Name}, not {@code name}.
 * Use a per-member {@link Remap @Remap} when a lowercased property-style name
 * is required.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RemapByPrefix {

    /** Prefixes to strip from member names when exposing to JS. */
    String[] value();
}
