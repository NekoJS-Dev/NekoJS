package com.tkisor.nekojs.api.surface;

import java.util.List;

@FunctionalInterface
public interface ApiCallHandler {
    Object invoke(ApiCallContext context, Object receiver, List<Object> arguments) throws Exception;
}
