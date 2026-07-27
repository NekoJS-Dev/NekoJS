package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;

public interface JsTypeAdapter<T> {
    Class<T> targetType();

    boolean supports(JsValueView value, ConversionContext context);

    T convert(JsValueView value, ConversionContext context);

    ConversionPrecedence precedence();

    default List<AdapterInputShape> inputShapes() {
        return List.of();
    }
}
