package com.tkisor.nekojs.api.surface;

import java.util.Map;
import java.util.Objects;

/**
 * API 解析失败时抛出的异常，携带错误码与详情。
 *
 * <p>继承 {@link IllegalStateException}，表示运行时状态无法满足解析要求。
 */
public class ApiResolutionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String code;
    // 诊断载荷运行时只读，异常从不跨进程序列化；保留字段（而非 transient）避免序列化时静默丢数据
    @SuppressWarnings("serial")
    private final Map<String, String> details;

    /** @param code 错误码，不能为 {@code null} */
    public ApiResolutionException(String code, String message) {
        this(code, message, Map.of());
    }

    /**
     * @param code    错误码，不能为 {@code null}
     * @param message 说明
     * @param details 详情映射，不能为 {@code null}（拷贝为不可变映射）
     */
    public ApiResolutionException(String code, String message, Map<String, String> details) {
        super(message);
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(details, "details");
        this.code = code;
        this.details = Map.copyOf(details);
    }

    /** 返回错误码。 */
    public String code() {
        return code;
    }

    /** 返回详情（不可变映射）。 */
    public Map<String, String> details() {
        return details;
    }
}
