package com.tkisor.nekojs.api.contract;

import java.util.Objects;

public class ApiContractException extends IllegalArgumentException {

    private final ApiContractViolation violation;

    public ApiContractException(ApiContractViolation violation) {
        super(violation.toString());
        this.violation = Objects.requireNonNull(violation, "violation");
    }

    public ApiContractException(ApiContractViolation violation, Throwable cause) {
        super(violation.toString(), cause);
        this.violation = Objects.requireNonNull(violation, "violation");
    }

    public ApiContractViolation violation() {
        return violation;
    }
}
