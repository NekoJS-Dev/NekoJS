package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Prevents GraalVM from exposing the annotated element to JavaScript.
 *
 * <p>During {@code HostAccess} policy enforcement, GraalVM checks for this annotation
 * via {@code MemberVisibilityQuery} and excludes matching members from JS visibility.
 *
 * <p>Usage: mark Java-internal methods/fields that should never be callable from scripts,
 * e.g. {@code close()} on a timer or internal lifecycle hooks.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HideFromJS {
}
