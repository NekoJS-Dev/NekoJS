package com.tkisor.nekojs.wrapper.event.server;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 修改属性视图的脚本面（ticket 39 AC8，复用票 15 {@code BuilderSurface} 的
 * ProxyObject putMember → 同一 {@link Method} seam 手法）：把
 * {@code ItemModificationJS}/{@code BlockModificationJS} 包成 {@link ProxyObject}，
 * {@code item.maxStackSize = 16}（putMember）与 {@code item.setMaxStackSize(16)}
 * （成员方法）分发到<b>同一个 Java setter</b>，进入同一校验、规范化、声明收集与
 * definition fingerprint 路径。
 *
 * <p>不依赖 Graal 对宿主对象的天然 Bean 映射——characterization
 * （{@code ModificationLegacyCharacterizationTest.graalPropertyWriteOnHostViewDoesNotReachSetter}）
 * 实证宿主视图的 property 写被静默丢弃（{@code assigned,read=undefined}），ProxyObject
 * 的 putMember seam 才是可靠转发点（spec 08 预授权回退路径，与 typed Builder 同款）。
 *
 * <p>本类不携带 MC 类型（成员目录按视图类反射派生并缓存），26.x 与 1.21.1 共享编译。
 * 引擎生命周期接缝（{@code applyTo} 等包私有/内部方法）不进脚本面。
 */
public final class ModificationViewSurface implements ProxyObject {

    /** 成员目录（视图类不可变，按类缓存）。 */
    private static final Map<Class<?>, MemberCatalog> CATALOGS = new ConcurrentHashMap<>();

    private final Object view;

    private ModificationViewSurface(Object view) {
        this.view = view;
    }

    /** 包一个修改属性视图。 */
    public static ModificationViewSurface of(Object view) {
        return new ModificationViewSurface(view);
    }

    /** 若 value 是被本面包裹的视图，解出裸对象；否则原样返回。 */
    public static Object unwrap(Object value) {
        return value instanceof ModificationViewSurface surface ? surface.view : value;
    }

    // ---- ProxyObject：成员目录 ----

    @Override
    public Object getMember(String name) {
        MemberCatalog catalog = catalog();
        Method setter = catalog.setters.get(name);
        if (setter != null) {
            // 可写属性：读面走配套 getter（视图的 pending-value 读语义）
            Method getter = catalog.getters.get(name);
            if (getter != null) {
                return readGetter(getter);
            }
            return new SetterMethod(setter, name);
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

    /** 属性写入：转发到与显式 setter 相同的 setter 方法。 */
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

    // ---- 反射读写 ----

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

    /** 方法成员（显式 setter 形态等）：单一方法（修改视图无重载面）。 */
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

    /** 值装配：JS 值 → setter 参数。错误信息带成员名与期望类型（与 BuilderSurface 同款）。 */
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

    /** JS 函数 → Java Consumer：回调参数若为被包裹视图则解包。 */
    private static Consumer<Object> consumerOf(Value function) {
        return argument -> function.execute(ModificationViewSurface.unwrap(argument));
    }

    private static RuntimeException asInvocationError(String member, InvocationTargetException e) {
        Throwable cause = e.getCause();
        return cause instanceof RuntimeException runtime ? runtime
                : new IllegalArgumentException("member '" + member + "' failed: " + cause, cause);
    }

    /** 视图类的公开实例成员目录：setter/getter 配对 + 其余方法。 */
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
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();
                if (name.startsWith("set") && name.length() > 3 && params.length == 1) {
                    setters.put(property(name), method);
                } else if ((name.startsWith("get") || name.startsWith("is")) && params.length == 0) {
                    getters.put(property(name), method);
                } else {
                    // 引擎接缝（包私有不进 getMethods；equals/hashCode/toString 已被 Object 过滤）
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

        List<String> memberNames() {
            List<String> names = new ArrayList<>();
            names.addAll(setters.keySet());
            names.addAll(getters.keySet());
            names.addAll(methods.keySet());
            return names;
        }

        List<String> setterNames() {
            return new ArrayList<>(setters.keySet());
        }
    }
}
