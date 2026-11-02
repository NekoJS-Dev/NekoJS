package com.tkisor.nekojs.api.annotation;

import com.tkisor.nekojs.api.ScriptInputShapeProvider;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Replaces a reflected parameter's broad input declaration with the provider's shape. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ScriptInput {
    Class<? extends ScriptInputShapeProvider> value();
}
