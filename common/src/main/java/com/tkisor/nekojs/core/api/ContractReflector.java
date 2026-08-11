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
 * 从 Java facade 接口 / 数据类型反射派生 {@link ApiSymbol}/{@link ApiSignature}。
 *
 * <p>Java 方法签名即契约的唯一真相源（替代手写 JSON）：
 * <ul>
 *   <li>{@link #extractSymbols(String, Class, boolean)} — 反射 facade 接口，产出
 *       {@code global:<facadeName>} + {@code member:<facadeName>.<method>}，并在首个参数为
 *       数据类型时额外产出 receiver 双产出 {@code member:<DataType>.<method>}。
 *   <li>{@link #reflectDataType(String, Class)} — 反射数据类型本身（record 访问器或
 *       实例方法），产出 {@code member:<contractName>.<member>}，用于 receiver 双产出
 *       覆盖不到的数据类型方法（如 {@code RegistryView.exists}、{@code ModInfo.id}）。
 *   <li>{@link #reflectEventRegistrationSymbols()} — 反射 common 侧的
 *       {@link ScriptEventRegistrationEvent}（Graal/ScriptType 参数全部映射为 object）。
 * </ul>
 *
 * <p>参数名从方法参数名反射（需 {@code -parameters} 编译），参数类型和返回类型通过
 * {@link #toTypeRef(Type)} 转换为 {@link ApiTypeRef}。哪些类型是 NekoJS 数据类型（可作
 * receiver）以及类名→契约名映射，统一由 common-api 侧的 {@link ContractReceiver} 注解
 * 声明（见 {@link #isReceiverType} / {@link #contractNameOf}），不再硬编码于本类。
 */
public final class ContractReflector {

    private ContractReflector() {
    }

    /**
     * 从 facade 接口反射出符号列表。
     *
     * <p>每个 public 方法产出一个 {@link ApiSymbol}：
     * <ul>
     *   <li>全局符号（无参数 receiver）：{@code global:<facadeName>}
     *   <li>成员方法：{@code member:<facadeName>.<methodName>}，同名重载合并为一个符号（多个 signature）
     *   <li>首个参数为数据类型时，额外产出 receiver 符号 {@code member:<DataType>.<methodName>}
     *       （receiver 不算脚本参数，参数偏移 1）
     * </ul>
     *
     * @param facadeName 全局名（如 "Text"）
     * @param facadeType facade 接口类
     * @return 符号列表
     */
    public static List<ApiSymbol> extractSymbols(String facadeName, Class<?> facadeType) {
        return extractSymbols(facadeName, facadeType, true);
    }

    /**
     * @param includeGlobal true 产出 global:<facadeName> 符号（facade 需要）；
     *                      false 不产出（数据类型如 ModInfoValue 不是全局对象）
     */
    public static List<ApiSymbol> extractSymbols(String facadeName, Class<?> facadeType, boolean includeGlobal) {
        List<ApiSymbol> symbols = new ArrayList<>();

        // global:<facadeName> —— facade 对象本身（数据类型不需要）
        if (includeGlobal) {
            symbols.add(new ApiSymbol(
                ApiSymbolId.parse("global:" + facadeName),
                List.of(new ApiSignature(
                        List.of(),
                        ApiTypeRef.primitive("object"),
                        false))));
        }

        // 按符号 ID 分组收集签名（重载合并 + 静态/receiver 双产出）
        Map<String, List<ApiSignature>> signaturesByName = new java.util.LinkedHashMap<>();
        for (Method method : facadeType.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            if (method.getName().startsWith("neko$")) continue;
            String jsName = jsNameOf(method);

            // 静态符号：member:<facadeName>.<method>（全部参数作为脚本参数）
            String staticId = "member:" + facadeName + "." + jsName;
            signaturesByName
                    .computeIfAbsent(staticId, k -> new ArrayList<>())
                    .add(methodToSignature(method, 0));

            // receiver 符号：若第一个参数是数据类型，额外产出 member:<DataType>.<method>
            // （脚本侧 dataType.method()，receiver 不算参数）
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
     * 反射数据类型本身（非 facade），产出 {@code member:<contractName>.<member>}。
     *
     * <p>覆盖 receiver 双产出遗漏的场景：数据类型自身声明的方法（如
     * {@code RegistryView.exists}、{@code TextValue.isEmpty}、{@code PerfTimerValue.mark}）
     * 没有对应 facade 方法以它为首参，故 receiver 双产出不触发。
     *
     * <p>处理两类数据类型：
     * <ul>
     *   <li><b>record</b>（如 {@link NbtEntry}、{@link ModInfoValue}）：每个 record component
     *       产出一个零参符号（访问器）。
     *   <li><b>普通类/接口</b>（如 {@link RegistryView}、{@link PerfTimerValue}、{@link TextValue}）：
     *       扫描声明的 public 实例方法（非 static、非 synthetic、非 {@code neko$} 前缀）。
     * </ul>
     *
     * <p>同名重载合并为一个符号（多个 signature）。
     *
     * @param contractName 契约名（数据类型在 JS 侧暴露的名字，如 "ModInfo"）
     * @param dataType 数据类型类
     * @return 符号列表
     */
    public static List<ApiSymbol> reflectDataType(String contractName, Class<?> dataType) {
        Map<String, List<ApiSignature>> signaturesByName = new java.util.LinkedHashMap<>();

        // record：每个 component 产出一个零参访问器符号
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

        // 普通类/接口：扫描声明的 public 实例方法
        for (Method method : dataType.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (!Modifier.isPublic(method.getModifiers())) continue;
            if (method.getName().startsWith("neko$")) continue;
            // 跳过 Object 继承的方法（toString/hashCode/equals）
            if (isObjectMethod(method)) continue;

            String id = "member:" + contractName + "." + jsNameOf(method);
            signaturesByName
                    .computeIfAbsent(id, k -> new ArrayList<>())
                    .add(methodToSignature(method, 0));
        }
        return toSymbols(signaturesByName);
    }

    /**
     * 反射 {@link ScriptEventRegistrationEvent}（common 侧），产出
     * {@code member:ScriptEventRegistrationEvent.<method>}。
     *
     * <p>Graal {@code Value}、{@link ScriptType}、{@code Object} 参数全部映射为
     * {@code PRIMITIVE object}（与原 JSON 契约一致——这些是运行时透明类型）。
     * {@code targetType()} 零参，{@code register} 4 个重载合并。
     */
    public static List<ApiSymbol> reflectEventRegistrationSymbols() {
        return reflectDataType("ScriptEventRegistrationEvent", ScriptEventRegistrationEvent.class);
    }

    /**
     * 判断一个类是否是 NekoJS 数据类型（可作 receiver）：由 common-api 的
     * {@link ContractReceiver} 注解声明，避免在此硬编码类清单。facade 方法第一个参数
     * 若是这些类型，则额外产出该类型的 receiver 成员符号。
     */
    private static boolean isReceiverType(Class<?> cls) {
        return cls.isAnnotationPresent(ContractReceiver.class);
    }

    /**
     * 取数据类型的契约名：优先读 {@link ContractReceiver#value()}，空则用类 simpleName。
     * 与 {@link #isReceiverType} 共用同一注解——单一真相源。
     */
    private static String contractNameOf(Class<?> cls) {
        ContractReceiver receiver = cls.getAnnotation(ContractReceiver.class);
        if (receiver != null && !receiver.value().isEmpty()) {
            return receiver.value();
        }
        return cls.getSimpleName();
    }

    /**
     * 取方法的 JS 可见名：优先 {@link Remap} 注解（Java 关键字冲突等场景，如
     * {@code byteValue}→{@code byte}），否则用方法名。与 Graal host access 的
     * {@code MemberRemapper} 共用同一注解——单一真相源。
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
            // varargs：Java 原生 varargs（...）或末位 List<Object>（约定可变参数联合，
            // 如 Text.append/Text.translatable 的 values 参数）
            boolean varargs = i == javaParams.length - 1
                    && (method.isVarArgs() || isVarargsList(genericTypes[i]));
            params.add(new ApiParameter(name, type, false, varargs));
        }

        ApiTypeRef returnType = toReturnType(method.getGenericReturnType());
        return new ApiSignature(params, returnType, false);
    }

    /** 末位 {@code List<Object>} 表示可变参数联合（string|number|boolean|TextValue）。 */
    private static boolean isVarargsList(Type type) {
        return type instanceof ParameterizedType pt && pt.getRawType() == List.class
                && pt.getActualTypeArguments()[0] == Object.class;
    }

    /**
     * Java Type → ApiTypeRef 转换器。
     *
     * <p>映射规则：
     * <ul>
     *   <li>{@code String} → PRIMITIVE("string")
     *   <li>{@code boolean}/{@code Boolean} → PRIMITIVE("boolean")
     *   <li>数值类型 → PRIMITIVE("number")
     *   <li>{@code void}/{@code Void} → VOID
     *   <li>{@code Object} → PRIMITIVE("object")
     *   <li>{@code JsonValue} → PRIMITIVE("json")
     *   <li>{@code NbtValue} → PRIMITIVE("nbt")
     *   <li>{@code TextValue}/{@code NekoId}/{@code RegistryView}/{@code ModInfoValue}/{@code PerfTimerValue} → SYMBOL("type:<ContractName>")
     *   <li>{@code List<Object>}（可变参数联合）→ UNION(string, number, boolean, type:TextValue)（用于 Text 参数）
     *   <li>数组 → ARRAY(element)
     *   <li>其他 → PRIMITIVE("object")（安全回退）
     * </ul>
     */
    public static ApiTypeRef toTypeRef(Type type) {
        if (type == null) return ApiTypeRef.primitive("object");

        // 基础类型
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

        // NekoJS 可移植数据类型
        if (type == JsonValue.class) return ApiTypeRef.primitive("json");
        if (type == NbtValue.class) return ApiTypeRef.primitive("nbt");
        // 其他 receiver 数据类型（TextValue/NekoId/RegistryView/NbtEntry/ModInfoValue/PerfTimerValue）
        // → SYMBOL 引用。isReceiverType 是单一清单，toTypeRef/extractSymbols 共用。
        if (type instanceof Class<?> cls && isReceiverType(cls)) {
            return symbolRef(cls);
        }

        // Object → 可变参数联合（Text 参数场景）
        if (type == Object.class) {
            return ApiTypeRef.primitive("object");
        }

        // List<Object> → 可变参数联合
        if (type instanceof ParameterizedType pt && pt.getRawType() == List.class) {
            Type elem = pt.getActualTypeArguments()[0];
            if (elem == Object.class) {
                // List<Object> 表示可变参数（string|number|boolean|TextValue）
                return ApiTypeRef.union(List.of(
                        ApiTypeRef.primitive("string"),
                        ApiTypeRef.primitive("number"),
                        ApiTypeRef.primitive("boolean"),
                        symbolRef(TextValue.class)));
            }
            return ApiTypeRef.array(toTypeRef(elem));
        }

        // 数组
        if (type instanceof Class<?> cls && cls.isArray()) {
            return ApiTypeRef.array(toTypeRef(cls.componentType()));
        }

        // List<String> 等参数化集合 → 数组
        if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> rawCls) {
            if (List.class.isAssignableFrom(rawCls) && pt.getActualTypeArguments().length > 0) {
                return ApiTypeRef.array(toTypeRef(pt.getActualTypeArguments()[0]));
            }
        }

        // 安全回退
        return ApiTypeRef.primitive("object");
    }

    private static ApiTypeRef symbolRef(Class<?> type) {
        return ApiTypeRef.symbol(ApiSymbolId.parse("type:" + contractNameOf(type)));
    }

    /**
     * 返回类型专用映射。与 {@link #toTypeRef} 的差异：
     * <ul>
     *   <li>{@link JsonValue}/{@link NbtValue} 作为返回类型映射为 {@code UNION(SYMBOL, null)}
     *       （被 ApiFacadeProxy 包裹成带成员方法的 JS 对象；nullable 因 Java 无 null 标注，
     *       保守允许 null 返回——失败场景如 JsonIO.read 缺文件返回 null）。
     *   <li>其他数据类型（TextValue/NekoId/RegistryView/ModInfoValue/PerfTimerValue）已由
     *       {@link #toTypeRef} 映射为 SYMBOL，这里包成 nullable UNION 保持一致。
     * </ul>
     * 作为参数类型时全部是 PRIMITIVE（json/nbt 值透传，或 symbol 引用）。
     *
     * <p>例：{@code JsonIO.parse} 返回 {@code JsonValue} → {@code UNION(type:JsonValue, null)}
     * （JS 侧可继续调 {@code .toString()}）；而 {@code JsonIO.toString(JsonValue)} 的参数是
     * {@code PRIMITIVE json}（直接接收 JsonValue 值）。
     */
    private static ApiTypeRef toReturnType(Type type) {
        // void 不包装
        if (type == void.class || type == Void.class) {
            return ApiTypeRef.voidType();
        }
        // JsonValue/NbtValue 及其子类型（如 NbtValue.CompoundValue）作为返回类型 → nullable symbol
        if (type instanceof Class<?> cls && JsonValue.class.isAssignableFrom(cls)) {
            return nullableSymbol(JsonValue.class);
        }
        if (type instanceof Class<?> cls && NbtValue.class.isAssignableFrom(cls)) {
            return nullableSymbol(NbtValue.class);
        }
        // 其他返回类型统一包成 nullable UNION：Java 无 null 标注，保守允许 null 返回
        // （失败场景如 RegistryView.dataMapValue 缺条目返回 null、JsonIO.read 缺文件返回 null）。
        // 非 null 值由 marshalReturn 的 UNION 分支按实际类型选中最优 branch，不影响正常返回。
        return nullable(toTypeRef(type));
    }

    /** 包成 UNION(base, null)，使 marshalReturn 的 acceptsNull 放行 null 返回。 */
    private static ApiTypeRef nullable(ApiTypeRef base) {
        return ApiTypeRef.union(List.of(base, ApiTypeRef.primitive("null")));
    }

    private static ApiTypeRef nullableSymbol(Class<?> type) {
        return nullable(symbolRef(type));
    }
}
