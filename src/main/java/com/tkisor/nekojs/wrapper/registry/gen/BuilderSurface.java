package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import com.tkisor.nekojs.core.bridge.ScriptNumberCoercion;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Script surface of a typed builder: wraps a {@link RegistryObjectBuilder} in a
 * {@link ProxyObject} so that property writes and explicit setters <b>dispatch to the same Java
 * setter</b>.
 *
 * <p>It does not rely on Graal's built-in Bean mapping for host objects. Measurements (fixed by
 * the runtime contract fixture {@code BuilderSetterPropertyParityTest}) show that a property
 * write on a host object never reaches the setter, so the putMember hook of ProxyObject is the
 * reliable forwarding point. Both kinds of write go through the single {@link Method} returned by
 * {@link RegistryBuilderContract.Member#setter()} and enter the same validation, normalization,
 * definition fingerprint and registry collection path. {@code final id}, read-only members and
 * unreleased experimental members are exceptions (they are exposed directly on the read side).
 *
 * <p>Composite configuration callbacks (Consumer parameters such as {@code food(cb)}) also
 * receive their sub builder wrapped in this surface, so a raw host builder never leaks back to
 * the script and there is no public-field bypass.
 */
public final class BuilderSurface implements ProxyObject {

    private final Object builder;
    private final RegistryBuilderContract contract;

    private BuilderSurface(Object builder) {
        this.builder = builder;
        this.contract = RegistryBuilderContract.of(builderClass(builder));
    }

    /** Wraps a builder (the builder type must be a {@link RegistryObjectBuilder} subclass). */
    public static BuilderSurface of(Object builder) {
        return new BuilderSurface(builder);
    }

    /**
     * Returns the raw object when value is a builder wrapped by this surface, and value unchanged
     * otherwise. Used to unwrap arguments.
     */
    public static Object unwrap(Object value) {
        if (value instanceof CallableBuilderProperty property) return property.nested.builder;
        return value instanceof BuilderSurface surface ? surface.builder : value;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RegistryObjectBuilder<?>> builderClass(Object builder) {
        return (Class<? extends RegistryObjectBuilder<?>>) builder.getClass();
    }

    /**
     * The wrapped raw builder, used by the Java side for collection and registration; the script
     * surface never touches it.
     */
    public Object target() {
        return builder;
    }

    // ---- ProxyObject: member catalog ----

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

    /** Writes a property: forwards to the same setter method the explicit setter uses. */
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

    // ---- Property access ----

    private Object readProperty(RegistryBuilderContract.Member member) {
        // Getter or final identity field; contract.readMember handles both and puts the member name in the exception.
        Object result = wrapResult(contract.readMember(builder, member));
        return result instanceof BuilderSurface nested && !member.overloads().isEmpty()
                ? new CallableBuilderProperty(nested, new MethodMember(member)) : result;
    }

    /** A nested builder can expose both property access and its parent's configuration callback. */
    private record CallableBuilderProperty(BuilderSurface nested, ProxyExecutable configure)
            implements ProxyObject, ProxyExecutable {
        @Override public Object execute(Value... arguments) { return configure.execute(arguments); }
        @Override public Object getMember(String name) { return nested.getMember(name); }
        @Override public Object getMemberKeys() { return nested.getMemberKeys(); }
        @Override public boolean hasMember(String name) { return nested.hasMember(name); }
        @Override public void putMember(String name, Value value) { nested.putMember(name, value); }
        @Override public boolean removeMember(String name) { return false; }
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

    // ---- Method members, including explicit setters ----

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

    // ---- Value conversion ----

    /**
     * Converts a JS value to a setter or method argument. Errors name the member and the expected type.
     * Numeric members normalize through {@link ScriptNumberCoercion}: script numbers are doubles, and
     * the lossy checks in {@code asInt/asLong/asFloat} would reject valid values like
     * {@code hardness = 0.9} with a raw engine exception.
     */
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
            return ScriptNumberCoercion.toInt(value, what);
        }
        if (target == long.class || target == Long.class) {
            return ScriptNumberCoercion.toLong(value, what);
        }
        if (target == float.class || target == Float.class) {
            return ScriptNumberCoercion.toFloat(value, what);
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
        if (value.isProxyObject()) return unwrap(value.asProxyObject());
        return value.isHostObject() ? value.asHostObject() : null;
    }

    private static void requireNumber(Value value, String what) {
        if (!value.isNumber()) {
            throw new IllegalArgumentException(what + " expects a number");
        }
    }

    /** Wraps a JS function as a Java {@link Consumer}. A callback argument that is a wrapped builder is
     * unwrapped and then wrapped in this surface again. */
    private static Consumer<Object> consumerOf(Value function) {
        return argument -> {
            Object wrapped = argument instanceof RegistryObjectBuilder<?> ? BuilderSurface.of(argument) : argument;
            function.execute(wrapped);
        };
    }

    /** Wraps the result in this surface when it is a builder, such as a {@code b.item} child builder or a
     * chained method that returns this. */
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
