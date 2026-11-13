package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;

/**
 * Adapter interface that converts a script-side JS value to a Java target type.
 *
 * <p>It corresponds to {@link com.tkisor.nekojs.api.JSTypeAdapter}, but hides the JS value
 * behind {@link JsValueView} instead of depending on GraalVM {@code Value} directly, so
 * conversion logic can be tested without the engine.
 *
 * <p>An implementation must make {@link #convert(JsValueView, ConversionContext)} succeed
 * whenever {@link #supports(JsValueView, ConversionContext)} returns {@code true}. On failure
 * it must throw {@link ValueConversionException}, never return {@code null}.
 *
 * @param <T> target Java type
 */
public interface JsTypeAdapter<T> {
    /** Returns the target Java type. */
    Class<T> targetType();

    /** Returns whether the given JS value can be converted to the target type. */
    boolean supports(JsValueView value, ConversionContext context);

    /** Converts the given JS value to the target type; throws {@link ValueConversionException} on failure. */
    T convert(JsValueView value, ConversionContext context);

    /** Returns the conversion precedence; when several adapters support a value, the higher precedence wins. */
    ConversionPrecedence precedence();

    /**
     * Declares the input shapes this adapter accepts; probe uses them to build the TypeScript
     * input alias. Empty by default.
     */
    default List<AdapterInputShape> inputShapes() {
        return List.of();
    }
}
