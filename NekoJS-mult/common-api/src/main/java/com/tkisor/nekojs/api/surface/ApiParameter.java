package com.tkisor.nekojs.api.surface;

import java.util.Objects;

/**
 * API 签名中的一个参数：名称、类型、是否可选、是否可变参数。
 *
 * @param name     参数名，不能为 {@code null}
 * @param type     参数类型，不能为 {@code null}
 * @param optional 是否可选
 * @param varargs  是否为可变参数（须位于末位）
 */
public record ApiParameter(String name, ApiTypeRef type, boolean optional, boolean varargs) {
    public ApiParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}
