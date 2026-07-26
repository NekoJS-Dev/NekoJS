package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.ApiCallback;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import graal.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ApiValueMarshaller {

    private final ApiRuntimeView runtimeView;
    private final Set<ApiSymbolId> registeredSymbolIds;

    public ApiValueMarshaller(ApiRuntimeView runtimeView, Set<ApiSymbolId> registeredSymbolIds) {
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.registeredSymbolIds = Set.copyOf(registeredSymbolIds);
    }

    public ApiSignature selectSignature(ApiSymbol symbol, List<?> rawArgs) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(rawArgs, "rawArgs");

        for (ApiSignature sig : symbol.signatures()) {
            if (matchesSignature(sig, rawArgs)) {
                return sig;
            }
        }

        throw new ApiRuntimeException(
                "NO_MATCHING_SIGNATURE",
                "No matching signature for " + symbol.id() + " with " + rawArgs.size() + " arguments");
    }

    public List<Object> marshalArguments(ApiSignature signature, List<?> rawArgs, String symbolIdStr) {
        Objects.requireNonNull(signature, "signature");
        List<Object> marshalled = new ArrayList<>();
        List<ApiParameter> params = signature.parameters();

        for (int i = 0; i < rawArgs.size(); i++) {
            Object rawArg = rawArgs.get(i);
            ApiTypeRef paramType = i < params.size() ? params.get(i).type() : params.get(params.size() - 1).type();

            if (paramType.kind() == ApiTypeRef.Kind.CALLBACK && rawArg instanceof Value graalValue) {
                if (graalValue.canExecute()) {
                    marshalled.add(wrapCallback(graalValue, paramType.callbackSignature()));
                } else {
                    throw new ApiRuntimeException(
                            "CALLBACK_NOT_EXECUTABLE",
                            "Argument " + i + " is not executable for callback parameter",
                            symbolIdStr, null, null, null, null);
                }
            } else if (rawArg instanceof Value graalValue) {
                marshalled.add(convertValue(graalValue, paramType));
            } else {
                marshalled.add(rawArg);
            }
        }

        return marshalled;
    }

    private Object convertValue(Value graalValue, ApiTypeRef paramType) {
        if (graalValue == null || graalValue.isNull()) {
            return null;
        }

        if (paramType.kind() == ApiTypeRef.Kind.PRIMITIVE) {
            String typeName = paramType.name();
            if ("string".equals(typeName) && graalValue.isString()) {
                return graalValue.asString();
            }
            if ("number".equals(typeName) && graalValue.isNumber()) {
                return graalValue.asDouble();
            }
            if ("boolean".equals(typeName) && graalValue.isBoolean()) {
                return graalValue.asBoolean();
            }
        }

        return graalValue;
    }

    public Object marshalReturn(Object rawReturn, ApiTypeRef returnType, boolean nativeReturn, String symbolIdStr) {
        Objects.requireNonNull(returnType, "returnType");

        if (returnType.kind() == ApiTypeRef.Kind.VOID) {
            return null;
        }

        if (rawReturn == null) {
            return null;
        }

        if (isPrimitive(rawReturn)) {
            return rawReturn;
        }

        if (nativeReturn) {
            return rawReturn;
        }

        if (rawReturn instanceof Value graalValue) {
            return graalValue;
        }

        if (isRegisteredSymbol(rawReturn)) {
            return rawReturn;
        }

        throw new ApiRuntimeException(
                "NATIVE_TYPE_LEAK",
                "Return value of type " + rawReturn.getClass().getName() + " is not allowed outside VERSION tier",
                symbolIdStr, null, null, null, null);
    }

    private ApiCallback wrapCallback(Value graalValue, ApiSignature callbackSignature) {
        return rawArgs -> {
            List<Object> marshalledArgs = marshalCallbackPayload(callbackSignature, rawArgs);
            Value result = graalValue.execute(marshalledArgs.toArray());
            if (result == null || result.isNull()) {
                return null;
            }
            if (result.isString()) return result.asString();
            if (result.isNumber()) return result.asDouble();
            if (result.isBoolean()) return result.asBoolean();
            return result;
        };
    }

    private List<Object> marshalCallbackPayload(ApiSignature callbackSignature, List<?> rawArgs) {
        List<Object> marshalled = new ArrayList<>();
        List<ApiParameter> params = callbackSignature.parameters();

        for (int i = 0; i < rawArgs.size(); i++) {
            Object rawArg = rawArgs.get(i);
            ApiTypeRef paramType = i < params.size() ? params.get(i).type() : params.get(params.size() - 1).type();

            if (paramType.kind() == ApiTypeRef.Kind.CALLBACK && rawArg instanceof Value graalValue) {
                if (graalValue.canExecute()) {
                    marshalled.add(wrapCallback(graalValue, paramType.callbackSignature()));
                } else {
                    throw new ApiRuntimeException(
                            "CALLBACK_NOT_EXECUTABLE",
                            "Callback payload argument " + i + " is not executable");
                }
            } else {
                marshalled.add(rawArg);
            }
        }

        return marshalled;
    }

    private boolean matchesSignature(ApiSignature signature, List<?> rawArgs) {
        List<ApiParameter> params = signature.parameters();
        int requiredCount = (int) params.stream().filter(p -> !p.optional()).count();
        int totalCount = params.size();
        boolean hasVarargs = !params.isEmpty() && params.get(params.size() - 1).varargs();

        if (rawArgs.size() < requiredCount) {
            return false;
        }

        if (!hasVarargs && rawArgs.size() > totalCount) {
            return false;
        }

        return true;
    }

    private boolean isPrimitive(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character;
    }

    private boolean isRegisteredSymbol(Object value) {
        if (value == null) return false;
        String className = value.getClass().getName();
        return registeredSymbolIds.stream()
                .anyMatch(id -> {
                    String qn = id.qualifiedName();
                    return qn.equals(className) || className.startsWith(qn + ".");
                });
    }
}
