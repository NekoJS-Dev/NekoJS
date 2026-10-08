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

    /** Associates the actual STARTUP proxy methods with contract-derived builders in an isolated TS namespace. */
    public static void register(TypeDocsRegister registry, RegistryInfosPoint.RegistryInfos infos,
                                RegistryTypesPoint.RegistryTypes types) {
        List<RegistryBuilderSurfaceEntry> entries = derive(types);
        var payload = StartupRegistryEventSurface.derive(infos, types, entries);
        entries.stream().map(entry -> new RegistryBuilderSurfaceEntry(entry.builderName(), entry.registryKey(),
                entry.typeName(), entry.sugarName(), entry.members(), entry.description(), payload))
                .forEach(registry::registerRegistryBuilderSurface);
    }

    private static RegistryBuilderSurfaceEntry entryOf(
            ResourceKey<? extends Registry<?>> registryKey, String typeName,
            Class<? extends RegistryObjectBuilder<?>> builderClass, String sugarName) {
        RegistryBuilderContract contract = RegistryBuilderContract.of(builderClass);
        List<RegistryBuilderSurfaceEntry.Member> members = new ArrayList<>();
        for (RegistryBuilderContract.Member member : contract.members()) {
            switch (member.kind()) {
                case WRITABLE_PROPERTY -> members.add(new RegistryBuilderSurfaceEntry.Member(
                        member.name(), RegistryBuilderSurfaceEntry.MemberKind.WRITABLE_PROPERTY,
                        member.tsType(), member.pyType()));
                case READ_ONLY_PROPERTY -> members.add(new RegistryBuilderSurfaceEntry.Member(
                        member.name(), RegistryBuilderSurfaceEntry.MemberKind.READ_ONLY_PROPERTY,
                        member.tsType(), member.pyType()));
                case METHOD -> member.overloads().stream()
                        .sorted(Comparator.comparingInt(Method::getParameterCount)
                                .thenComparing(method -> Arrays.toString(method.getParameterTypes())))
                        .map(method -> new RegistryBuilderSurfaceEntry.Member(
                                member.name(), RegistryBuilderSurfaceEntry.MemberKind.METHOD,
                                tsSignature(method), pySignature(method)))
                        .distinct()
                        .forEach(members::add);
            }
        }
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

    /** Renders the TypeScript signature of one reflected overload without changing its arity. */
    private static String tsSignature(Method method) {
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Parameter[] parameters = method.getParameters();
        for (int parameterIndex = 0; parameterIndex < parameters.length; parameterIndex++) {
            if (parameterIndex > 0) {
                sb.append(", ");
            }
            if (method.isVarArgs() && parameterIndex == parameters.length - 1) {
                sb.append("...").append(paramName(parameters[parameterIndex], parameterIndex))
                        .append(tsParamShape(parameters[parameterIndex]));
            } else {
                sb.append(paramName(parameters[parameterIndex], parameterIndex))
                        .append(tsParamShape(parameters[parameterIndex]));
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

    /** Renders the Python signature from the same reflected overload as TypeScript. */
    private static String pySignature(Method method) {
        StringBuilder sb = new StringBuilder("def ").append(method.getName()).append("(self");
        Parameter[] parameters = method.getParameters();
        for (int parameterIndex = 0; parameterIndex < parameters.length; parameterIndex++) {
            sb.append(", ");
            if (method.isVarArgs() && parameterIndex == parameters.length - 1) {
                sb.append("*").append(paramName(parameters[parameterIndex], parameterIndex)).append(": ")
                        .append(RegistryBuilderContract.pyTypeOf(parameters[parameterIndex].getType().getComponentType()));
            } else {
                sb.append(paramName(parameters[parameterIndex], parameterIndex))
                        .append(pyParamShape(parameters[parameterIndex]));
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

    private static String paramName(Parameter param, int index) {
        return param.isNamePresent() && !param.getName().startsWith("arg")
                ? param.getName()
                : "arg" + index;
    }
}
