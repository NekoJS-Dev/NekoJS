package com.tkisor.nekojs.api.error;

/**
 * API error code constants, used by {@link ApiInvocationException} etc.
 *
 * <p>Error codes are platform-stable strings, used by scripts to determine error types. This class cannot be instantiated.
 */
public final class ApiErrorCodes {
    /** Capability not supported. */
    public static final String UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";
    /** Module not supported. */
    public static final String UNSUPPORTED_MODULE = "UNSUPPORTED_MODULE";
    /** Invalid reference. */
    public static final String INVALID_REFERENCE = "INVALID_REFERENCE";
    /** API contract violation. */
    public static final String API_CONTRACT_VIOLATION = "API_CONTRACT_VIOLATION";
    /** Duplicate API symbol. */
    public static final String DUPLICATE_API_SYMBOL = "DUPLICATE_API_SYMBOL";
    /** Duplicate capability provider. */
    public static final String DUPLICATE_CAPABILITY_PROVIDER = "DUPLICATE_CAPABILITY_PROVIDER";
    /** Native type leak. */
    public static final String NATIVE_TYPE_LEAK = "NATIVE_TYPE_LEAK";
    /** API manifest expired. */
    public static final String STALE_API_MANIFEST = "STALE_API_MANIFEST";
    /** No matching signature. */
    public static final String NO_MATCHING_SIGNATURE = "NO_MATCHING_SIGNATURE";
    /** Call ambiguity. */
    public static final String AMBIGUOUS_CALL = "AMBIGUOUS_CALL";
    /** Type mismatch. */
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    /** Callback not executable. */
    public static final String CALLBACK_NOT_EXECUTABLE = "CALLBACK_NOT_EXECUTABLE";
    /** Invocation error. */
    public static final String INVOCATION_ERROR = "INVOCATION_ERROR";
    /** Invalid JSON. */
    public static final String INVALID_JSON = "INVALID_JSON";
    /** JSON limit exceeded. */
    public static final String JSON_LIMIT_EXCEEDED = "JSON_LIMIT_EXCEEDED";
    /** JSON path forbidden. */
    public static final String JSON_PATH_FORBIDDEN = "JSON_PATH_FORBIDDEN";
    /** JSON file too large. */
    public static final String JSON_FILE_TOO_LARGE = "JSON_FILE_TOO_LARGE";
    /** JSON IO error. */
    public static final String JSON_IO_ERROR = "JSON_IO_ERROR";
    /** JSON atomic write failed. */
    public static final String JSON_ATOMIC_WRITE_FAILED = "JSON_ATOMIC_WRITE_FAILED";
    /** Invalid NBT. */
    public static final String INVALID_NBT = "INVALID_NBT";
    /** NBT limit exceeded. */
    public static final String NBT_LIMIT_EXCEEDED = "NBT_LIMIT_EXCEEDED";
    /** NBT path forbidden. */
    public static final String NBT_PATH_FORBIDDEN = "NBT_PATH_FORBIDDEN";
    /** NBT file too large. */
    public static final String NBT_FILE_TOO_LARGE = "NBT_FILE_TOO_LARGE";
    /** NBT IO error. */
    public static final String NBT_IO_ERROR = "NBT_IO_ERROR";
    /** NBT atomic write failed. */
    public static final String NBT_ATOMIC_WRITE_FAILED = "NBT_ATOMIC_WRITE_FAILED";

    private ApiErrorCodes() {
    }
}
