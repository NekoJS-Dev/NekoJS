package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * typed Builder 契约条目的派生器（ticket 15，AC7/AC9）：
 * 以 {@code registry_types} 扩展点产物里登记的 builder 类为<b>唯一契约输入</b>，
 * 经 {@link RegistryBuilderContract} 反射出成员目录，转成结构化 catalog 条目
 * （{@link RegistryBuilderSurfaceEntry}）交给 probe TS/Python 后端渲染——
 * runtime member（{@link BuilderSurface}）、definition fingerprint、TS/Python
 * declaration 与 contract/golden 全部同源，不手写第二份成员表。
 *
 * <p>未声明 builder 类的第三方类型不产生派生条目（其糖方法仍可用；声明面继续由
 * legacy 手写 manual declaration 观察，直到删除 gate 裁定）。
 */
public final class RegistryBuilderSurfaces {

    private RegistryBuilderSurfaces() {}

    /** 把 {@code registry_types} 产物派生成结构化契约条目（确定性排序：注册表键 → 类型名）。 */
    public static List<RegistryBuilderSurfaceEntry> derive(RegistryTypesPoint.RegistryTypes types) {
        List<RegistryBuilderSurfaceEntry> entries = new ArrayList<>();
        types.builderClasses().keySet().stream()
                .sorted(Comparator.comparing(key -> key.identifier().toString()))
                .forEach(registry -> types.builderClassesOf(registry).forEach((typeName, builderClass) -> {
                    String defaultTypeName = types.defaults().get(registry);
                    entries.add(entryOf(registry, typeName, builderClass,
                            typeName.equals(defaultTypeName) ? sugarOf(registry) : null));
                }));
        return List.copyOf(entries);
    }

    /** 登记进 type_docs（NekoRegistryPointsPlugin.registerTypeDocs 调用）。 */
    public static void register(TypeDocsRegister registry, RegistryTypesPoint.RegistryTypes types) {
        derive(types).forEach(registry::registerRegistryBuilderSurface);
    }

    private static RegistryBuilderSurfaceEntry entryOf(
            ResourceKey<? extends Registry<?>> registryKey, String typeName,
            Class<? extends RegistryObjectBuilder<?>> builderClass, String sugarName) {
        RegistryBuilderContract contract = RegistryBuilderContract.of(builderClass);
        List<RegistryBuilderSurfaceEntry.Member> members = new ArrayList<>();
        contract.members().forEach(member -> members.add(switch (member.kind()) {
            case WRITABLE_PROPERTY -> new RegistryBuilderSurfaceEntry.Member(
                    member.name(), RegistryBuilderSurfaceEntry.MemberKind.WRITABLE_PROPERTY,
                    member.tsType(), member.pyType());
            case READ_ONLY_PROPERTY -> new RegistryBuilderSurfaceEntry.Member(
                    member.name(), RegistryBuilderSurfaceEntry.MemberKind.READ_ONLY_PROPERTY,
                    member.tsType(), member.pyType());
            case METHOD -> new RegistryBuilderSurfaceEntry.Member(
                    member.name(), RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                    tsSignature(member.overloads()), pySignature(member.name(), member.overloads()));
        }));
        return new RegistryBuilderSurfaceEntry(
                builderClass.getSimpleName(),
                registryKey.identifier().toString(),
                typeName,
                sugarName,
                members,
                "Startup registry builder for '" + registryKey.identifier() + "' (type '" + typeName
                        + "'); derived from contract reflection, property writes and explicit setters hit the same setter.");
    }

    /** 糖方法名（与 RegistryEventJS 的 bySugar 派生同规则：path snake_case → lowerCamelCase）。 */
    private static String sugarOf(ResourceKey<? extends Registry<?>> key) {
        String[] parts = key.identifier().getPath().split("_");
        StringBuilder name = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return name.toString();
    }

    /** 方法成员的 TS 形状：重载并列为联合签名（当前内置类型无方法重载进面，防御性取首个）。 */
    private static String tsSignature(List<Method> overloads) {
        Method method = deterministicOverload(overloads);
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            if (method.isVarArgs() && i == params.length - 1) {
                sb.append("...").append(paramName(params[i], i)).append(tsParamShape(params[i]));
            } else {
                sb.append(paramName(params[i], i)).append(tsParamShape(params[i]));
            }
        }
        sb.append("): ").append(method.getReturnType() == void.class || method.getReturnType() == Void.class
                ? "void"
                : RegistryBuilderContract.tsTypeOf(method.getReturnType()));
        return sb.toString();
    }

    private static String tsParamShape(Parameter param) {
        Class<?> type = param.getType();
        if (java.util.function.Consumer.class.isAssignableFrom(type)) {
            return ": (b: any) => void";
        }
        if (type.isArray()) {
            return ": " + RegistryBuilderContract.tsTypeOf(type.getComponentType()) + "[]";
        }
        return ": " + RegistryBuilderContract.tsTypeOf(type);
    }

    /** 方法成员的 Python 形状（与 TS 同一 Method 输入派生；成员名保持 JS 面 verbatim）。 */
    private static String pySignature(String name, List<Method> overloads) {
        Method method = deterministicOverload(overloads);
        StringBuilder sb = new StringBuilder("def ").append(name).append("(self");
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            sb.append(", ");
            if (method.isVarArgs() && i == params.length - 1) {
                // Python varargs：*name: component（Callable/List 包装不适用于剩余参数）
                sb.append("*").append(paramName(params[i], i)).append(": ")
                        .append(RegistryBuilderContract.pyTypeOf(params[i].getType().getComponentType()));
            } else {
                sb.append(paramName(params[i], i)).append(pyParamShape(params[i]));
            }
        }
        sb.append(") -> ").append(method.getReturnType() == void.class || method.getReturnType() == Void.class
                ? "None"
                : RegistryBuilderContract.pyTypeOf(method.getReturnType()));
        return sb.append(": ...").toString();
    }

    private static String pyParamShape(Parameter param) {
        Class<?> type = param.getType();
        if (java.util.function.Consumer.class.isAssignableFrom(type)) {
            return ": Callable[[Any], None]";
        }
        if (type.isArray()) {
            return ": list[" + RegistryBuilderContract.pyTypeOf(type.getComponentType()) + "]";
        }
        return ": " + RegistryBuilderContract.pyTypeOf(type);
    }

    /** {@code getMethods()} 顺序不保证：重载取参数最多者（再按签名串稳定），golden 输出确定。 */
    private static Method deterministicOverload(List<Method> overloads) {
        return overloads.stream()
                .sorted(java.util.Comparator.comparing((Method m) -> -m.getParameterCount())
                        .thenComparing(m -> Arrays.toString(m.getParameterTypes())))
                .findFirst()
                .orElseThrow();
    }

    private static String paramName(Parameter param, int index) {
        return param.isNamePresent() && !param.getName().startsWith("arg")
                ? param.getName()
                : "arg" + index;
    }
}
