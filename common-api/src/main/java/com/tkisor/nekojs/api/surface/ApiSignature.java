package com.tkisor.nekojs.api.surface;

import java.util.List;
import java.util.Objects;

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

    public static ApiSignature function(List<ApiParameter> parameters, ApiTypeRef returnType) {
        return new ApiSignature(parameters, returnType, false);
    }

    public static ApiSignature constructor(List<ApiParameter> parameters, ApiTypeRef returnType) {
        return new ApiSignature(parameters, returnType, true);
    }

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

    public String compatibilityKey() {
        return callKey() + ":" + returnType().compatibilityKey();
    }
}
