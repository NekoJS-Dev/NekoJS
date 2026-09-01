package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an additional script-facing signature for a method or constructor
 * that type reflection cannot derive on its own.
 *
 * <p>Two channels feed overload information into the generated {@code .d.ts}:
 * <ol>
 *   <li><b>Automatic</b> — type adapters declare {@code inputShapes()}, and the
 *       probe widens every parameter referencing the adapter's target type into
 *       an input alias union ({@code $Foo_}). This covers "same shape, extra
 *       accepted input forms" and requires no annotation.</li>
 *   <li><b>This annotation</b> — for signatures the adapter channel cannot
 *       express: different parameter counts (e.g. {@code of(id)} vs
 *       {@code of(id, count)}), parameters declared as {@code Object}, or
 *       overloads whose meaning differs per form.</li>
 * </ol>
 *
 * <p>Each {@code value()} entry is one parameter, written verbatim as
 * {@code name: Type} (optional parameters as {@code name?: Type}). Entries are
 * emitted as a TypeScript overload declaration alongside the reflected
 * signature; type names must be resolvable in the generated module (prefer the
 * {@code $Name} form used throughout the declarations). {@code returns()}
 * defaults to the method's reflected return type when empty.
 *
 * <pre>{@code
 * @Doc("Creates a stack from an id.")
 * @Overload({"id: string"})
 * @Overload(value = {"item: $ItemStack", "count?: number"}, doc = "Copy with a count.")
 * public static ItemStack of(Object input, int count) { ... }
 * }</pre>
 *
 * <p>Consumed by the TypeScript probe backend only; getter/setter property
 * accessors ignore overloads (annotate a regular method form instead).
 *
 * @see com.tkisor.nekojs.api.AdapterInputShape for the automatic channel
 */
@Documented
@Repeatable(Overload.Container.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Overload {

    /** Parameters of this overload, each {@code name: Type} or {@code name?: Type}. */
    String[] value() default {};

    /** Return type of this overload; empty inherits the method's reflected return type. */
    String returns() default "";

    /** Optional one-paragraph doc rendered as JSDoc above this overload. */
    String doc() default "";

    /** Repeatable container. */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @interface Container {
        Overload[] value();
    }
}
