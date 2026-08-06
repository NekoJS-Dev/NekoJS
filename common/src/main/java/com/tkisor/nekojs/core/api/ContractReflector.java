package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.facade.RegistryView;
import com.tkisor.nekojs.api.facade.ModInfoValue;
import com.tkisor.nekojs.api.data.PerfTimerValue;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 facade 接口反射派生 {@link ApiSymbol}/{@link ApiSignature}，替代从 portable-core JSON
 * 的 symbols 字段读取。
 *
 * <p>Java 方法签名即契约：参数名从方法参数名反射（需 -parameters 编译），参数类型和返回类型
 * 通过 {@link #toTypeRef(Type)} 转换为 {@link ApiTypeRef}。
 *
 * <p>对于 Java 类型系统表达不了的 UNION/CALLBACK（如 {@code List<Object>} 表示可变参数联合），
 * 方法上可用 {@link Remap} 注解标记自定义类型——目前通过命名约定处理，后续可扩展专用注解。
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
     * </ul>
     *
     * @param facadeName 全局名（如 "Text"）
     * @param facadeType facade 接口类
     * @return 符号列表
     */
    public static List<ApiSymbol> extractSymbols(String facadeName, Class<?> facadeType) {
        List<ApiSymbol> symbols = new ArrayList<>();

        // global:<facadeName> —— facade 对象本身
        symbols.add(new ApiSymbol(
                ApiSymbolId.parse("global:" + facadeName),
                List.of(new ApiSignature(
                        List.of(),
                        ApiTypeRef.primitive("object"),
                        false,
                        List.of()))));

        // member:<facadeName>.<method> —— 按 methodName 分组，重载合并
        Map<String, List<ApiSignature>> signaturesByName = new java.util.LinkedHashMap<>();
        for (Method method : facadeType.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) continue;
            if (method.getName().startsWith("neko$")) continue;

            String memberId = "member:" + facadeName + "." + method.getName();
            signaturesByName
                    .computeIfAbsent(memberId, k -> new ArrayList<>())
                    .add(methodToSignature(method));
        }

        for (var entry : signaturesByName.entrySet()) {
            symbols.add(new ApiSymbol(ApiSymbolId.parse(entry.getKey()), entry.getValue()));
        }
        return symbols;
    }

    private static ApiSignature methodToSignature(Method method) {
        List<ApiParameter> params = new ArrayList<>();
        java.lang.reflect.Parameter[] javaParams = method.getParameters();
        Type[] genericTypes = method.getGenericParameterTypes();

        for (int i = 0; i < javaParams.length; i++) {
            String name = javaParams[i].isNamePresent()
                    ? javaParams[i].getName()
                    : "arg" + i;
            ApiTypeRef type = toTypeRef(genericTypes[i]);
            boolean varargs = i == javaParams.length - 1 && method.isVarArgs();
            params.add(new ApiParameter(name, type, false, varargs));
        }

        ApiTypeRef returnType = toTypeRef(method.getGenericReturnType());
        return new ApiSignature(params, returnType, false, List.of());
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
     *   <li>{@code TextValue}/{@code NekoId}/{@code RegistryView}/{@code ModInfoValue}/{@code PerfTimerValue} → SYMBOL("type:<SimpleName>")
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
        if (type == TextValue.class) return symbolRef(TextValue.class);
        if (type == NekoId.class) return symbolRef(NekoId.class);
        if (type == RegistryView.class) return symbolRef(RegistryView.class);
        if (type == ModInfoValue.class) return symbolRef(ModInfoValue.class);
        if (type == PerfTimerValue.class) return symbolRef(PerfTimerValue.class);

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
        return ApiTypeRef.symbol(ApiSymbolId.parse("type:" + type.getSimpleName()));
    }
}
