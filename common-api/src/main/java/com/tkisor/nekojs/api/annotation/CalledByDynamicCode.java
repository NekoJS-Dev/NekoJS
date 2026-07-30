package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method whose callers are NOT statically discoverable —
 * called from dynamically generated/evaluated code (e.g. {@code context.eval()} strings,
 * GraalVM interop bridges, ProxyObject dispatch, or reflection).
 *
 * <p>These methods will appear unused to IDE "find usages" tools.
 * This annotation serves as documentation for maintainers.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface CalledByDynamicCode {
}
