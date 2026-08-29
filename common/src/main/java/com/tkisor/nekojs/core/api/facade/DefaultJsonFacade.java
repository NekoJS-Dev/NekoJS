package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.JsonFacade;
import com.tkisor.nekojs.core.api.json.JsonValueException;
import com.tkisor.nekojs.core.api.json.JsonFileStore;
import com.tkisor.nekojs.core.api.json.JsonValueParser;
import com.tkisor.nekojs.core.api.json.JsonValueSerializer;

import java.util.Map;
import java.nio.file.Path;

public final class DefaultJsonFacade implements JsonFacade {
    private final JsonFileStore fileStore;

    public DefaultJsonFacade(Path dataRoot) {
        this.fileStore = new JsonFileStore(dataRoot);
    }

    @Override
    public JsonValue parse(String source) {
        try {
            return JsonValueParser.parse(source);
        } catch (JsonValueException e) {
            throw asApiError(e);
        }
    }

    @Override
    public String toString(JsonValue value) {
        try {
            return JsonValueSerializer.compact(value);
        } catch (JsonValueException e) {
            throw asApiError(e);
        }
    }

    @Override
    public String toPrettyString(JsonValue value) {
        try {
            return JsonValueSerializer.pretty(value);
        } catch (JsonValueException e) {
            throw asApiError(e);
        }
    }

    @Override
    public JsonValue read(String path) {
        return fileStore.read(path);
    }

    @Override
    public void write(String path, JsonValue value) {
        fileStore.write(path, value);
    }

    private static ApiInvocationException asApiError(JsonValueException error) {
        String code = error.reason() == JsonValueException.Reason.LIMIT_EXCEEDED
                ? ApiErrorCodes.JSON_LIMIT_EXCEEDED
                : ApiErrorCodes.INVALID_JSON;
        return new ApiInvocationException(code, error.getMessage(), Map.of(), error);
    }
}
