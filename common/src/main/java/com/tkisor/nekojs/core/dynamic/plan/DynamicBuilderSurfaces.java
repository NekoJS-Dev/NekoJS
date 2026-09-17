package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 动态定义 builder 契约条目的派生器（ticket 16，AC9）：以三个冻结类型的 builder 类为
 * <b>唯一契约输入</b>，经 {@link DynamicBuilderContract} 反射出成员目录，转成结构化
 * catalog 条目（{@link RegistryBuilderSurfaceEntry}，票 15 同款条目形状）交给
 * probe TS/Python 后端渲染——runtime member（{@link DynamicBuilderSurface}）、
 * definition fingerprint、TS/Python declaration 全部同源，不手写第二份成员表。
 *
 * <p>与票 15 {@code RegistryBuilderSurfaces} 的关系：共享<b>条目形状与渲染器</b>（同一
 * 事实源的呈现约定），但派生输入是动态 builder 自己的契约——不进入启动期
 * {@code registry_types} 清单，不与启动 drain 语义耦合（工单协调裁定「共享类型事实、
 * 独立 Builder 路径」）。
 */
public final class DynamicBuilderSurfaces {

    private DynamicBuilderSurfaces() {}

    /** 三个冻结类型的结构化契约条目（builder 名字典序确定）。 */
    public static List<RegistryBuilderSurfaceEntry> derive() {
        List<RegistryBuilderSurfaceEntry> entries = new ArrayList<>();
        entries.add(entryOf(DynamicDefinitionType.ITEM, DynamicItemBuilder.class));
        entries.add(entryOf(DynamicDefinitionType.SOUND_EVENT, DynamicSoundEventBuilder.class));
        entries.add(entryOf(DynamicDefinitionType.MOB_EFFECT, DynamicMobEffectBuilder.class));
        entries.sort(Comparator.comparing(RegistryBuilderSurfaceEntry::builderName));
        return List.copyOf(entries);
    }

    private static RegistryBuilderSurfaceEntry entryOf(
            DynamicDefinitionType type, Class<? extends DynamicDefinitionBuilder> builderClass) {
        DynamicBuilderContract contract = DynamicBuilderContract.of(builderClass);
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
                    tsSignature(builderClass, member.overloads()),
                    pySignature(builderClass, member.name(), member.overloads()));
        }));
        return new RegistryBuilderSurfaceEntry(
                builderClass.getSimpleName(),
                type.registryKey(),
                "dynamic",
                type.apiName(),
                members,
                "Dynamic registry candidate builder for '" + type.registryKey()
                        + "' (runtime only; activation gated by the transaction/sync gate);"
                        + " property writes and explicit setters hit the same setter.");
    }

    /**
     * 方法成员的 TS 形状：声明面每名冻结一个确定性签名（参数最多者，再按签名串稳定）；
     * 同名重载（如 {@code fireResistant()} / {@code fireResistant(value)}）在脚本侧由
     * 实参个数解析。链式 fluent setter 的返回类型渲染为 builder 名（声明面可读）。
     */
    private static String tsSignature(Class<?> builderClass, List<Method> overloads) {
        Method method = deterministicOverload(overloads);
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(paramName(params[i], i)).append(": ").append(DynamicBuilderContract.tsTypeOf(params[i].getType()));
        }
        sb.append("): ").append(returnShape(builderClass, method, "void", false));
        return sb.toString();
    }

    /** 方法成员的 Python 形状（与 TS 同一 Method 输入派生；成员名保持 JS 面 verbatim）。 */
    private static String pySignature(Class<?> builderClass, String name, List<Method> overloads) {
        Method method = deterministicOverload(overloads);
        StringBuilder sb = new StringBuilder("def ").append(name).append("(self");
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            // 参数名回落序号必须用真实下标（与 TS 侧同款）：恒传 0 会产出重复的 arg0
            sb.append(", ").append(paramName(params[i], i)).append(": ")
                    .append(DynamicBuilderContract.pyTypeOf(params[i].getType()));
        }
        sb.append(") -> ").append(returnShape(builderClass, method, "None", true)).append(": ...");
        return sb.toString();
    }

    /** 返回类型形状：链式返回 builder → builder 名；void → void/None；其余 → 类型映射。 */
    private static String returnShape(Class<?> builderClass, Method method, String voidShape, boolean python) {
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class || returnType == Void.class) {
            return voidShape;
        }
        if (builderClass.isAssignableFrom(returnType)) {
            return builderClass.getSimpleName();
        }
        return python ? DynamicBuilderContract.pyTypeOf(returnType) : DynamicBuilderContract.tsTypeOf(returnType);
    }

    private static Method deterministicOverload(List<Method> overloads) {
        return overloads.stream()
                .sorted(Comparator.comparingInt((Method m) -> -m.getParameterCount())
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
