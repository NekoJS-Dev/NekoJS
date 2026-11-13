package com.tkisor.nekojs.core.dynamic.plan;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public member contract of a dynamic definition builder. It derives the member directory from
 * the builder class by <b>reflection</b>, and that single contract input drives the script-facing
 * {@link DynamicBuilderSurface} (runtime members: a property write and an explicit setter call
 * both forward to the same {@link Method}), the normalized readings of the definition
 * fingerprint, and the TS/Python declarations (structured entries derived through
 * {@link DynamicBuilderSurfaces}). There is no second hand-written member table.
 *
 * <p>Members are classified under the same convention as {@code RegistryBuilderContract} (the two
 * JavaBean forms, see the naming decision in {@link DynamicDefinitionBuilder}):
 * <ul>
 *   <li><b>Writable property</b>: both {@code setXxx(T)} and the matching {@code getXxx():T}
 *       (or {@code isXxx()} for boolean) exist. On the script side {@code b.xxx = v}
 *       (ProxyObject putMember) and an explicit {@code b.setXxx(v)} (method member) call the same
 *       setter {@link Method} and go through the same validation, normalization and fingerprint
 *       path;</li>
 *   <li><b>Read-only property</b>: a getter without a setter (none exist on the current dynamic
 *       builders, but the rule still classifies them);</li>
 *   <li><b>Method</b>: every other public instance method, including the explicit setter form
 *       {@code setXxx(v)} itself.</li>
 * </ul>
 * Object methods such as {@code toString} and static members stay out of the script surface. The
 * reflection result is cached per class, since builder classes do not change.
 */
public final class DynamicBuilderContract {

    private static final Map<Class<?>, DynamicBuilderContract> CACHE = new ConcurrentHashMap<>();

    /** Member kind: writable property (both setter forms), read-only property, or method including
     *  the explicit setter. */
    public enum MemberKind { WRITABLE_PROPERTY, READ_ONLY_PROPERTY, METHOD }

    /**
     * One contract member. For a writable property, {@code setter()} is the only setter
     * {@link Method}, shared by property writes and explicit calls.
     */
    public record Member(String name, MemberKind kind, Class<?> valueType, Method getter, Method setter,
            List<Method> overloads) {

        Member(String name, MemberKind kind, Class<?> valueType, Method getter, Method setter) {
            this(name, kind, valueType, getter, setter, List.of());
        }

        /** TS declaration type. The mapping is deterministic and an unknown type becomes any. */
        public String tsType() {
            return tsTypeOf(valueType);
        }

        /** Python declaration type, derived from the same input as the TS type. */
        public String pyType() {
            return pyTypeOf(valueType);
        }
    }

    private final Class<?> builderType;
    private final Map<String, Member> members;

