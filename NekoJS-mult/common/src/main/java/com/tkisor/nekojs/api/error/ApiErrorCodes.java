package com.tkisor.nekojs.api.error;

/**
 * API 错误码常量集合，供 {@link ApiInvocationException} 等使用。
 *
 * <p>错误码是跨平台稳定的字符串，脚本侧据此判断错误类型。本类不可实例化。
 */
public final class ApiErrorCodes {
    /** 能力不支持。 */
    public static final String UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";
    /** 模块不支持。 */
    public static final String UNSUPPORTED_MODULE = "UNSUPPORTED_MODULE";
    /** 无效引用。 */
    public static final String INVALID_REFERENCE = "INVALID_REFERENCE";
    /** API 契约违规。 */
    public static final String API_CONTRACT_VIOLATION = "API_CONTRACT_VIOLATION";
    /** API 符号重复。 */
    public static final String DUPLICATE_API_SYMBOL = "DUPLICATE_API_SYMBOL";
    /** 能力提供者重复。 */
    public static final String DUPLICATE_CAPABILITY_PROVIDER = "DUPLICATE_CAPABILITY_PROVIDER";
    /** 原生类型泄漏。 */
    public static final String NATIVE_TYPE_LEAK = "NATIVE_TYPE_LEAK";
    /** API 清单过期。 */
    public static final String STALE_API_MANIFEST = "STALE_API_MANIFEST";
    /** 无匹配签名。 */
    public static final String NO_MATCHING_SIGNATURE = "NO_MATCHING_SIGNATURE";
    /** 调用歧义。 */
    public static final String AMBIGUOUS_CALL = "AMBIGUOUS_CALL";
    /** 类型不匹配。 */
    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    /** 回调不可执行。 */
    public static final String CALLBACK_NOT_EXECUTABLE = "CALLBACK_NOT_EXECUTABLE";
    /** 调用错误。 */
    public static final String INVOCATION_ERROR = "INVOCATION_ERROR";
    /** 非法 JSON。 */
    public static final String INVALID_JSON = "INVALID_JSON";
    /** JSON 超限。 */
    public static final String JSON_LIMIT_EXCEEDED = "JSON_LIMIT_EXCEEDED";
    /** JSON 路径被禁止。 */
    public static final String JSON_PATH_FORBIDDEN = "JSON_PATH_FORBIDDEN";
    /** JSON 文件过大。 */
    public static final String JSON_FILE_TOO_LARGE = "JSON_FILE_TOO_LARGE";
    /** JSON IO 错误。 */
    public static final String JSON_IO_ERROR = "JSON_IO_ERROR";
    /** JSON 原子写失败。 */
    public static final String JSON_ATOMIC_WRITE_FAILED = "JSON_ATOMIC_WRITE_FAILED";
    /** 非法 NBT。 */
    public static final String INVALID_NBT = "INVALID_NBT";
    /** NBT 超限。 */
    public static final String NBT_LIMIT_EXCEEDED = "NBT_LIMIT_EXCEEDED";
    /** NBT 路径被禁止。 */
    public static final String NBT_PATH_FORBIDDEN = "NBT_PATH_FORBIDDEN";
    /** NBT 文件过大。 */
    public static final String NBT_FILE_TOO_LARGE = "NBT_FILE_TOO_LARGE";
    /** NBT IO 错误。 */
    public static final String NBT_IO_ERROR = "NBT_IO_ERROR";
    /** NBT 原子写失败。 */
    public static final String NBT_ATOMIC_WRITE_FAILED = "NBT_ATOMIC_WRITE_FAILED";

    private ApiErrorCodes() {
    }
}
