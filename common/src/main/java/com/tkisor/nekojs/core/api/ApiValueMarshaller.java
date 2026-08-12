package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.surface.ApiCallback;
import com.tkisor.nekojs.api.data.JsNumber;
import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyArray;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ApiValueMarshaller {

    private final ApiRuntimeView runtimeView;
    private final ApiGuestErrorFactory guestErrorFactory;

    public ApiValueMarshaller(ApiRuntimeView runtimeView) {
        this(runtimeView, null);
    }

    ApiValueMarshaller(ApiRuntimeView runtimeView, ApiGuestErrorFactory guestErrorFactory) {
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.guestErrorFactory = guestErrorFactory;
    }

    public ApiSignature selectSignature(ApiSymbol symbol, List<?> rawArgs) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(rawArgs, "rawArgs");

        ApiSignature selected = null;
        int selectedScore = -1;
        for (ApiSignature signature : symbol.signatures()) {
            int score = signatureScore(signature, rawArgs);
            if (score > selectedScore) {
                selected = signature;
                selectedScore = score;
            } else if (score >= 0 && score == selectedScore) {
                throw new ApiRuntimeException(
                        ApiErrorCodes.AMBIGUOUS_CALL,
                        "Multiple signatures match " + symbol.id(),
                        symbol.id().value(), null, null, null, null);
            }
        }

        if (selected != null) {
            return selected;
        }

        throw new ApiRuntimeException(
                ApiErrorCodes.NO_MATCHING_SIGNATURE,
                "No matching signature for " + symbol.id() + " with " + rawArgs.size() + " arguments",
                symbol.id().value(), null, null, null, null);
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
                            ApiErrorCodes.CALLBACK_NOT_EXECUTABLE,
                            "Argument " + i + " is not executable for callback parameter",
                            symbolIdStr, null, null, null, null);
                }
            } else if (rawArg instanceof Value graalValue) {
                marshalled.add(convertValue(graalValue, paramType, symbolIdStr));
            } else if (isJsonType(paramType)) {
                if (rawArg instanceof JsonValue json) {
                    marshalled.add(json);
                } else {
                    throw typeMismatch(symbolIdStr, "JSON input must be a managed JsonValue or guest JSON value");
                }
            } else if (isNbtType(paramType)) {
                if (rawArg instanceof NbtValue nbt) {
                    marshalled.add(nbt);
                } else {
                    throw typeMismatch(symbolIdStr, "NBT input must be a managed NbtValue or guest NBT value");
                }
            } else if (isNbtPrimitiveArraySymbol(symbolIdStr) && rawArg instanceof List<?> values
                    && values.size() > NbtValue.MAX_NODES) {
                throw nbtLimit(symbolIdStr, "NBT primitive array exceeds " + NbtValue.MAX_NODES + " values");
            } else {
                marshalled.add(rawArg);
            }
        }

        return marshalled;
    }

    private Object convertValue(Value graalValue, ApiTypeRef paramType, String symbolIdStr) {
        if (graalValue == null) {
            return null;
        }
        if (graalValue.isNull()) {
            if (isJsonType(paramType)) return JsonValue.nullValue();
            if (isNbtType(paramType)) throw typeMismatch(symbolIdStr, "NBT input cannot be null");
            return null;
        }

        if (paramType.kind() == ApiTypeRef.Kind.UNION) {
            List<ApiTypeRef> matches = paramType.arguments().stream()
                    .filter(type -> valueScore(graalValue, type) >= 0)
                    .toList();
            if (matches.size() != 1) {
                throw new ApiRuntimeException(
                        ApiErrorCodes.TYPE_MISMATCH,
                        "Argument does not match exactly one union branch (received " + describeValue(graalValue) + ")",
                        symbolIdStr, null, null, null, null);
            }
            return convertValue(graalValue, matches.getFirst(), symbolIdStr);
        }

        if (paramType.kind() == ApiTypeRef.Kind.CALLBACK) {
            if (graalValue.canExecute()) {
                return wrapCallback(graalValue, paramType.callbackSignature());
            }
            throw new ApiRuntimeException(
                    ApiErrorCodes.CALLBACK_NOT_EXECUTABLE,
                    "Argument is not executable for callback parameter",
                    symbolIdStr, null, null, null, null);
        }

        if (paramType.kind() == ApiTypeRef.Kind.PRIMITIVE) {
            String typeName = paramType.name();
            if ("json".equals(typeName)) {
                return convertJsonValue(graalValue, symbolIdStr, 0, new JsonBudget());
            }
            if ("nbt".equals(typeName)) {
                return convertNbtValue(graalValue, symbolIdStr, 0, new NbtBudget());
            }
            if ("string".equals(typeName) && graalValue.isString()) {
                return graalValue.asString();
            }
            if ("number".equals(typeName) && graalValue.isNumber()) {
                double number = graalValue.asDouble();
                if (!Double.isFinite(number)) {
                    throw new ApiRuntimeException(
                            ApiErrorCodes.TYPE_MISMATCH,
                            "Number argument must be finite (received " + graalValue.toString() + ")",
                            symbolIdStr, null, null, null, null);
                }
                return new JsNumber(number, graalValue.toString());
            }
            if ("boolean".equals(typeName) && graalValue.isBoolean()) {
                return graalValue.asBoolean();
            }
            if ("null".equals(typeName) && graalValue.isNull()) {
                return null;
            }
            throw new ApiRuntimeException(
                    ApiErrorCodes.TYPE_MISMATCH,
                    "Argument does not match primitive type " + typeName + " (received " + describeValue(graalValue) + ")",
                    symbolIdStr, null, null, null, null);
        }


        if (paramType.kind() == ApiTypeRef.Kind.ARRAY && graalValue.hasArrayElements()) {
            if (isNbtPrimitiveArraySymbol(symbolIdStr) && graalValue.getArraySize() > NbtValue.MAX_NODES) {
                throw nbtLimit(symbolIdStr, "NBT primitive array exceeds " + NbtValue.MAX_NODES + " values");
            }
            ApiTypeRef elementType = paramType.arguments().getFirst();
            List<Object> values = new ArrayList<>();
            for (long i = 0; i < graalValue.getArraySize(); i++) {
                values.add(convertValue(graalValue.getArrayElement(i), elementType, symbolIdStr));
            }
            return java.util.Collections.unmodifiableList(values);
        }

        if (paramType.kind() == ApiTypeRef.Kind.SYMBOL && graalValue.isProxyObject()) {
            Object proxy = graalValue.asProxyObject();
            if (proxy instanceof ApiFacadeProxy facade) {
                ApiSymbolId expectedType = ApiSymbolId.parse(paramType.name());
                if (!facade.typeId().equals(expectedType)) {
                    throw new ApiRuntimeException(
                            ApiErrorCodes.TYPE_MISMATCH,
                            "Expected " + expectedType + " but received " + facade.typeId());
                }
                return facade.implementation();
            }
        }

        return graalValue;
    }

    private JsonValue convertJsonValue(Value value, String symbolIdStr, int depth, JsonBudget budget) {
        budget.visit(depth);
        if (value == null || value.isNull()) return JsonValue.nullValue();
        if (value.isProxyObject()) {
            Object proxy = value.asProxyObject();
            if (proxy instanceof ApiFacadeProxy facade && facade.implementation() instanceof JsonValue json) {
                return json;
            }
            throw typeMismatch(symbolIdStr, "JSON input must not contain unsupported proxy values");
        }
        if (value.isString()) {
            String string = value.asString();
            if (string.length() > JsonValue.MAX_STRING_CHARS) {
                throw jsonLimit(symbolIdStr, "JSON string exceeds " + JsonValue.MAX_STRING_CHARS + " characters");
            }
            try {
                return JsonValue.string(string);
            } catch (IllegalArgumentException e) {
                throw typeMismatch(symbolIdStr, "JSON string is not valid UTF-16");
            }
        }
        if (value.isBoolean()) return JsonValue.bool(value.asBoolean());
        if (value.isNumber()) {
            double number = value.asDouble();
            if (!Double.isFinite(number)) {
                throw typeMismatch(symbolIdStr, "JSON number must be finite");
            }
            return JsonValue.number(value.toString());
        }
        if (value.canExecute() || value.isHostObject()) {
            throw typeMismatch(symbolIdStr, "JSON input must not contain executable or host values");
        }
        if (value.hasArrayElements()) {
            budget.enter(value, symbolIdStr);
            try {
                List<JsonValue> values = new ArrayList<>();
                for (long index = 0; index < value.getArraySize(); index++) {
                    values.add(convertJsonValue(value.getArrayElement(index), symbolIdStr, depth + 1, budget));
                }
                return JsonValue.array(values);
            } finally {
                budget.exit(value);
            }
        }
        if (value.hasMembers()) {
            budget.enter(value, symbolIdStr);
            try {
                Map<String, JsonValue> values = new LinkedHashMap<>();
                for (String key : value.getMemberKeys()) {
                    if (key.length() > JsonValue.MAX_STRING_CHARS) {
                        throw jsonLimit(symbolIdStr, "JSON object key exceeds " + JsonValue.MAX_STRING_CHARS + " characters");
                    }
                    values.put(key, convertJsonValue(value.getMember(key), symbolIdStr, depth + 1, budget));
                }
                try {
                    return JsonValue.object(values);
                } catch (IllegalArgumentException e) {
                    throw typeMismatch(symbolIdStr, "JSON object contains an invalid key");
                }
            } finally {
                budget.exit(value);
            }
        }
        throw typeMismatch(symbolIdStr, "JSON input must be null, primitive, array, object, or JsonValue");
    }

    private NbtValue convertNbtValue(Value value, String symbolIdStr, int depth, NbtBudget budget) {
        budget.visit(depth, symbolIdStr);
        if (value == null || value.isNull()) throw typeMismatch(symbolIdStr, "NBT input cannot be null");
        if (value.isProxyObject()) {
            Object proxy = value.asProxyObject();
            if (proxy instanceof ApiFacadeProxy facade && facade.implementation() instanceof NbtValue nbt) return nbt;
            throw typeMismatch(symbolIdStr, "NBT input must not contain unsupported proxy values");
        }
        if (value.isString()) {
            try {
                return NbtValue.string(value.asString());
            } catch (IllegalArgumentException error) {
                throw nbtLimit(symbolIdStr, "NBT string exceeds " + NbtValue.MAX_STRING_CHARS + " characters");
            }
        }
        if (value.isNumber()) {
            double number = value.asDouble();
            if (!Double.isFinite(number)) throw typeMismatch(symbolIdStr, "NBT number must be finite");
            if (number == Math.rint(number) && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return NbtValue.intValue((int) number);
            }
            return NbtValue.doubleValue(number);
        }
        if (value.isBoolean() || value.canExecute() || value.isHostObject()) {
            throw typeMismatch(symbolIdStr, "NBT input must not contain boolean, executable, or host values");
        }
        if (value.hasArrayElements()) {
            budget.enter(value, symbolIdStr);
            try {
                List<NbtValue> values = new ArrayList<>();
                for (long index = 0; index < value.getArraySize(); index++) {
                    values.add(convertNbtValue(value.getArrayElement(index), symbolIdStr, depth + 1, budget));
                }
                try {
                    return NbtValue.list(values);
                } catch (IllegalArgumentException error) {
                    throw typeMismatch(symbolIdStr, "NBT list values must share one element kind");
                }
            } finally {
                budget.exit(value);
            }
        }
        if (value.hasMembers()) {
            budget.enter(value, symbolIdStr);
            try {
                Map<String, NbtValue> values = new LinkedHashMap<>();
                for (String key : value.getMemberKeys()) {
                    if (key.length() > NbtValue.MAX_STRING_CHARS) {
                        throw nbtLimit(symbolIdStr, "NBT compound key exceeds " + NbtValue.MAX_STRING_CHARS + " characters");
                    }
                    values.put(key, convertNbtValue(value.getMember(key), symbolIdStr, depth + 1, budget));
                }
                return NbtValue.compound(values);
            } finally {
                budget.exit(value);
            }
        }
        throw typeMismatch(symbolIdStr, "NBT input must be string, number, array, object, or NbtValue");
    }

    public Object marshalReturn(Object rawReturn, ApiTypeRef returnType, boolean nativeReturn, String symbolIdStr) {
        Objects.requireNonNull(returnType, "returnType");

        if (returnType.kind() == ApiTypeRef.Kind.VOID) {
            return null;
        }

        if (rawReturn == null) {
            if (acceptsNull(returnType)) {
                return null;
            }
            throw new ApiRuntimeException(
                    ApiErrorCodes.API_CONTRACT_VIOLATION,
                    "Null return does not match " + returnType.compatibilityKey(),
                    symbolIdStr, null, null, null, null);
        }

        if (returnType.kind() == ApiTypeRef.Kind.UNION) {
            int bestScore = -1;
            ApiTypeRef selected = null;
            boolean ambiguous = false;
            for (ApiTypeRef branch : returnType.arguments()) {
                int score = returnBranchScore(rawReturn, branch);
                if (score > bestScore) {
                    bestScore = score;
                    selected = branch;
                    ambiguous = false;
                } else if (score >= 0 && score == bestScore) {
                    ambiguous = true;
                }
            }
            if (selected == null || ambiguous) {
                throw new ApiRuntimeException(
                        ApiErrorCodes.API_CONTRACT_VIOLATION,
                        "Cannot select a return branch for " + returnType.compatibilityKey());
            }
            return marshalReturn(rawReturn, selected, nativeReturn, symbolIdStr);
        }

        if (isPrimitive(rawReturn) && matchesPrimitive(rawReturn, returnType)) {
            return rawReturn;
        }

        if (isPrimitive(rawReturn)) {
            throw new ApiRuntimeException(
                    ApiErrorCodes.API_CONTRACT_VIOLATION,
                    "Return value does not match " + returnType.compatibilityKey(),
                    symbolIdStr, null, null, null, null);
        }

        if (returnType.kind() == ApiTypeRef.Kind.SYMBOL) {
            return ApiFacadeProxy.value(
                    runtimeView, ApiSymbolId.parse(returnType.name()), rawReturn, guestErrorFactory);
        }

        if (returnType.kind() == ApiTypeRef.Kind.ARRAY) {
            ApiTypeRef elementType = returnType.arguments().getFirst();
            List<Object> marshalled = new ArrayList<>();
            if (rawReturn instanceof Iterable<?> values) {
                for (Object value : values) {
                    marshalled.add(marshalReturn(value, elementType, false, symbolIdStr));
                }
                return ProxyArray.fromList(marshalled);
            }
            if (rawReturn.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(rawReturn); i++) {
                    marshalled.add(marshalReturn(Array.get(rawReturn, i), elementType, false, symbolIdStr));
                }
                return ProxyArray.fromList(marshalled);
            }
        }

        if (nativeReturn) {
            return rawReturn;
        }

        if (rawReturn instanceof Value graalValue) {
            return graalValue;
        }

        throw new ApiRuntimeException(
                ApiErrorCodes.NATIVE_TYPE_LEAK,
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
                            ApiErrorCodes.CALLBACK_NOT_EXECUTABLE,
                            "Callback payload argument " + i + " is not executable");
                }
            } else {
                marshalled.add(rawArg);
            }
        }

        return marshalled;
    }

    private int signatureScore(ApiSignature signature, List<?> rawArgs) {
        List<ApiParameter> params = signature.parameters();
        int requiredCount = (int) params.stream().filter(p -> !p.optional() && !p.varargs()).count();
        int totalCount = params.size();
        boolean hasVarargs = !params.isEmpty() && params.get(params.size() - 1).varargs();

        if (rawArgs.size() < requiredCount) {
            return -1;
        }

        if (!hasVarargs && rawArgs.size() > totalCount) {
            return -1;
        }

        int score = 100;
        for (int i = 0; i < rawArgs.size(); i++) {
            ApiTypeRef type = i < params.size() ? params.get(i).type() : params.get(params.size() - 1).type();
            int argumentScore = valueScore(rawArgs.get(i), type);
            if (argumentScore < 0) {
                return -1;
            }
            score += argumentScore;
        }
        int omittedCount = Math.max(0, params.size() - rawArgs.size());
        score -= omittedCount;
        if (hasVarargs) {
            score -= 1;
        }
        return score;
    }

    private int valueScore(Object value, ApiTypeRef type) {
        if (type.kind() == ApiTypeRef.Kind.UNION) {
            int score = type.arguments().stream().mapToInt(member -> valueScore(value, member)).max().orElse(-1);
            return score < 0 ? -1 : score - 1;
        }
        if (value == null || value instanceof Value graalValue && graalValue.isNull()) {
            if (isJsonType(type)) return 4;
            return type.kind() == ApiTypeRef.Kind.PRIMITIVE && "null".equals(type.name()) ? 4 : -1;
        }
        if (value instanceof Value graalValue) {
            return switch (type.kind()) {
                case PRIMITIVE -> primitiveScore(graalValue, type.name());
                case CALLBACK -> graalValue.canExecute() ? 4 : -1;
                case ARRAY -> graalValue.hasArrayElements() ? 4 : -1;
                case SYMBOL -> symbolScore(graalValue, type);
                case UNION, VOID, TYPE_VARIABLE -> -1;
            };
        }
        return switch (type.kind()) {
            case PRIMITIVE -> matchesPrimitive(value, type) ? 4 : -1;
            case CALLBACK -> value instanceof ApiCallback ? 4 : -1;
            case ARRAY -> value instanceof Iterable<?> || value.getClass().isArray() ? 4 : -1;
            case SYMBOL -> value instanceof ApiFacadeProxy facade
                    && facade.typeId().equals(ApiSymbolId.parse(type.name())) ? 4 : -1;
            case UNION, VOID, TYPE_VARIABLE -> -1;
        };
    }

    private int symbolScore(Value value, ApiTypeRef type) {
        if (!value.isProxyObject()) {
            return -1;
        }
        Object proxy = value.asProxyObject();
        return proxy instanceof ApiFacadeProxy facade
                && facade.typeId().equals(ApiSymbolId.parse(type.name())) ? 4 : -1;
    }

    private int primitiveScore(Value value, String typeName) {
        return switch (typeName) {
            case "string" -> value.isString() ? 4 : -1;
            case "number" -> value.isNumber() ? 4 : -1;
            case "boolean" -> value.isBoolean() ? 4 : -1;
            case "null" -> value.isNull() ? 4 : -1;
            case "object" -> value.hasMembers() || value.hasArrayElements() ? 1 : -1;
            case "json" -> jsonScore(value);
            case "nbt" -> nbtScore(value);
            default -> -1;
        };
    }

    private int jsonScore(Value value) {
        if (value.isNull() || value.isString() || value.isBoolean() || value.isNumber()) return 4;
        if (value.isProxyObject()) {
            Object proxy = value.asProxyObject();
            return proxy instanceof ApiFacadeProxy facade && facade.implementation() instanceof JsonValue ? 5 : -1;
        }
        return !value.canExecute() && !value.isHostObject() && (value.hasArrayElements() || value.hasMembers()) ? 3 : -1;
    }

    private int nbtScore(Value value) {
        if (value.isString() || value.isNumber()) return 4;
        if (value.isProxyObject()) {
            Object proxy = value.asProxyObject();
            return proxy instanceof ApiFacadeProxy facade && facade.implementation() instanceof NbtValue ? 5 : -1;
        }
        return !value.isBoolean() && !value.canExecute() && !value.isHostObject()
                && (value.hasArrayElements() || value.hasMembers()) ? 3 : -1;
    }

    private boolean matchesPrimitive(Object value, ApiTypeRef type) {
        if (type.kind() != ApiTypeRef.Kind.PRIMITIVE) {
            return false;
        }
        return switch (type.name()) {
            case "string" -> value instanceof String || value instanceof Character;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            case "object" -> true;
            case "json" -> value instanceof JsonValue;
            case "nbt" -> value instanceof NbtValue;
            default -> false;
        };
    }

    private static boolean isJsonType(ApiTypeRef type) {
        return type.kind() == ApiTypeRef.Kind.PRIMITIVE && "json".equals(type.name());
    }

    private static boolean isNbtType(ApiTypeRef type) {
        return type.kind() == ApiTypeRef.Kind.PRIMITIVE && "nbt".equals(type.name());
    }

    private static boolean isNbtPrimitiveArraySymbol(String symbolId) {
        return "member:NBT.byteArray".equals(symbolId) || "member:NBT.intArray".equals(symbolId);
    }

    private static ApiRuntimeException typeMismatch(String symbolId, String message) {
        return new ApiRuntimeException(ApiErrorCodes.TYPE_MISMATCH, message, symbolId, null, null, null, null);
    }

    /**
     * Produces a short human-readable description of a Graal {@link Value}'s JS kind,
     * for inclusion in type-mismatch error messages so the script author can see what
     * they actually passed. Capped at ~80 chars to avoid leaking huge structures.
     */
    private static String describeValue(Value graalValue) {
        String kind;
        if (graalValue.isNull()) {
            return "null";
        }
        if (graalValue.isString()) {
            kind = "string";
        } else if (graalValue.isNumber()) {
            kind = "number";
        } else if (graalValue.isBoolean()) {
            kind = "boolean";
        } else if (graalValue.canExecute()) {
            kind = "function";
        } else if (graalValue.hasArrayElements()) {
            kind = "array";
        } else if (graalValue.isHostObject()) {
            kind = "host object (" + safeClassName(graalValue) + ")";
        } else if (graalValue.hasMembers()) {
            kind = "object";
        } else {
            kind = "unknown";
        }
        if ("string".equals(kind) || "number".equals(kind) || "boolean".equals(kind)) {
            String repr = graalValue.toString();
            if (repr.length() > 60) repr = repr.substring(0, 57) + "...";
            return kind + " \"" + repr + "\"";
        }
        return kind;
    }

    private static String safeClassName(Value graalValue) {
        try {
            Object host = graalValue.as(Object.class);
            return host != null ? host.getClass().getSimpleName() : "null";
        } catch (Exception ignored) {
            return "?";
        }
    }

    private static ApiRuntimeException jsonLimit(String symbolId, String message) {
        return new ApiRuntimeException(ApiErrorCodes.JSON_LIMIT_EXCEEDED, message, symbolId, null, null, null, null);
    }

    private static ApiRuntimeException nbtLimit(String symbolId, String message) {
        return new ApiRuntimeException(ApiErrorCodes.NBT_LIMIT_EXCEEDED, message, symbolId, null, null, null, null);
    }

    private static final class JsonBudget {
        private int nodes;
        // Value equality delegates to the guest receiver, so wrappers of the same JS container compare equal.
        private final Set<Value> active = new HashSet<>();

        void visit(int depth) {
            if (depth > JsonValue.MAX_DEPTH) {
                throw new ApiRuntimeException(
                        ApiErrorCodes.JSON_LIMIT_EXCEEDED,
                        "JSON nesting exceeds " + JsonValue.MAX_DEPTH,
                        null, null, null, null, null);
            }
            if (++nodes > JsonValue.MAX_NODES) {
                throw new ApiRuntimeException(
                        ApiErrorCodes.JSON_LIMIT_EXCEEDED,
                        "JSON contains more than " + JsonValue.MAX_NODES + " values",
                        null, null, null, null, null);
            }
        }

        void enter(Value value, String symbolId) {
            if (!active.add(value)) {
                throw typeMismatch(symbolId, "JSON input must not contain cycles");
            }
        }

        void exit(Value value) {
            active.remove(value);
        }
    }

    private static final class NbtBudget {
        private int nodes;
        private final Set<Value> active = new HashSet<>();

        void visit(int depth, String symbolId) {
            if (depth > NbtValue.MAX_DEPTH) throw nbtLimit(symbolId, "NBT nesting exceeds " + NbtValue.MAX_DEPTH);
            if (++nodes > NbtValue.MAX_NODES) throw nbtLimit(symbolId, "NBT contains more than " + NbtValue.MAX_NODES + " values");
        }

        void enter(Value value, String symbolId) {
            if (!active.add(value)) throw typeMismatch(symbolId, "NBT input must not contain cycles");
        }

        void exit(Value value) {
            active.remove(value);
        }
    }

    private int returnBranchScore(Object value, ApiTypeRef type) {
        if (type.kind() == ApiTypeRef.Kind.UNION) {
            return type.arguments().stream().mapToInt(branch -> returnBranchScore(value, branch)).max().orElse(-1);
        }
        return switch (type.kind()) {
            case PRIMITIVE -> !matchesPrimitive(value, type) ? -1 : "object".equals(type.name()) ? 1 : 4;
            case ARRAY -> value instanceof Iterable<?> || value.getClass().isArray() ? 4 : -1;
            case SYMBOL -> isPrimitive(value) || value instanceof Iterable<?> || value.getClass().isArray() ? -1 : 2;
            case CALLBACK -> value instanceof ApiCallback ? 4 : -1;
            case UNION, VOID, TYPE_VARIABLE -> -1;
        };
    }

    private boolean acceptsNull(ApiTypeRef type) {
        if (type.kind() == ApiTypeRef.Kind.PRIMITIVE) {
            return "null".equals(type.name());
        }
        return type.kind() == ApiTypeRef.Kind.UNION && type.arguments().stream().anyMatch(this::acceptsNull);
    }

    private boolean isPrimitive(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character;
    }

}
