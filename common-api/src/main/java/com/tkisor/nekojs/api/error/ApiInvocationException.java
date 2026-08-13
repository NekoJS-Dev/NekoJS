package com.tkisor.nekojs.api.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * API 调用失败时抛出的异常，携带稳定错误码与结构化详情。
 *
 * <p>继承 {@link RuntimeException}；错误码不能为空，详情键值均校验后拷贝为不可变映射。
 * 脚本侧可经 {@code err.code} 读取错误码（取值见 {@link ApiErrorCodes}）。
 */
public class ApiInvocationException extends RuntimeException {
    private final String code;
    private final Map<String, String> details;

    /**
     * @param code    错误码，不能为空
     * @param message 说明，不能为 {@code null}
     */
    public ApiInvocationException(String code, String message) {
        this(code, message, Map.of(), null);
    }

    /**
     * @param code    错误码，不能为空
     * @param message 说明，不能为 {@code null}
     * @param details 详情，不能为 {@code null}
     */
    public ApiInvocationException(String code, String message, Map<String, String> details) {
        this(code, message, details, null);
    }

    /**
     * @param code    错误码，不能为空
     * @param message 说明，不能为 {@code null}
     * @param details 详情，不能为 {@code null}
     * @param cause   底层原因，可为 {@code null}
     */
    public ApiInvocationException(
            String code,
            String message,
            Map<String, String> details,
            Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        this.code = code;
        this.details = immutableDetails(details);
    }

    /** 返回错误码。 */
    public String code() {
        return code;
    }

    /** 返回详情（不可变映射）。 */
    public Map<String, String> details() {
        return details;
    }

    private static Map<String, String> immutableDetails(Map<String, String> details) {
        Objects.requireNonNull(details, "details");
        Map<String, String> copy = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("detail key must not be blank");
            }
            copy.put(key, Objects.requireNonNull(value, "detail value for " + key));
        });
        return Map.copyOf(copy);
    }
}
