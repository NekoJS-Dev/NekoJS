package com.tkisor.nekojs.api.data;

/**
 * 类型转换适配器的优先级，映射到 GraalVM {@code HostAccess.TargetMappingPrecedence}。
 *
 * <p>当多个 {@link JsTypeAdapter} 同时声称支持某个值时，优先级高者胜出；
 * 顺序为 {@code LOWEST < LOW < HIGH < HIGHEST}。
 */
public enum ConversionPrecedence {
    LOWEST,
    LOW,
    HIGH,
    HIGHEST
}
