package com.tkisor.nekojs.api.error;

public final class ApiErrorCodes {
    public static final String UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";
    public static final String UNSUPPORTED_MODULE = "UNSUPPORTED_MODULE";
    public static final String INVALID_REFERENCE = "INVALID_REFERENCE";
    public static final String API_CONTRACT_VIOLATION = "API_CONTRACT_VIOLATION";
    public static final String DUPLICATE_API_SYMBOL = "DUPLICATE_API_SYMBOL";
    public static final String DUPLICATE_CAPABILITY_PROVIDER = "DUPLICATE_CAPABILITY_PROVIDER";
    public static final String NATIVE_TYPE_LEAK = "NATIVE_TYPE_LEAK";
    public static final String STALE_API_MANIFEST = "STALE_API_MANIFEST";
    public static final String NO_MATCHING_SIGNATURE = "NO_MATCHING_SIGNATURE";
    public static final String AMBIGUOUS_CALL = "AMBIGUOUS_CALL";
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String CALLBACK_NOT_EXECUTABLE = "CALLBACK_NOT_EXECUTABLE";
    public static final String INVOCATION_ERROR = "INVOCATION_ERROR";
    public static final String INVALID_JSON = "INVALID_JSON";
    public static final String JSON_LIMIT_EXCEEDED = "JSON_LIMIT_EXCEEDED";
    public static final String JSON_PATH_FORBIDDEN = "JSON_PATH_FORBIDDEN";
    public static final String JSON_FILE_TOO_LARGE = "JSON_FILE_TOO_LARGE";
    public static final String JSON_IO_ERROR = "JSON_IO_ERROR";
    public static final String JSON_ATOMIC_WRITE_FAILED = "JSON_ATOMIC_WRITE_FAILED";
    public static final String INVALID_NBT = "INVALID_NBT";
    public static final String NBT_LIMIT_EXCEEDED = "NBT_LIMIT_EXCEEDED";
    public static final String NBT_PATH_FORBIDDEN = "NBT_PATH_FORBIDDEN";
    public static final String NBT_FILE_TOO_LARGE = "NBT_FILE_TOO_LARGE";
    public static final String NBT_IO_ERROR = "NBT_IO_ERROR";
    public static final String NBT_ATOMIC_WRITE_FAILED = "NBT_ATOMIC_WRITE_FAILED";

    private ApiErrorCodes() {
    }
}
