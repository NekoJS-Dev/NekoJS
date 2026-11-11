package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the TypeScript return type when Java reflection cannot describe the script-visible value.
 *
 * <p>The expression replaces the reflected output in TypeScript declarations, including Bean getters
 * and inherited methods. Use inline {@code import('module').Type} references for external types;
 * unqualified names must resolve in the generated module. Referenced modules must already be supplied
 * by the catalog or reflection closure; this annotation does not collect dependencies. Include the Java type in an intersection
 * when its members remain available. This metadata does not convert values or affect other backends.
 * Explicit Probe type edits take precedence.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ScriptOutput {
    /** A nonblank TypeScript type expression for the actual returned value. */
    String value();
}
