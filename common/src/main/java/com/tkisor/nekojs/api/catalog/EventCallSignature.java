package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.AdapterInputShape;

import java.util.List;
import java.util.Objects;

/** Describes a callable Script Event with its callback fixed as the last argument. */
public record EventCallSignature(
        List<Parameter> inputs,
        List<Parameter> callbackParameters,
        AdapterInputShape returns
) {
    public EventCallSignature {
        inputs = List.copyOf(inputs);
        callbackParameters = List.copyOf(callbackParameters);
        Objects.requireNonNull(returns, "returns");
    }

    public record Parameter(String name, AdapterInputShape shape) {
        public Parameter {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(shape, "shape");
        }
    }
}
