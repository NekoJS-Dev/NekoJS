package com.tkisor.nekojs.core.dynamic.plan;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态定义 builder 的脚本面（ticket 16，AC4）：把 {@link DynamicDefinitionBuilder} 包成
 * {@link ProxyObject}，属性写入与显式 setter <b>分发到同一个 fluent setter</b>
 * {@link Method}（{@link DynamicBuilderContract.Member#setter()}）。
 *
 * <p>与票 15 {@code BuilderSurface} 同一 putMember seam 语义（宿主对象 property 写不会
 * 落 setter，ProxyObject 才是可靠转发点——spec 08 预授权），但服务于<b>独立的动态
 * Builder 路径</b>：不实现 {@code RegistryObjectBuilder}、不进入启动期 drain，包出的
 * builder 只进入 inert 候选计划。{@code b.maxStackSize = 16} 与 {@code b.maxStackSize(16)}
 * 因此共享同一条校验、规范化与 definition fingerprint 路径；Graal 天然 Bean 映射的
 * characterization 结论由票 15 的 parity fixture 固定，本类的行为由本票的
 * {@code DynamicBuilderSurfaceParityTest}（真实 GraalJS）固定。
 */
public final class DynamicBuilderSurface implements ProxyObject {

    private final DynamicDefinitionBuilder builder;
    private final DynamicBuilderContract contract;

    private DynamicBuilderSurface(DynamicDefinitionBuilder builder) {
        this.builder = builder;
        this.contract = DynamicBuilderContract.of(builderClass(builder));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends DynamicDefinitionBuilder> builderClass(DynamicDefinitionBuilder builder) {
        return (Class<? extends DynamicDefinitionBuilder>) builder.getClass();
    }

    /** 包一个动态定义 builder。 */
    public static DynamicBuilderSurface of(DynamicDefinitionBuilder builder) {
        return new DynamicBuilderSurface(builder);
    }

    /** 若 value 是被本面包裹的 builder，解出裸对象；否则原样返回。 */
    public static Object unwrap(Object value) {
        return value instanceof DynamicBuilderSurface surface ? surface.builder : value;
    }

    /** 被包裹的裸 builder（计划/Adapter 侧用；脚本面不直接接触）。 */
    public DynamicDefinitionBuilder target() {
        return builder;
    }

    // ---- ProxyObject：成员目录 ----

    @Override
    public Object getMember(String name) {
        DynamicBuilderContract.Member member = contract.member(name);
        if (member == null) {
            throw new IllegalArgumentException(
                    builderTypeName() + " has no member '" + name + "'; known: " + contract.memberNames());
        }
        return switch (member.kind()) {
            case WRITABLE_PROPERTY, READ_ONLY_PROPERTY -> contract.readMember(builder, member);
            case METHOD -> new MethodMember(member);
        };
    }

    @Override
    public boolean hasMember(String name) {
        return contract.member(name) != null;
    }

    @Override
    public Object getMemberKeys() {
        return contract.memberNames().toArray();
    }

    /** 属性写入：转发到与显式 setter 调用相同的 fluent setter 方法。 */
    @Override
    public void putMember(String name, Value value) {
        DynamicBuilderContract.Member member = contract.member(name);
        if (member == null) {
            throw new IllegalArgumentException(
                    builderTypeName() + " has no member '" + name + "'; known: " + contract.memberNames());
        }
        if (member.kind() != DynamicBuilderContract.MemberKind.WRITABLE_PROPERTY) {
            throw new IllegalArgumentException("'" + name + "' on " + builderTypeName() + " is "
                    + (member.kind() == DynamicBuilderContract.MemberKind.READ_ONLY_PROPERTY
                            ? "read-only (derived)"
                            : "a method, not a writable property")
                    + "; writable: " + contract.writableNames());
        }
        contract.invoke(builder, member.setter(), coerce(value, member.setter().getParameterTypes()[0],
                member.name()));
    }

    @Override
    public boolean removeMember(String name) {
        return false;
    }

    private String builderTypeName() {
        return builder.getClass().getSimpleName();
    }

    // ---- 方法成员（含显式 fluent setter 形态与 0 参便利方法） ----

    private final class MethodMember implements ProxyExecutable {

        private final DynamicBuilderContract.Member member;

        MethodMember(DynamicBuilderContract.Member member) {
            this.member = member;
        }

        @Override
        public Object execute(Value... args) {
            Method method = resolveOverload(args);
            if (method == null) {
                List<String> shapes = new ArrayList<>();
                member.overloads().forEach(overload -> shapes.add(signature(overload)));
                throw new IllegalArgumentException(member.name() + " on " + builderTypeName()
                        + " expects " + shapes + " but got " + args.length + " argument(s)");
            }
            Object[] javaArgs = new Object[method.getParameterCount()];
            for (int i = 0; i < method.getParameterCount(); i++) {
                javaArgs[i] = coerce(args[i], method.getParameterTypes()[i], member.name() + " arg" + i);
            }
            Object result = contract.invoke(builder, method, javaArgs);
            return result instanceof DynamicDefinitionBuilder chained ? DynamicBuilderSurface.of(chained)
                    : result;
        }

        private Method resolveOverload(Value[] args) {
            for (Method overload : member.overloads()) {
                int paramCount = overload.getParameterCount();
                if (paramCount == args.length) {
                    return overload;
                }
            }
            return null;
        }

        private String signature(Method method) {
            StringBuilder sb = new StringBuilder(member.name()).append('(');
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(params[i].getSimpleName());
            }
            return sb.append(')').toString();
        }
    }

    // ---- 值装配 ----

    /** JS 值 → setter/方法参数。错误信息带成员名与期望类型（错误面由契约派生）。 */
    static Object coerce(Value value, Class<?> target, String what) {
        if (value.isNull()) {
            if (target.isPrimitive()) {
                throw new IllegalArgumentException(what + ": null is not a valid " + target.getSimpleName());
            }
            return null;
        }
        if (target == int.class || target == Integer.class) {
            return requireNumber(value, what).asInt();
        }
        if (target == long.class || target == Long.class) {
            return requireNumber(value, what).asLong();
        }
        if (target == float.class || target == Float.class) {
            return requireNumber(value, what).asFloat();
        }
        if (target == double.class || target == Double.class) {
            return requireNumber(value, what).asDouble();
        }
        if (target == boolean.class || target == Boolean.class) {
            if (!value.isBoolean()) {
                throw new IllegalArgumentException(what + " expects a boolean");
            }
            return value.asBoolean();
        }
        if (target == String.class || target == CharSequence.class) {
            if (!value.isString()) {
                throw new IllegalArgumentException(what + " expects a string");
            }
            return value.asString();
        }
        if (target == Object.class) {
            return value.isString() ? value.asString() : value.as(Object.class);
        }
        Object host = value.isHostObject() ? value.asHostObject() : null;
        if (host != null && target.isInstance(host)) {
            return host;
        }
        try {
            return value.as(target);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    what + " expects " + target.getSimpleName() + " but the value cannot be converted", e);
        }
    }

    private static Value requireNumber(Value value, String what) {
        if (!value.isNumber()) {
            throw new IllegalArgumentException(what + " expects a number");
        }
        return value;
    }
}
