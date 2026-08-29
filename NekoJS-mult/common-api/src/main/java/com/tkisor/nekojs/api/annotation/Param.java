package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * English documentation for a single parameter of a script-facing method or
 * constructor.
 *
 * <p>Consumed by the built-in probe together with {@link Doc}: rendered as a
 * {@code @param <name> <text>} line inside the generated JSDoc block, which
 * editors surface in signature help. Repeatable — declare one entry per
 * parameter, in declaration order for readability (order is preserved in
 * the generated output).
 *
 * <p>{@code name} must match the JS-visible parameter name (the reflected
 * Java parameter name; use the remapped name when {@link Remap} renames the
 * member). Parameters without a {@code @Param} entry simply get no
 * {@code @param} line — no validation is performed.
 */
@Documented
@Repeatable(Param.Container.class)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    /** Parameter name as seen from scripts. */
    String name();

    /** Documentation text for the parameter. */
    String value();

    /** Repeatable container. */
    @Documented
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        Param[] value();
    }
}
