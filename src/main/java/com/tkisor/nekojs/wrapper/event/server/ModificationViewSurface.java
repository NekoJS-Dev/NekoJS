package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.bridge.ScriptNumberCoercion;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * {@link ProxyObject} adapter for modification property views ({@code ItemModificationJS} and
 * {@code BlockModificationJS}), following the same member-dispatch pattern as {@code BuilderSurface}.
 * Property assignments ({@code item.maxStackSize = 16} via {@code putMember}) and explicit setter
 * calls ({@code item.setMaxStackSize(16)} via {@code getMember}) invoke the same Java setter
 * {@link Method}, so both forms share validation, normalization, declaration recording, and plan
 * fingerprinting.
 *
 * <p>The member catalog registers both bean property names (such as {@code maxStackSize}) and
 * original accessor names ({@code setMaxStackSize} / {@code getMaxStackSize}), because explicit
 * method calls on a {@link ProxyObject} in GraalJS resolve through {@code getMember} rather than
 * host-object reflection.
 *
 * <p>This avoids relying on GraalJS host-object bean mapping, which silently drops property
 * writes on plain host objects with private fields and public setters (verified in
 * {@code ModificationLegacyCharacterizationTest.hostPropertyWriteLegacyFactAndProxyObjectViewContract}).
 *
 * <p>Each view class also implements {@link ProxyObject} directly and delegates to an instance of
 * this class, so {@code Consumer} callbacks can receive the view instance without an extra wrapper
 * while routing both property writes and setter calls to the same {@link Method}.
 *
 * <p>Free of Minecraft types (the member catalog is reflected from the view class and cached), so
 * 26.x and 1.21.1 share this class. Package-private lifecycle methods such as {@code applyTo} are
 * excluded from the public member catalog.
 */
public final class ModificationViewSurface implements ProxyObject {

    /** Member catalog (view class immutable, cached by class). */
    private static final Map<Class<?>, MemberCatalog> CATALOGS = new ConcurrentHashMap<>();

    /** {@link ProxyObject} method names implemented on the view itself and excluded from script members. */
    private static final Set<String> PROXY_OBJECT_MEMBER_NAMES = proxyObjectMemberNames();

    private final Object view;

    private ModificationViewSurface(Object view) {
        this.view = view;
    }

    /** Wraps a modification property view. */
    public static ModificationViewSurface of(Object view) {
        return new ModificationViewSurface(view);
    }

    /** Unwraps {@code value} to the underlying view if wrapped in {@code ModificationViewSurface}; otherwise returns it as-is. */
    public static Object unwrap(Object value) {
        return value instanceof ModificationViewSurface surface ? surface.view : value;
    }

    // ---- ProxyObject: member catalog ----

    @Override
    public Object getMember(String name) {
        MemberCatalog catalog = catalog();
        Method setter = catalog.setters.get(name);
        if (setter != null) {
            // Writable property: read side follows companion getter (view's pending-value read semantics)
            Method getter = catalog.getters.get(name);
            if (getter != null) {
                return readGetter(getter);
            }
            // Write-only property: reject reads instead of returning the setter function
            // (explicit calls like item.setMaxStackSize(...) still resolve through catalog.methods)
            throw new IllegalArgumentException("'" + name + "' on " + viewTypeName()
                    + " is write-only; readable: " + catalog.getterNames());
        }
        Method getter = catalog.getters.get(name);
        if (getter != null) {
            return readGetter(getter);
        }
        Method method = catalog.methods.get(name);
        if (method != null) {
            return new SetterMethod(method, name);
        }
        throw new IllegalArgumentException(viewTypeName() + " has no member '" + name
                + "'; known: " + catalog.memberNames());
    }

    @Override
    public boolean hasMember(String name) {
        MemberCatalog catalog = catalog();
        return catalog.setters.containsKey(name) || catalog.getters.containsKey(name)
                || catalog.methods.containsKey(name);
    }

    @Override
    public Object getMemberKeys() {
        return catalog().memberNames().toArray();
    }

    /** Routes property assignment to the same Java setter used by explicit setter calls. */
    @Override
    public void putMember(String name, Value value) {
        Method setter = catalog().setters.get(name);
        if (setter == null) {
            MemberCatalog catalog = catalog();
            if (catalog.getters.containsKey(name)) {
                throw new IllegalArgumentException("'" + name + "' on " + viewTypeName()
                        + " is read-only; writable: " + catalog.setterNames());
            }
            throw new IllegalArgumentException(viewTypeName() + " has no member '" + name
                    + "'; known: " + catalog.memberNames());
        }
        invoke(setter, name, coerce(value, setter.getParameterTypes()[0], name));
    }

    @Override
    public boolean removeMember(String name) {
        return false;
    }

    // ---- Reflective accessors ----

