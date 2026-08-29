package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * English documentation for the return value of a script-facing method.
 *
 * <p>Consumed by the built-in probe together with {@link Doc}: rendered as a
 * {@code @returns <text>} line inside the generated JSDoc block. At most one
 * entry per method; constructors have no return value and are not a valid
 * target.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Return {

    /** Documentation text for the return value. */
    String value();
}
