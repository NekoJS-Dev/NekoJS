package com.tkisor.nekojs.api.data;

/**
 * Precedence of a type conversion adapter, mapped to GraalVM
 * {@code HostAccess.TargetMappingPrecedence}.
 *
 * <p>When several {@link JsTypeAdapter} instances claim the same value, the one with the
 * higher precedence wins. The order is {@code LOWEST < LOW < HIGH < HIGHEST}.
 */
public enum ConversionPrecedence {
    LOWEST,
    LOW,
    HIGH,
    HIGHEST
}
