package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Publishes an implemented public interface for returned values and event payloads in authoring metadata.
 * This identity view never converts values, changes host access, or broadens accepted Java inputs.
 * Only direct object returns are projected; generic arguments and mutable array elements stay native.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ScriptTypeView {
    Class<?> value();
}
