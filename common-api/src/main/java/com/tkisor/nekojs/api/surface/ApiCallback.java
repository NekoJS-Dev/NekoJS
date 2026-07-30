package com.tkisor.nekojs.api.surface;

import java.util.List;

@FunctionalInterface
public interface ApiCallback {
    Object call(List<Object> arguments) throws Exception;
}
