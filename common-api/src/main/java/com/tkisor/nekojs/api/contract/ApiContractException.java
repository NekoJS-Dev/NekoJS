package com.tkisor.nekojs.api.contract;

import java.util.Objects;

/**
 * API 契约校验失败时抛出的异常，携带结构化的 {@link ApiContractViolation}。
 *
 * <p>继承 {@link IllegalArgumentException}，适用于「传入参数不符合契约」这类用法错误。
 */
public class ApiContractException extends IllegalArgumentException {

    private final ApiContractViolation violation;

    /** @param violation 违规详情，不能为 {@code null}。 */
    public ApiContractException(ApiContractViolation violation) {
        super(violation.toString());
        this.violation = Objects.requireNonNull(violation, "violation");
    }

    /**
     * @param violation 违规详情，不能为 {@code null}
     * @param cause     底层原因，可为 {@code null}
     */
    public ApiContractException(ApiContractViolation violation, Throwable cause) {
        super(violation.toString(), cause);
        this.violation = Objects.requireNonNull(violation, "violation");
    }

    /** 返回违规详情。 */
    public ApiContractViolation violation() {
        return violation;
    }
}
