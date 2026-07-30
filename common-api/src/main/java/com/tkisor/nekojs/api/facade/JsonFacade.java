package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.JsonValue;

public interface JsonFacade {
    JsonValue parse(String source);

    String toString(JsonValue value);

    String toPrettyString(JsonValue value);

    JsonValue read(String path);

    void write(String path, JsonValue value);
}
