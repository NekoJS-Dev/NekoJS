package com.tkisor.nekojs.api.contract;

import java.util.Objects;

public record ApiContractViolation(String code, String path, String message) {
    public ApiContractViolation {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public String toString() {
        return code + " at " + path + ": " + message;
    }
}
