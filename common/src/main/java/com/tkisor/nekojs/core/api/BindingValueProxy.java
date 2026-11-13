package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.JavaMemberIndex;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A Context-local view of a public type, with its Bean properties and overloads. */
public final class BindingValueProxy implements ProxyObject {
    private final Value delegate;
    private final Object target;
    private final JavaMemberIndex.ExposedMembers members;
    private final boolean staticOnly;
    private final Set<String> names;

    public BindingValueProxy(Value delegate, Class<?> publicType, boolean staticOnly) {
        this.delegate = delegate;
        this.target = staticOnly ? null : delegate.isHostObject() ? delegate.asHostObject() : null;
        this.members = JavaMemberIndex.exposedMembersOf(publicType);
        this.staticOnly = staticOnly;
        Set<String> exposed = new LinkedHashSet<>();
        members.methods().forEach((name, methods) -> {
            if (methods.stream().anyMatch(this::applicable)) exposed.add(name);
        });
        members.propertyGetters().forEach((name, methods) -> {
            if (methods.stream().anyMatch(this::applicable)) exposed.add(name);
        });
        members.fields().forEach((name, fields) -> {
            if (!staticOnly && !fields.isEmpty()) exposed.add(name);
        });
        members.methods().forEach((name, methods) -> {
            if (name.startsWith("set") && name.length() > 3 && methods.stream().anyMatch(
                    m -> m.getParameterCount() == 1 && applicable(m))) {
                exposed.add(Character.toLowerCase(name.charAt(3)) + name.substring(4));
            }
        });
        this.names = Set.copyOf(exposed);
    }

    private boolean applicable(Method method) {
        return Modifier.isStatic(method.getModifiers()) == staticOnly;
    }

    @Override
    public Object getMember(String key) {
        if (!names.contains(key)) return null;
        var getters = members.propertyGetters().get(key);
        if (getters != null) {
            var getter = getters.stream().filter(this::applicable).findFirst();
            if (getter.isPresent()) return invoke(List.of(getter.get()), new Value[0]);
        }
        var methods = members.methods().get(key);
        if (methods != null) return (ProxyExecutable) args -> invoke(methods, args);
        var fields = members.fields().get(key);
        if (fields != null) {
            try {
                var field = fields.getFirst();
                return narrow(Value.asValue(field.get(target)), field.getType());
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("[NEKO-4057] Cannot read public API field: " + key, failure);
            }
        }
        return null;
    }

    private Object invoke(List<Method> methods, Value[] args) {
        Method selected = null;
        Object[] converted = null;
        int best = Integer.MAX_VALUE;
        for (Method method : methods) {
            if (!applicable(method)) continue;
            int fixed = method.getParameterCount() - (method.isVarArgs() ? 1 : 0);
            if (args.length < fixed || (!method.isVarArgs() && args.length != fixed)) continue;
            try {
                Object[] candidate = new Object[method.getParameterCount()];
                int score = method.isVarArgs() ? 10 : 0;
                for (int index = 0; index < fixed; index++) {
                    Class<?> type = method.getParameterTypes()[index];
                    candidate[index] = convert(args[index], type);
                    if (type == Object.class) score += 5;
                }
                if (method.isVarArgs()) {
                    Class<?> component = method.getParameterTypes()[fixed].componentType();
                    Object tail = Array.newInstance(component, args.length - fixed);
                    for (int index = fixed; index < args.length; index++) {
                        Array.set(tail, index - fixed, convert(args[index], component));
                    }
                    candidate[fixed] = tail;
                }
                if (score < best) {
                    selected = method;
                    converted = candidate;
                    best = score;
                }
            } catch (ClassCastException | IllegalArgumentException ignored) {
                // Only overloads declared by the public type participate in conversion.
            }
        }
        if (selected == null) throw new IllegalArgumentException("[NEKO-4057] No matching public API overload");
        try {
            if (target == null && !staticOnly) {
                // Public interfaces backed by a guest object still convert through the declared type.
                Object guestView = delegate.as(selected.getDeclaringClass());
                return narrow(Value.asValue(selected.invoke(guestView, converted)), selected.getReturnType());
            }
            return narrow(Value.asValue(selected.invoke(target, converted)), selected.getReturnType());
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("[NEKO-4057] Public API invocation failed", cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("[NEKO-4057] Cannot invoke public API member", failure);
        }
    }

    private static Object convert(Value value, Class<?> type) {
        if (value.isProxyObject() && value.asProxyObject() instanceof BindingValueProxy view
                && view.target != null && type.isInstance(view.target)) return view.target;
        return value.as(type);
    }

    private static Object narrow(Value value, Class<?> type) {
        if (value == null || value.isNull()) return null;
        if (!type.isPrimitive() && type != Object.class && !type.isArray()
                && !type.getPackageName().startsWith("java.") && !type.getPackageName().startsWith("graal.")
                && !type.getPackageName().startsWith("net.minecraft.")
                && !type.getPackageName().startsWith("net.neoforged.")
                && !type.getPackageName().startsWith("net.fabricmc.")) {
            return new BindingValueProxy(value, type, false);
        }
        return value;
    }

    @Override public Object getMemberKeys() { return names.toArray(String[]::new); }
    @Override public boolean hasMember(String key) { return names.contains(key); }

    @Override
    public void putMember(String key, Value value) {
        if (!names.contains(key)) throw new UnsupportedOperationException("[NEKO-4057] Unknown public API member: " + key);
        String setter = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        var methods = members.methods().get(setter);
        if (methods != null && methods.stream().anyMatch(m -> m.getParameterCount() == 1 && applicable(m))) {
            invoke(methods, new Value[]{value});
            return;
        }
        var fields = members.fields().get(key);
        if (fields != null) {
            var field = fields.getFirst();
            if (!Modifier.isFinal(field.getModifiers())) {
                try {
                    field.set(target, convert(value, field.getType()));
                    return;
                } catch (IllegalAccessException failure) {
                    throw new IllegalStateException("[NEKO-4057] Cannot write public API field: " + key, failure);
                }
            }
        }
        throw new UnsupportedOperationException("[NEKO-4057] API member is read-only: " + key);
    }
}
