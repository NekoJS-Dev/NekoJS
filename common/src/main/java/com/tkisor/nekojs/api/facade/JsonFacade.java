package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.data.JsonValue;

/**
 * JSON facade, exposed to scripts as the global {@code JsonIO}.
 *
 * <p>It parses and serializes JSON strings and reads and writes JSON files under the data directory.
 * Every value is a platform-independent {@link JsonValue}. A parse failure or an exceeded limit throws
 * {@link com.tkisor.nekojs.api.error.ApiInvocationException} (see
 * {@link com.tkisor.nekojs.api.error.ApiErrorCodes} for the error codes).
 */
public interface JsonFacade {
    /** Parses a JSON string into a {@link JsonValue}; throws when the JSON is invalid or a limit is exceeded. */
    JsonValue parse(String source);

    /** Serializes a {@link JsonValue} into a compact JSON string. */
    String toString(JsonValue value);

    /** Serializes a {@link JsonValue} into an indented, human-readable JSON string. */
    String toPrettyString(JsonValue value);

    /**
     * Reads the JSON file at the given relative path under the data directory and parses it into a
     * {@link JsonValue}.
     *
     * @return the parsed value, or {@code null} when the file does not exist
     */
    JsonValue read(String path);

    /**
     * Serializes a {@link JsonValue} and writes it to the JSON file at the given relative path under the
     * data directory.
     *
     * <p>Writes are atomic (temp file + atomic move), so readers never observe a partial file.
     */
    void write(String path, JsonValue value);
}
