package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a common-api data/facade type as a NekoJS <b>contract receiver</b> — a type whose
 * instances can be the receiver of an extension method ({@code dataType.method(...)}) and
 * that the contract reflector ({@code ContractReflector}, in {@code common}) treats as a
 * first-class data type when reflecting Java facades into API symbols.
 *
 * <p>The set of receiver types used to be a hardcoded {@code ||} chain inside the reflector;
 * this annotation makes it data-driven, so adding a new receiver type no longer requires
 * editing the reflector (and removes a {@code common} → {@code common-api} class-name
 * knowledge leak).
 *
 * <p>{@link #value()} overrides the contract name exposed to JS (defaults to the class
 * simple name). Use it when the Java class name carries a suffix that should not appear in
 * the JS contract, e.g. {@code ModInfoValue} → {@code "ModInfo"}, {@code PerfTimerValue}
 * → {@code "PerfTimer"}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContractReceiver {

    /** Contract name exposed to JS; empty (default) means "use the class simple name". */
    String value() default "";
}
