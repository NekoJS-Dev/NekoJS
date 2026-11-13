package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.core.bridge.ScriptNumberCoercion;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Script-facing wrapper around a dynamic definition builder: exposes the
 * {@link DynamicDefinitionBuilder} as a {@link ProxyObject} so that property writes and explicit
 * setter calls <b>dispatch to the same fluent setter</b> {@link Method}
 * ({@link DynamicBuilderContract.Member#setter()}).
 *
 * <p>It forwards through the same {@code putMember} point as the startup {@code BuilderSurface}:
 * a property write on the host object never reaches the setter, and a {@link ProxyObject} is the
 * reliable place to forward from. But it serves the <b>separate dynamic builder path</b>: it does
 * not implement {@code RegistryObjectBuilder}, does not take part in the startup drain, and the
 * builder it wraps only enters an inert candidate plan. {@code b.maxStackSize = 16} and
 * {@code b.maxStackSize(16)} therefore share the same validation, normalization and definition
 * fingerprint path. The Graal bean mapping this relies on is pinned by the parity fixture, and
 * this class's behavior by {@code DynamicBuilderSurfaceParityTest}, which runs on real GraalJS.
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

    /** Wraps a dynamic definition builder. */
    public static DynamicBuilderSurface of(DynamicDefinitionBuilder builder) {
        return new DynamicBuilderSurface(builder);
    }

    /** Unwraps a builder wrapped by this class; returns any other value unchanged. */
    public static Object unwrap(Object value) {
        return value instanceof DynamicBuilderSurface surface ? surface.builder : value;
    }

    /** The wrapped builder, for the plan and Adapter side; the script side never touches it. */
    public DynamicDefinitionBuilder target() {
        return builder;
    }

    // ---- ProxyObject: member directory ----

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

    /** Writes a property: forwards to the same fluent setter an explicit setter call reaches. */
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

    // ---- Method members: fluent setters and zero-argument convenience methods ----

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

    // ---- Value conversion ----

    /**
     * Converts a JS value to a setter or method argument. Error messages carry the member name and
     * the expected type. Numeric members go through {@link ScriptNumberCoercion}: script numbers
     * are doubles, and the lossy {@code asInt/asLong/asFloat} checks would reject valid values
     * such as {@code fixedRange = 0.7} with a raw engine exception.
     */
    static Object coerce(Value value, Class<?> target, String what) {
        if (value.isNull()) {
            if (target.isPrimitive()) {
                throw new IllegalArgumentException(what + ": null is not a valid " + target.getSimpleName());
            }
            return null;
        }
        if (target == int.class || target == Integer.class) {
            return ScriptNumberCoercion.toInt(value, what);
        }
        if (target == long.class || target == Long.class) {
            return ScriptNumberCoercion.toLong(value, what);
        }
        if (target == float.class || target == Float.class) {
            return ScriptNumberCoercion.toFloat(value, what);
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
