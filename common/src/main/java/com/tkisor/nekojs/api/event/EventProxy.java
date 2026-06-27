package com.tkisor.nekojs.api.event;

import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件对象校验代理：装饰传入 JS 事件 callbacks 的 Java 事件对象，
 * 通过 GraalVM {@link ProxyObject} 拦截成员访问。
 *
 * <p>对已知成员（getter / public field）正常返回；
 * 对未知成员抛出清晰错误（含最接近候选建议），使脚本拼写错误在事件触发时立即暴露，
 * 不再静默返回 {@code undefined}。
 *
 * <p>用法：在 {@link EventBusJS} 回调分发处，将真实 event 对象包装为
 * {@code EventProxy.of(event)}，再传给 callback。
 */
public final class EventProxy implements ProxyObject {

    private static final Map<Class<?>, Set<String>> MEMBER_CACHE = new ConcurrentHashMap<>();

    private final Object target;
    private final Class<?> targetClass;
    private final Set<String> validMembers;

    private EventProxy(Object target, Class<?> targetClass, Set<String> validMembers) {
        this.target = target;
        this.targetClass = targetClass;
        this.validMembers = validMembers;
    }

    public static EventProxy of(Object event) {
        Class<?> clazz = event.getClass();
        Set<String> members = validMembers(clazz);
        return new EventProxy(event, clazz, members);
    }

    public static Set<String> validMembers(Class<?> clazz) {
        return MEMBER_CACHE.computeIfAbsent(clazz, EventProxy::collectMembers);
    }

    public static String suggestMember(Class<?> clazz, String key) {
        return suggest(key, validMembers(clazz));
    }

    public static String unknownMemberMessage(Class<?> clazz, String key) {
        String suggest = suggestMember(clazz, key);
        return "Event '" + clazz.getSimpleName() + "' has no member '" + key + "'." +
                (suggest != null ? " Did you mean '" + suggest + "'?" : "");
    }

    public static Class<?> memberType(Class<?> clazz, String key) {
        if (clazz == null || key == null || key.isBlank()) {
            return null;
        }
        String cap = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        try {
            return clazz.getMethod("get" + cap).getReturnType();
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return clazz.getMethod("is" + cap).getReturnType();
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return clazz.getMethod("has" + cap).getReturnType();
        } catch (NoSuchMethodException ignored) {
        }
        Field field = findField(clazz, key);
        return field != null ? field.getType() : null;
    }

    @Override
    public Object getMember(String key) {
        if (key == null || key.isEmpty()) return null;
        if (validMembers.contains(key)) {
            // Try getter: damage -> getDamage, alive -> isAlive
            String cap = Character.toUpperCase(key.charAt(0)) + key.substring(1);
            try {
                try {
                    return targetClass.getMethod("get" + cap).invoke(target);
                } catch (NoSuchMethodException e1) {
                    try {
                        return targetClass.getMethod("is" + cap).invoke(target);
                    } catch (NoSuchMethodException e2) {
                        try {
                            return targetClass.getMethod("has" + cap).invoke(target);
                        } catch (NoSuchMethodException e3) {
                            Field f = findField(targetClass, key);
                            if (f != null) {
                                f.setAccessible(true);
                                return f.get(target);
                            }
                            return null;
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to access '" + key + "' on " + targetClass.getSimpleName(), e);
            }
        }
        throw new IllegalArgumentException(unknownMemberMessage(targetClass, key));
    }

    @Override
    public Object getMemberKeys() {
        return validMembers.toArray(new String[0]);
    }

    @Override
    public boolean hasMember(String key) {
        return validMembers.contains(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Event objects are read-only");
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }

    private static String suggest(String key, Set<String> validMembers) {
        if (key == null || key.isEmpty()) return null;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String member : validMembers) {
            int d = levenshtein(key, member);
            if (d < bestDist) {
                bestDist = d;
                best = member;
            }
        }
        return bestDist <= 3 ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                curr[j] = Math.min(
                    Math.min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1)
                );
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    private static Set<String> collectMembers(Class<?> clazz) {
        Set<String> members = new LinkedHashSet<>();
        for (Method m : clazz.getMethods()) {
            String name = m.getName();
            if (m.getParameterCount() != 0 || "getClass".equals(name) || name.startsWith("neko$")) {
                continue;
            }
            String prop = propertyName(name);
            if (prop != null) {
                members.add(prop);
            }
        }
        for (Field f : clazz.getFields()) {
            members.add(f.getName());
        }
        return members;
    }

    private static String propertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        if (methodName.startsWith("has") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
