package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.ContractReceiver;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.event.ScriptEventRegistrationEvent;
import com.tkisor.nekojs.api.facade.ModInfoValue;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.data.PerfTimerValue;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reflects {@link ApiSymbol} and {@link ApiSignature} from Java facade interfaces and data types.
 *
 * <p>Java method signatures are the source of truth for the contract, replacing handwritten JSON:
 * <ul>
 *   <li>{@link #extractSymbols(String, Class, boolean)}: Reflects facade interfaces to produce
 *       {@code global:<facadeName>} and {@code member:<facadeName>.<method>}. If the first parameter is
 *       a data type, it additionally produces a receiver symbol {@code member:<DataType>.<method>}.
 *   <li>{@link #reflectDataType(String, Class)}: Reflects data types (record accessors or instance methods)
 *       to produce {@code member:<contractName>.<member>}. Covers methods not reachable by receiver
 *       double-production, such as {@code RegistryView.exists} or {@code ModInfo.id}.
 *   <li>{@link #reflectEventRegistrationSymbols()}: Reflects common-side {@link ScriptEventRegistrationEvent},
 *       mapping Graal and ScriptType parameters to {@code object}.
 * </ul>
 *
 * <p>Parameter names are reflected from method parameters (requires {@code -parameters} compilation).
 * Parameter and return types are converted to {@link ApiTypeRef} using {@link #toTypeRef(Type)}.
 * NekoJS data types (eligible as receivers) and class-to-contract name mappings are declared
 * using the {@link ContractReceiver} annotation in common-api (see {@link #isReceiverType} /
 * {@link #contractNameOf}), avoiding hardcoding in this class.
 */
public final class ContractReflector {

    private ContractReflector() {
    }

    /**
     * Reflects a list of symbols from a facade interface.
     *
     * <p>Each public method produces one {@link ApiSymbol}:
     * <ul>
     *   <li>Global symbol (no parameter receiver): {@code global:<facadeName>}
     *   <li>Member method: {@code member:<facadeName>.<methodName>}. Overloads with the same name are merged
     *       into a single symbol (multiple signatures).
     *   <li>If the first parameter is a data type, an additional receiver symbol
     *       {@code member:<DataType>.<methodName>} is produced (receiver does not count as a script
     *       parameter, parameter offset is 1).
     * </ul>
     *
     * @param facadeName Global name (e.g., "Text")
     * @param facadeType Facade interface class
     * @return List of symbols
     */
    public static List<ApiSymbol> extractSymbols(String facadeName, Class<?> facadeType) {
        return extractSymbols(facadeName, facadeType, true);
    }

    /**
     * @param includeGlobal {@code true} to produce the {@code global:<facadeName>} symbol
     *                      (required for facades); {@code false} otherwise (not for data types
     *                      like ModInfoValue).
     */
    public static List<ApiSymbol> extractSymbols(String facadeName, Class<?> facadeType, boolean includeGlobal) {
        List<ApiSymbol> symbols = new ArrayList<>();

        // global:<facadeName> — facade object itself (not for data types)
        if (includeGlobal) {
            symbols.add(new ApiSymbol(
                ApiSymbolId.parse("global:" + facadeName),
                List.of(new ApiSignature(
                        List.of(),
                        ApiTypeRef.primitive("object"),
                        false))));
        }

        // Collect signatures grouped by symbol ID (handles overload merging + static/receiver double-production)
        Map<String, List<ApiSignature>> signaturesByName = new java.util.LinkedHashMap<>();
        for (Method method : facadeType.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            if (method.getName().startsWith("neko$")) continue;
            String jsName = jsNameOf(method);

            // Static symbol id: member:<facadeName>.<method>, with every Java parameter as a script argument
            String staticId = "member:" + facadeName + "." + jsName;
            signaturesByName
                    .computeIfAbsent(staticId, k -> new ArrayList<>())
                    .add(methodToSignature(method, 0));

            // Receiver symbol: if the first parameter is a data type, produce an additional
            // member:<DataType>.<method> (in script, dataType.method(), receiver is not a parameter)
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length > 0 && isReceiverType(paramTypes[0])) {
                String receiverContractName = contractNameOf(paramTypes[0]);
                String receiverId = "member:" + receiverContractName + "." + jsName;
                signaturesByName
                        .computeIfAbsent(receiverId, k -> new ArrayList<>())
                        .add(methodToSignature(method, 1));
            }
        }

        for (var entry : signaturesByName.entrySet()) {
            symbols.add(new ApiSymbol(ApiSymbolId.parse(entry.getKey()), entry.getValue()));
        }
        return symbols;
    }

    /**
     * Reflects a data type itself (not a facade), producing {@code member:<contractName>.<member>}.
     *
     * <p>Covers scenarios missed by receiver double-production: methods declared directly on the
     * data type (e.g., {@code RegistryView.exists}, {@code TextValue.isEmpty},
     * {@code PerfTimerValue.mark}) which have no corresponding facade method using them as the first
     * parameter.
     *
     * <p>Handles two types of data types:
     * <ul>
     *   <li><b>Records</b> (e.g., {@link NbtEntry}, {@link ModInfoValue}): Each record component
     *       produces a zero-parameter symbol (accessor).
     *   <li><b>Classes/Interfaces</b> (e.g., {@link RegistryView}, {@link PerfTimerValue},
     *       {@link TextValue}): Scans public instance methods (excludes static, synthetic, and
     *       {@code neko$} prefix).
     * </ul>
     *
     * <p>Overloads with the same name are merged into a single symbol (multiple signatures).
     *
     * @param contractName Contract name (name exposed to JS, e.g., "ModInfo")
     * @param dataType Data type class
     * @return List of symbols
     */
    public static List<ApiSymbol> reflectDataType(String contractName, Class<?> dataType) {
        Map<String, List<ApiSignature>> signaturesByName = new java.util.LinkedHashMap<>();

        // Record: produce a zero-parameter accessor symbol for each component
        if (dataType.isRecord()) {
            for (RecordComponent component : dataType.getRecordComponents()) {
                String id = "member:" + contractName + "." + component.getName();
                ApiTypeRef returnType = toReturnType(component.getGenericType());
                signaturesByName
                        .computeIfAbsent(id, k -> new ArrayList<>())
                        .add(new ApiSignature(List.of(), returnType, false));
            }
            return toSymbols(signaturesByName);
        }

        // Ordinary class/interface: scan declared public instance methods
        for (Method method : dataType.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (!Modifier.isPublic(method.getModifiers())) continue;
            if (method.getName().startsWith("neko$")) continue;
            // Skip methods inherited from Object (toString/hashCode/equals)
            if (isObjectMethod(method)) continue;

            String id = "member:" + contractName + "." + jsNameOf(method);
            signaturesByName
                    .computeIfAbsent(id, k -> new ArrayList<>())
                    .add(methodToSignature(method, 0));
        }
        return toSymbols(signaturesByName);
    }

    /**
     * Reflects {@link ScriptEventRegistrationEvent} (common-side), producing
     * {@code member:ScriptEventRegistrationEvent.<method>}.
     *
     * <p>Graal {@code Value}, {@link ScriptType}, and {@code Object} parameters are all mapped
     * to {@code PRIMITIVE object} (consistent with the original JSON contract as these are
     * transparent runtime types). {@code targetType()} has zero parameters, and the four
     * {@code register} overloads are merged.
     */
    public static List<ApiSymbol> reflectEventRegistrationSymbols() {
        return reflectDataType("ScriptEventRegistrationEvent", ScriptEventRegistrationEvent.class);
    }

    /**
     * Checks if a class is a NekoJS data type (eligible as a receiver), declared by the
     * {@link ContractReceiver} annotation in common-api to avoid hardcoding. If the first
     * parameter of a facade method is one of these types, an additional receiver member
     * symbol is produced for that type.
     */
    private static boolean isReceiverType(Class<?> cls) {
        return cls.isAnnotationPresent(ContractReceiver.class);
    }

    /**
     * Gets the contract name for a data type: prefers {@link ContractReceiver#value()},
     * falling back to the class simple name. Uses the same annotation as
     * {@link #isReceiverType} for a single source of truth.
     */
    private static String contractNameOf(Class<?> cls) {
        ContractReceiver receiver = cls.getAnnotation(ContractReceiver.class);
        if (receiver != null && !receiver.value().isEmpty()) {
            return receiver.value();
        }
        return cls.getSimpleName();
    }

    /**
     * Returns the name a method is visible under in JS: the {@link Remap} annotation when present
     * (for Java keyword clashes such as {@code byteValue} -> {@code byte}), the method name
     * otherwise. Graal host access reads the same annotation in {@code MemberRemapper}, so there is
     * one source of truth.
     */
    private static String jsNameOf(Method method) {
        Remap remap = method.getAnnotation(Remap.class);
        return remap != null ? remap.value() : method.getName();
    }

    private static boolean isObjectMethod(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static List<ApiSymbol> toSymbols(Map<String, List<ApiSignature>> signaturesByName) {
        List<ApiSymbol> symbols = new ArrayList<>();
        for (var entry : signaturesByName.entrySet()) {
            symbols.add(new ApiSymbol(ApiSymbolId.parse(entry.getKey()), entry.getValue()));
        }
        return symbols;
    }

    private static ApiSignature methodToSignature(Method method, int paramOffset) {
        List<ApiParameter> params = new ArrayList<>();
        java.lang.reflect.Parameter[] javaParams = method.getParameters();
        Type[] genericTypes = method.getGenericParameterTypes();

        for (int i = paramOffset; i < javaParams.length; i++) {
            String name = javaParams[i].isNamePresent()
                    ? javaParams[i].getName()
                    : "arg" + (i - paramOffset);
            ApiTypeRef type = toTypeRef(genericTypes[i]);
            // Varargs: a native Java varargs parameter (...) or a trailing List<Object>, which by convention
            // is the vararg union (for example the values parameter of Text.append/Text.translatable)
            boolean varargs = i == javaParams.length - 1
                    && (method.isVarArgs() || isVarargsList(genericTypes[i]));
            params.add(new ApiParameter(name, type, false, varargs));
        }

        ApiTypeRef returnType = toReturnType(method.getGenericReturnType());
        return new ApiSignature(params, returnType, false);
    }

    /** A trailing {@code List<Object>} means the vararg union (string|number|boolean|TextValue). */
    private static boolean isVarargsList(Type type) {
        return type instanceof ParameterizedType pt && pt.getRawType() == List.class
                && pt.getActualTypeArguments()[0] == Object.class;
    }

    /**
     * Converts a Java Type to an ApiTypeRef.
     *
     * <p>Mapping rules:
     * <ul>
     *   <li>{@code String} -> PRIMITIVE("string")
     *   <li>{@code boolean}/{@code Boolean} -> PRIMITIVE("boolean")
     *   <li>numeric types -> PRIMITIVE("number")
     *   <li>{@code void}/{@code Void} -> VOID
     *   <li>{@code Object} -> PRIMITIVE("object")
     *   <li>{@code JsonValue} -> PRIMITIVE("json")
     *   <li>{@code NbtValue} -> PRIMITIVE("nbt")
     *   <li>{@code TextValue}/{@code NekoId}/{@code RegistryView}/{@code ModInfoValue}/{@code PerfTimerValue} -> SYMBOL("type:<ContractName>")
     *   <li>{@code List<Object>} (vararg union) -> UNION(string, number, boolean, type:TextValue) (used for Text parameters)
     *   <li>arrays -> ARRAY(element)
     *   <li>anything else -> PRIMITIVE("object") (safe fallback)
     * </ul>
     */
    public static ApiTypeRef toTypeRef(Type type) {
        if (type == null) return ApiTypeRef.primitive("object");

        // Primitive types
        if (type == String.class || type == char.class || type == Character.class) {
            return ApiTypeRef.primitive("string");
        }
        if (type == boolean.class || type == Boolean.class) {
            return ApiTypeRef.primitive("boolean");
        }
        if (type == void.class || type == Void.class) {
            return ApiTypeRef.voidType();
        }
        if (type == byte.class || type == short.class || type == int.class
                || type == long.class || type == float.class || type == double.class
                || type instanceof Class<?> c && Number.class.isAssignableFrom(c)) {
            return ApiTypeRef.primitive("number");
        }

        // Portable NekoJS data types
        if (type == JsonValue.class) return ApiTypeRef.primitive("json");
        if (type == NbtValue.class) return ApiTypeRef.primitive("nbt");
        // Other receiver data types (TextValue/NekoId/RegistryView/NbtEntry/ModInfoValue/PerfTimerValue)
        // map to a SYMBOL reference. isReceiverType is the single list, shared by toTypeRef and
        // extractSymbols.
        if (type instanceof Class<?> cls && isReceiverType(cls)) {
            return symbolRef(cls);
        }

        // Object -> vararg union (the Text parameter case)
        if (type == Object.class) {
            return ApiTypeRef.primitive("object");
        }

        // List<Object> -> vararg union
        if (type instanceof ParameterizedType pt && pt.getRawType() == List.class) {
            Type elem = pt.getActualTypeArguments()[0];
            if (elem == Object.class) {
                // List<Object> means varargs (string|number|boolean|TextValue)
                return ApiTypeRef.union(List.of(
                        ApiTypeRef.primitive("string"),
                        ApiTypeRef.primitive("number"),
                        ApiTypeRef.primitive("boolean"),
                        symbolRef(TextValue.class)));
            }
            return ApiTypeRef.array(toTypeRef(elem));
        }

        // Arrays
        if (type instanceof Class<?> cls && cls.isArray()) {
            return ApiTypeRef.array(toTypeRef(cls.componentType()));
        }

        // Parameterized collections such as List<String> -> array
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawCls) {
            if (List.class.isAssignableFrom(rawCls) && pt.getActualTypeArguments().length > 0) {
                return ApiTypeRef.array(toTypeRef(pt.getActualTypeArguments()[0]));
            }
        }

        // Safe fallback
        return ApiTypeRef.primitive("object");
    }

    private static ApiTypeRef symbolRef(Class<?> type) {
        return ApiTypeRef.symbol(ApiSymbolId.parse("type:" + contractNameOf(type)));
    }

    /**
     * Maps a return type. Differences from {@link #toTypeRef}:
     * <ul>
     *   <li>{@link JsonValue}/{@link NbtValue} as a return type map to {@code UNION(SYMBOL, null)}
     *       (ApiFacadeProxy wraps the value into a JS object with member methods; Java has no null
     *       annotation, so null is allowed conservatively, for example a failed JsonIO.read on a
     *       missing file returns null).
     *   <li>The other data types (TextValue/NekoId/RegistryView/ModInfoValue/PerfTimerValue) are
     *       already mapped to SYMBOL by {@link #toTypeRef}, and are wrapped into a nullable UNION
     *       here for consistency.
     * </ul>
     * As parameter types they are all PRIMITIVE (json/nbt values pass through, or a symbol
     * reference).
     *
     * <p>For example {@code JsonIO.parse} returns {@code JsonValue} and maps to
     * {@code UNION(type:JsonValue, null)}, so JS can keep calling {@code .toString()} on it, while
     * the JsonValue parameter of {@code JsonIO.toString(JsonValue)} is {@code PRIMITIVE json} and
     * receives the JsonValue value directly.
     */
    private static ApiTypeRef toReturnType(Type type) {
        // void is not wrapped
        if (type == void.class || type == Void.class) {
            return ApiTypeRef.voidType();
        }
        // JsonValue/NbtValue and their subtypes (such as NbtValue.CompoundValue) as a return type become a
        // nullable symbol
        if (type instanceof Class<?> cls && JsonValue.class.isAssignableFrom(cls)) {
            return nullableSymbol(JsonValue.class);
        }
        if (type instanceof Class<?> cls && NbtValue.class.isAssignableFrom(cls)) {
            return nullableSymbol(NbtValue.class);
        }
        // Every other return type is wrapped into a nullable UNION. Java has no null annotation, so null
        // returns are allowed conservatively: on failure RegistryView.dataMapValue returns null for a
        // missing entry and JsonIO.read returns null for a missing file. For a non-null value the
        // UNION branch of marshalReturn picks the best matching branch by actual type, so normal
        // returns are unaffected.
        return nullable(toTypeRef(type));
    }

    /** Wraps a type into UNION(base, null); acceptsNull in marshalReturn then lets a null return through. */
    private static ApiTypeRef nullable(ApiTypeRef base) {
        return ApiTypeRef.union(List.of(base, ApiTypeRef.primitive("null")));
    }

    private static ApiTypeRef nullableSymbol(Class<?> type) {
        return nullable(symbolRef(type));
    }
}
