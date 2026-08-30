package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * English description for a script-facing type, method, constructor, or field.
 *
 * <p>Consumed by the built-in probe during type reflection: the value is rendered
 * as a JSDoc block in the generated {@code .d.ts} declarations, so script authors
 * and AI assistants see the documentation directly in their editor. Repeatable —
 * multiple {@code @Doc} entries render as separate JSDoc paragraphs.
 *
 * <p>Documentation is English-only by project convention (annotation javadoc and
 * probe output are both English). This is the declarative counterpart of the
 * programmatic {@code registerTypeDocs} channel; on conflict, probe
 * {@code modify_type} edits applied at generation time win over annotations.
 *
 * <pre>{@code
 * @Doc("Creates an item stack from an id or item-like value.")
 * @Doc("Accepts plain ids like 'minecraft:stone' or '#minecraft:planks' tags.")
 * @Param(name = "id", value = "Item id, tag, or item-like object.")
 * @Return("The resolved ItemStack; never null.")
 * public ItemStack of(Object id) { ... }
 * }</pre>
 */
@Documented
@Repeatable(Doc.Container.class)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Doc {

    /** Documentation text. May contain {@code \n} for multi-line paragraphs. */
    String value();

    /** Repeatable container. */
    @Documented
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        Doc[] value();
    }
}