    private Object readGetter(Method getter) {
        try {
            return getter.invoke(view);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read '" + getter.getName() + "' of " + viewTypeName(), e);
        } catch (InvocationTargetException e) {
            throw asInvocationError(getter.getName(), e);
        }
    }

    private void invoke(Method method, String name, Object argument) {
        try {
            method.invoke(view, argument);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot write '" + name + "' of " + viewTypeName(), e);
        } catch (InvocationTargetException e) {
            throw asInvocationError(name, e);
        }
    }

    private MemberCatalog catalog() {
        return CATALOGS.computeIfAbsent(view.getClass(), MemberCatalog::new);
    }

    private String viewTypeName() {
        return view.getClass().getSimpleName();
    }

    /** Callable member wrapper for explicit accessor calls; modification views do not overload setters. */
    private final class SetterMethod implements ProxyExecutable {
        private final Method method;
        private final String name;

        SetterMethod(Method method, String name) {
            this.method = method;
            this.name = name;
        }

        @Override
        public Object execute(Value... args) {
            if (args.length != method.getParameterCount()) {
                throw new IllegalArgumentException(name + " on " + viewTypeName() + " expects "
                        + method.getParameterCount() + " argument(s) but got " + args.length);
            }
            if (args.length == 0) {
                try {
                    return method.invoke(view);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("cannot invoke '" + name + "' of " + viewTypeName(), e);
                } catch (InvocationTargetException e) {
                    throw asInvocationError(name, e);
                }
            }
            Object argument = coerce(args[0], method.getParameterTypes()[0], name);
            invoke(method, name, argument);
            return null;
        }
    }

    /**
     * Coerces a JS value to the setter's parameter type, including the member name and expected
     * type in error messages (matching {@code BuilderSurface}). Numeric parameters go through
     * {@link ScriptNumberCoercion} because JS numbers are doubles and Graal's lossy
     * {@code asInt}/{@code asLong}/{@code asFloat} checks reject valid values like
     * {@code friction = 0.9} with a raw engine exception.
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
            return value.isString() ? value.asString() : value.as(target);
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

    private static void requireNumber(Value value, String what) {
        if (!value.isNumber()) {
            throw new IllegalArgumentException(what + " expects a number");
        }
    }

    /** Adapts a JS function to a Java {@link Consumer}, unwrapping wrapped view arguments. */
    private static Consumer<Object> consumerOf(Value function) {
        return argument -> function.execute(ModificationViewSurface.unwrap(argument));
    }

    private static RuntimeException asInvocationError(String member, InvocationTargetException e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException runtime ? runtime
                : new IllegalArgumentException("member '" + member + "' failed: " + cause, cause);
    }

    /** Method names declared on {@link ProxyObject}, filtered out when building a {@link MemberCatalog}. */
    private static Set<String> proxyObjectMemberNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Method method : ProxyObject.class.getMethods()) {
            names.add(method.getName());
        }
        return Set.copyOf(names);
    }

    /** Public instance member catalog for a view class: paired getters/setters and callable methods. */
    private static final class MemberCatalog {
        final Map<String, Method> setters = new LinkedHashMap<>();
        final Map<String, Method> getters = new LinkedHashMap<>();
        final Map<String, Method> methods = new LinkedHashMap<>();

        MemberCatalog(Class<?> type) {
            for (Method method : type.getMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isStatic(modifiers) || method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                // View classes implement ProxyObject to delegate here; skip those interface
                // methods so getMember/putMember do not leak into the script catalog (otherwise
                // getMemberKeys would also become a read-only "memberKeys" property).
                if (PROXY_OBJECT_MEMBER_NAMES.contains(method.getName())) {
                    continue;
                }
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();
                if (name.startsWith("set") && name.length() > 3 && params.length == 1) {
                    // Register both the bean property name and the explicit setter method name
                    setters.put(property(name), method);
                    methods.put(name, method);
                } else if ((name.startsWith("get") || name.startsWith("is")) && params.length == 0) {
                    getters.put(property(name), method);
                    methods.put(name, method);
                } else {
                    // Other public instance methods (package-private methods are excluded by getMethods; Object methods are filtered above)
                    methods.put(name, method);
                }
            }
        }

        private static String property(String accessorName) {
            if (accessorName.startsWith("is")) {
                return Character.toLowerCase(accessorName.charAt(2)) + accessorName.substring(3);
            }
            return Character.toLowerCase(accessorName.charAt(3)) + accessorName.substring(4);
        }

        /** Deduplicated member names (an accessor is registered under both its property name and method name). */
        List<String> memberNames() {
            Set<String> names = new LinkedHashSet<>();
            names.addAll(setters.keySet());
            names.addAll(getters.keySet());
            names.addAll(methods.keySet());
            return List.copyOf(names);
        }

        List<String> setterNames() {
            return List.copyOf(setters.keySet());
        }

        List<String> getterNames() {
            return List.copyOf(getters.keySet());
        }
    }
}
