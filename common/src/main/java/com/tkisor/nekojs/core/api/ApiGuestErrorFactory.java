package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.error.ApiInvocationException;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ApiGuestErrorFactory {
    private final Value throwError;

    private ApiGuestErrorFactory(Value throwError) {
        this.throwError = throwError;
    }

    public static ApiGuestErrorFactory create(Context context) {
        Objects.requireNonNull(context, "context");
        Value throwError = context.eval("js", """
                (data) => {
                    const error = new Error(data.message);
                    for (const key of Object.keys(data)) {
                        Object.defineProperty(error, key, {
                            value: data[key], enumerable: true, writable: false, configurable: false
                        });
                    }
                    throw error;
                }
                """);
        return new ApiGuestErrorFactory(throwError);
    }

    Object raise(ApiInvocationException error) {
        Map<String, Object> properties = new LinkedHashMap<>(error.details());
        properties.put("code", error.code());
        properties.put("message", error.getMessage());
        return throwError.execute(ProxyObject.fromMap(properties));
    }
}
