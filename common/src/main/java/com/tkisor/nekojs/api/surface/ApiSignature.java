package com.tkisor.nekojs.api.surface;

import java.util.List;
import java.util.Objects;

/**
 * API 符号的一个可调用签名：参数列表、返回类型、是否为构造器。
 *
 * <p>构造时校验：可变参数必须位于末位、必填参数不能出现在可选参数之后。
 */
public record ApiSignature(
        List<ApiParameter> parameters,
        ApiTypeRef returnType,
        boolean isConstructor) {

    public ApiSignature {
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        Objects.requireNonNull(returnType, "returnType");
        boolean optionalSeen = false;
        for (int i = 0; i < parameters.size(); i++) {
            ApiParameter parameter = parameters.get(i);
            if (parameter.varargs() && i != parameters.size() - 1) {
                throw new IllegalArgumentException("varargs parameter must be last");
            }
            if (optionalSeen && !parameter.optional() && !parameter.varargs()) {
                throw new IllegalArgumentException("required parameter cannot follow an optional parameter");
            }
            optionalSeen |= parameter.optional() || parameter.varargs();
        }
    }

    /** 构造普通函数签名。 */
    public static ApiSignature function(List<ApiParameter> parameters, ApiTypeRef returnType) {
        return new ApiSignature(parameters, returnType, false);
    }

    /** 构造构造器签名。 */
    public static ApiSignature constructor(List<ApiParameter> parameters, ApiTypeRef returnType) {
        return new ApiSignature(parameters, returnType, true);
    }

    /** 返回调用键（仅参数形状，不含返回类型），用于 JS 重载去重。 */
    public String callKey() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0; i < parameters.size(); i++) {
            ApiParameter p = parameters.get(i);
            if (i > 0) sb.append(',');
            if (p.optional()) sb.append('?');
            if (p.varargs()) sb.append("...");
            sb.append(p.type().compatibilityKey());
        }
        sb.append(')');
        return sb.toString();
    }

    /** 返回兼容性键（调用键 + 返回类型）。 */
    public String compatibilityKey() {
        return callKey() + ":" + returnType().compatibilityKey();
    }
}
