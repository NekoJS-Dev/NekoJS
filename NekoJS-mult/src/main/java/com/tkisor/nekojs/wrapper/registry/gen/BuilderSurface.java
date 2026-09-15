package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * typed Builder 的脚本面（ticket 15，AC3）：把 {@link RegistryObjectBuilder} 包成
 * {@link ProxyObject}，属性写入与显式 setter <b>分发到同一个 Java setter</b>。
 *
 * <p>不依赖 Graal 对宿主对象的天然 Bean 映射——实测（runtime contract fixture
 * {@code BuilderSetterPropertyParityTest} 固定）宿主对象的属性写入不会落到 setter，
 * ProxyObject 的 putMember seam 才是可靠转发点（spec 08 预授权的回退路径）。
 * 两种写入都经 {@link RegistryBuilderContract.Member#setter()} 这一个
 * {@link Method}，进入同一校验、规范化、definition fingerprint 与注册收集路径；
 * {@code final id}、只读成员与未开放 experimental 成员是例外（读面直接暴露）。
 *
 * <p>复合配置回调（{@code food(cb)} 等 Consumer 参数）收到的子 builder 同样包本面，
 * 不让裸宿主 builder 泄漏回脚本（消除 public-field 旁路）。
 */
public final class BuilderSurface implements ProxyObject {

    private final Object builder;
    private final RegistryBuilderContract contract;

    private BuilderSurface(Object builder) {
        this.builder = builder;
        this.contract = RegistryBuilderContract.of(builderClass(builder));
    }

    /** 包一个 builder（builder 类型须为 {@link RegistryObjectBuilder} 子类）。 */
    public static BuilderSurface of(Object builder) {
        return new BuilderSurface(builder);
    }

    /** 若 value 是被本面包裹的 builder，解出裸对象；否则原样返回（参数解包用）。 */
    public static Object unwrap(Object value) {
        return value instanceof BuilderSurface surface ? surface.builder : value;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RegistryObjectBuilder<?>> builderClass(Object builder) {
        return (Class<? extends RegistryObjectBuilder<?>>) builder.getClass();
    }

    /** 被包裹的裸 builder（Java 侧收集/注册用；脚本面不直接接触）。 */
    public Object target() {
        return builder;
    }

    // ---- ProxyObject：成员目录 ----

    @Override
    public Object getMember(String name) {
        RegistryBuilderContract.Member member = contract.member(name);
        if (member == null) {
            throw new IllegalArgumentException(
                    builderTypeName() + " has no member '" + name + "'; known: " + contract.memberNames());
        }
        return switch (member.kind()) {
            case WRITABLE_PROPERTY, READ_ONLY_PROPERTY -> readProperty(member);
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

    /** 属性写入：转发到与显式 setter 相同的 setter 方法。 */
    @Override
    public void putMember(String name, Value value) {
        RegistryBuilderContract.Member member = contract.member(name);
        if (member == null) {
            throw new IllegalArgumentException(
                    builderTypeName() + " has no member '" + name + "'; known: " + contract.memberNames());
        }
        if (member.kind() != RegistryBuilderContract.MemberKind.WRITABLE_PROPERTY) {
            throw new IllegalArgumentException("'" + name + "' on " + builderTypeName() + " is "
                    + (member.kind() == RegistryBuilderContract.MemberKind.READ_ONLY_PROPERTY
                            ? "read-only (final identity or derived)"
                            : "a method, not a writable property")
                    + "; writable: " + writableNames());
        }
        invokeSetter(member, value);
    }

    @Override
    public boolean removeMember(String name) {
        return false;
    }

    // ---- 属性读写 ----

    private Object readProperty(RegistryBuilderContract.Member member) {
        // getter 或 final identity 字段（契约的 readMember 统一承载，异常带成员名）
        return wrapResult(contract.readMember(builder, member));
    }

    private void invokeSetter(RegistryBuilderContract.Member member, Value value) {
        try {
            member.setter().invoke(builder, coerce(value, member.setter().getParameterTypes()[0], member.name()));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot write '" + member.name() + "' of " + builderTypeName(), e);
        } catch (InvocationTargetException e) {
            throw asInvocationError(member.name(), e);
        }
    }

    private List<String> writableNames() {
        List<String> names = new ArrayList<>();
        contract.members().forEach(member -> {
            if (member.kind() == RegistryBuilderContract.MemberKind.WRITABLE_PROPERTY) {
                names.add(member.name());
            }
        });
        return names;
    }

    private String builderTypeName() {
        return builder.getClass().getSimpleName();
    }

    // ---- 方法成员（含显式 setter 形态） ----

    private final class MethodMember implements ProxyExecutable {

        private final RegistryBuilderContract.Member member;

        MethodMember(RegistryBuilderContract.Member member) {
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
            Object[] javaArgs = bindArguments(method, args);
            try {
                return wrapResult(method.invoke(builder, javaArgs));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("cannot invoke '" + member.name() + "' of " + builderTypeName(), e);
            } catch (InvocationTargetException e) {
                throw asInvocationError(member.name(), e);
            }
        }

        private Method resolveOverload(Value[] args) {
            Method candidate = null;
            for (Method overload : member.overloads()) {
                int paramCount = overload.getParameterCount();
                if (overload.isVarArgs()) {
                    if (args.length >= paramCount - 1) {
                        candidate = overload;
                        break;
                    }
                } else if (paramCount == args.length) {
                    candidate = overload;
                    break;
                }
            }
            return candidate;
        }

        private Object[] bindArguments(Method method, Value[] args) {
            Class<?>[] params = method.getParameterTypes();
            Object[] bound = new Object[params.length];
            if (method.isVarArgs()) {
                int fixed = params.length - 1;
                for (int i = 0; i < fixed; i++) {
                    bound[i] = coerce(args[i], params[i], member.name() + " arg" + i);
                }
                Class<?> component = params[fixed].getComponentType();
                Object varargs = java.lang.reflect.Array.newInstance(component, args.length - fixed);
                for (int i = fixed; i < args.length; i++) {
                    java.lang.reflect.Array.set(varargs, i - fixed,
                            coerce(args[i], component, member.name() + " arg" + i));
                }
                bound[fixed] = varargs;
                return bound;
            }
            for (int i = 0; i < params.length; i++) {
                bound[i] = coerce(args[i], params[i], member.name() + " arg" + i);
            }
            return bound;
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

    /** JS 值 → setter/方法参数。错误信息带成员名与期望类型（AC7：错误结果由契约派生）。 */
    private static Object coerce(Value value, Class<?> target, String what) {
        if (Consumer.class.isAssignableFrom(target) && value.canExecute()) {
            return consumerOf(value);
        }
        if (value.isNull()) {
            if (target.isPrimitive()) {
                throw new IllegalArgumentException(what + ": null is not a valid " + target.getSimpleName());
            }
            return null;
        }
        if (target == int.class || target == Integer.class) {
            requireNumber(value, what);
            return value.asInt();
        }
        if (target == long.class || target == Long.class) {
            requireNumber(value, what);
            return value.asLong();
        }
        if (target == float.class || target == Float.class) {
            requireNumber(value, what);
            return value.asFloat();
        }
        if (target == double.class || target == Double.class) {
            requireNumber(value, what);
            return value.asDouble();
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
        if (target.isInstance(unwrapHost(value))) {
            return unwrapHost(value);
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

    private static Object unwrapHost(Value value) {
        return value.isHostObject() ? value.asHostObject() : null;
    }

    private static void requireNumber(Value value, String what) {
        if (!value.isNumber()) {
            throw new IllegalArgumentException(what + " expects a number");
        }
    }

    /** JS 函数 → Java {@link Consumer}：回调参数若为被包裹 builder 则解包后重包本面。 */
    private static Consumer<Object> consumerOf(Value function) {
        return argument -> {
            Object wrapped = argument instanceof RegistryObjectBuilder<?> ? BuilderSurface.of(argument) : argument;
            function.execute(wrapped);
        };
    }

    /** 返回值若为 builder（如 {@code b.item} 子 builder、链式方法返回 this）则包本面。 */
    private static Object wrapResult(Object result) {
        if (result instanceof RegistryObjectBuilder<?>) {
            return BuilderSurface.of(result);
        }
        return result;
    }

    private static RuntimeException asInvocationError(String member, InvocationTargetException e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException runtime ? runtime
                : new IllegalArgumentException("member '" + member + "' failed: " + cause, cause);
    }
}
