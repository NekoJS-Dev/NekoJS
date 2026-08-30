package com.tkisor.nekojs.api.contract;

import java.util.Objects;

/**
 * 一条 API 契约违规，携带稳定错误码、违规路径与说明。
 *
 * @param code    错误码，不能为 {@code null}
 * @param path    违规发生的路径/位置，不能为 {@code null}
 * @param message 说明，不能为 {@code null}
 */
public record ApiContractViolation(String code, String path, String message) {
    public ApiContractViolation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    /** 返回 {@code "code at path: message"} 形式的描述。 */
    @Override
    public String toString() {
        return code + " at " + path + ": " + message;
    }
}
