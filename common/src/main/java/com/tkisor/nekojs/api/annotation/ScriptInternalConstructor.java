package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Omits a framework-owned constructor from generated script declarations and their type closure.
 *
 * <p>Use this for lifecycle objects supplied by the engine whose construction requires internal
 * state, rather than for constructors intended for script authors. It does not change Java
 * visibility, Graal host access, or runtime constructor invocation and is not an access-control
 * annotation. Public methods, properties, and other constructors remain part of the declaration.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface ScriptInternalConstructor {}
