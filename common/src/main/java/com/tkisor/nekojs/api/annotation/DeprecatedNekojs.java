package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a script-facing type or member as deprecated for script authors.
 *
 * <p>Distinct from Java's own {@link Deprecated}: that annotation often marks
 * engine-internal concerns, while this one means "script authors should stop
 * using this member". It never fires from {@code @Deprecated} alone — mark the
 * member explicitly when the deprecation is script-facing.
 *
 * <p>Consumed by the TypeScript probe: renders a {@code @deprecated} JSDoc tag
 * so editors strike the member through in scripts and {@code .d.ts}.
 *
 * <pre>{@code
 * @DeprecatedNekojs(value = "Tag filters moved to Ingredient.matchTag.", replacedBy = "matchTag")
 * public static IngredientJS anyTag(String tag) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface DeprecatedNekojs {

    /** Why it is deprecated; may be empty when {@link #replacedBy()} says it all. */
    String value() default "";

    /** The replacement member, as script authors write it (e.g. {@code "ItemEvents.clicked"}). */
    String replacedBy() default "";
}