    private DynamicBuilderContract(Class<?> builderType) {
        this.builderType = builderType;
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();
        Map<String, List<Method>> rest = new LinkedHashMap<>();
        for (Method method : builderType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class || name.equals("toString")) {
                continue;
            }
            if (isGetterName(name) && method.getParameterCount() == 0 && method.getReturnType() != void.class) {
                getters.put(propertyName(name), method);
            } else if (isSetterName(name) && method.getParameterCount() == 1) {
                setters.put(propertyName(name), method);
            } else {
                rest.computeIfAbsent(name, key -> new ArrayList<>()).add(method);
            }
        }
        Map<String, Member> result = new LinkedHashMap<>();
        Map<String, List<Method>> methodOverloads = new LinkedHashMap<>();
        getters.keySet().forEach(property -> {
            Method setter = setters.get(property);
            Method getter = getters.get(property);
            if (setter != null) {
                result.put(property, new Member(property, MemberKind.WRITABLE_PROPERTY,
                        setter.getParameterTypes()[0], getter, setter));
            } else {
                result.put(property, new Member(property, MemberKind.READ_ONLY_PROPERTY,
                        getter.getReturnType(), getter, null));
            }
        });
        // Keep the explicit setter form (b.setXxx(v)) as a method member; it is the same Method a
        // property write uses
        setters.forEach((property, setter) -> methodOverloads
                .computeIfAbsent(setter.getName(), key -> new ArrayList<>()).add(setter));
        rest.forEach((name, overloads) -> methodOverloads
                .computeIfAbsent(name, key -> new ArrayList<>()).addAll(overloads));
        methodOverloads.forEach((name, overloads) -> {
            // getMethods() has no guaranteed order: sort each overload list by parameter count descending,
            // then by signature string, so the derivation is deterministic
            List<Method> stable = overloads.stream()
                    .sorted(Comparator.comparingInt((Method m) -> -m.getParameterCount())
                            .thenComparing(Method::toGenericString))
                    .toList();
            result.putIfAbsent(name, new Member(name, MemberKind.METHOD, null, null, null, stable));
        });
        Map<String, Member> sorted = new LinkedHashMap<>();
        result.values().stream().sorted(Comparator.comparing(Member::name))
                .forEach(member -> sorted.put(member.name(), member));
        this.members = sorted;
    }

    /** Returns the contract of a dynamic builder class, creating and caching it on first use. */
    public static DynamicBuilderContract of(Class<? extends DynamicDefinitionBuilder> builderType) {
        return CACHE.computeIfAbsent(builderType, key -> new DynamicBuilderContract((Class<?>) key));
    }

    /** All members, sorted by name. */
    public List<Member> members() {
        return List.copyOf(members.values());
    }

    /** Returns the member with this name, or null when none is registered. */
    public Member member(String name) {
        return members.get(name);
    }

    /** The names of all members, for diagnostics and completion. */
    public List<String> memberNames() {
        return List.copyOf(members.keySet());
    }

    /** The names of writable properties, used in {@code putMember} error messages. */
    public List<String> writableNames() {
        List<String> names = new ArrayList<>();
        members.values().forEach(member -> {
            if (member.kind() == MemberKind.WRITABLE_PROPERTY) {
                names.add(member.name());
            }
        });
        return names;
    }

    /** Reads a property member's current value through its getter. */
    public Object readMember(Object builder, Member member) {
        try {
            if (member.getter() != null) {
                return member.getter().invoke(builder);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read builder member '" + member.name() + "'", e);
        }
        throw new IllegalArgumentException(
                "'" + member.name() + "' is not a readable property of " + builderType.getSimpleName());
    }

    /**
     * Returns the normalized readings of the definition fingerprint: the current value of every
     * writable property as {@code name=value}, in lexicographic order. Both ways of writing a
     * property pass the same setter, so the readings here always agree. Every writable property
     * takes part, so the result depends neither on a subset of fields nor on object identity.
     */
    public List<String> normalizedPropertyReadings(Object builder) {
        List<String> readings = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.kind() == MemberKind.WRITABLE_PROPERTY) {
                readings.add(member.name() + "=" + canonicalValue(readMember(builder, member)));
            }
        }
        return readings;
    }

    /** Canonical form of one value: {@code null} keeps its own literal; others use {@code String.valueOf}. */
    private static String canonicalValue(Object value) {
        if (value == null) {
            return "null";
        }
        return String.valueOf(value);
    }

    /** Invokes a member method shared by the surface method form and {@code putMember};
     * exceptions carry the member name. */
    public Object invoke(Object builder, Method method, Object... args) {
        try {
            return method.invoke(builder, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot invoke '" + method.getName() + "' of "
                    + builderType.getSimpleName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause instanceof RuntimeException runtime ? runtime
                    : new IllegalArgumentException("member '" + method.getName() + "' failed: " + cause, cause);
        }
    }

    private static boolean isGetterName(String name) {
        return name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3))
                || name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2));
    }

    private static boolean isSetterName(String name) {
        return name.length() > 3 && name.startsWith("set") && Character.isUpperCase(name.charAt(3));
    }

    /** {@code getMaxStackSize} → {@code maxStackSize}。 */
    private static String propertyName(String accessorName) {
        String prefix = accessorName.startsWith("is") ? accessorName.substring(0, 2) : accessorName.substring(0, 3);
        String rest = accessorName.substring(prefix.length());
        return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
    }

    /** Maps a Java type to its TypeScript type; declarations and runtime members share this mapping. */
    public static String tsTypeOf(Class<?> type) {
        if (type == Float.class) {
            return "number | null";
        }
        if (type == int.class || type == long.class || type == double.class || type == float.class
                || type == Integer.class || type == Long.class || type == Double.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == String.class || type == CharSequence.class) {
            return "string";
        }
        return "any";
    }

    /** Maps a Java type to its Python type from the same input as TS, so member semantics match. */
    public static String pyTypeOf(Class<?> type) {
        if (type == Float.class) {
            return "float | None";
        }
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) {
            return "int";
        }
        if (type == double.class || type == float.class || type == Double.class) {
            return "float";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "bool";
        }
        if (type == String.class || type == CharSequence.class) {
            return "str";
        }
        return "Any";
    }
}
