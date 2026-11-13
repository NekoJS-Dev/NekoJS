package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public member contract of a typed Builder. The member catalog is <b>reflected</b> from the Builder
 * class itself, so one contract input drives {@link BuilderSurface} (the runtime members), the
 * definition fingerprint, the TS/Python declarations and the contract/golden files, with no
 * hand-written second member table.
 *
 * <p>Classification is deterministic and the output is sorted by member name:
 * <ul>
 *   <li><b>Writable property</b>: {@code setXxx(T)} exists together with a matching
 *       {@code getXxx():T} (or {@code isXxx()} for a boolean). The script form {@code b.xxx = v}
 *       (through ProxyObject putMember) and an explicit {@code b.setXxx(v)} call (through the member
 *       table) reach <b>the same</b> setter {@link Method} and run the same
 *       validation/normalization/fingerprint path. This is the single write path.</li>
 *   <li><b>Read-only property</b>: has a getter and no setter, including the final identity field
 *       {@code id}; final and read-only members are the only exception.</li>
 *   <li><b>Method</b>: any other public instance method, including the setters themselves, so the
 *       explicit setter form stays callable.</li>
 * </ul>
 *
 * <p>Engine lifecycle members ({@code build}, {@code get}, {@code handleAdditionalObjects}: the lazy
 * build points that collect first and build later) and the internal tag members do not appear in the
 * script-facing API or in the declarations. Objects are created only during the node Adapter's
 * registration pass; a script that calls build early during collection would break collect-first,
 * build-later. static members are not part of the instance side. Reflection results are cached per
 * class, since builder classes do not change.
 */
public final class RegistryBuilderContract {

    /**
     * Engine lifecycle and internal members: they do not appear in the script-facing API or in the
     * declarations. {@code entityType()} builds the entity early through {@code get()}, which breaks
     * collect-first, build-later, so it belongs with build/get as a lifecycle member.
     */
    private static final Set<String> ENGINE_SEAM = Set.of(
            "build", "get", "handleAdditionalObjects", "equals", "hashCode", "toString",
            "getLocation", "getTagRegistry", "getTagTargets", "entityType", "buildProperties");

    private static final Map<Class<?>, RegistryBuilderContract> CACHE = new ConcurrentHashMap<>();

    /** Member kind: writable property, read-only property, or method (including the explicit setters). */
    public enum MemberKind { WRITABLE_PROPERTY, READ_ONLY_PROPERTY, METHOD }

    /** One contract member. A method member can hold overloads that share a name, such as
     * {@code PotionBuilder.effect}. */
    public record Member(
            String name, MemberKind kind, Class<?> valueType, Method getter, Method setter, List<Method> overloads) {

        Member(String name, MemberKind kind, Class<?> valueType, Method getter, Method setter) {
            this(name, kind, valueType, getter, setter, List.of());
        }

        /** TS declaration type: a deterministic mapping, with any for unknown types. */
        public String tsType() {
            return tsType(Map.of());
        }

        String tsType(Map<Class<?>, String> exportedNames) {
            if (getter != null && Map.class.isAssignableFrom(valueType)) return tsTypeOf(getter.getGenericReturnType());
            if (valueType != null && !overloads.isEmpty() && RegistryObjectBuilder.class.isAssignableFrom(valueType)) {
                String name = exportedNames.getOrDefault(valueType, valueType.getSimpleName());
                return name + " & ((consumer: (b: " + name + ") => void) => void)"
                        + (nullable() ? " | null" : "");
            }
            return tsTypeOf(valueType) + (nullable() ? " | null" : "");
        }

        /** Python declaration type, derived from the same input as TS and with the same member semantics. */
        public String pyType() {
            return pyType(Map.of());
        }

        String pyType(Map<Class<?>, String> exportedNames) {
            if (getter != null && Map.class.isAssignableFrom(valueType)) return pyTypeOf(getter.getGenericReturnType());
            if (valueType != null && !overloads.isEmpty() && RegistryObjectBuilder.class.isAssignableFrom(valueType)) {
                return exportedNames.getOrDefault(valueType, valueType.getSimpleName())
                        + " | Callable[..., None]" + (nullable() ? " | None" : "");
            }
            return pyTypeOf(valueType) + (nullable() ? " | None" : "");
        }

        private boolean nullable() {
            return getter != null && getter.isAnnotationPresent(com.tkisor.nekojs.api.annotation.ScriptNullable.class);
        }
    }

    private final Class<?> builderType;
    private final Map<String, Member> members;
    /** A final identity field, such as {@code id} from the base class: it becomes a read-only member. */
    private final Map<String, Field> readOnlyFields;

    private RegistryBuilderContract(Class<?> builderType) {
        this.builderType = builderType;
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, Method> setters = new LinkedHashMap<>();
        // Method members are grouped by name into an <b>overload list</b>: overloads that share a name but
        // differ in parameters all stay callable, like the 3-argument and 5-argument forms of
        // PotionBuilder.effect. Collecting a single Method would lose overloads depending on the
        // getMethods() order and make some script forms unreachable.
        Map<String, List<Method>> rest = new LinkedHashMap<>();
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Field field : builderType.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            fields.put(field.getName(), field);
        }
        for (Method method : builderType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            String name = method.getName();
            if (ENGINE_SEAM.contains(name) || method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (isGetterName(name) && method.getParameterCount() == 0) {
                getters.put(propertyName(name), method);
            } else if (isSetterName(name) && method.getParameterCount() == 1) {
                setters.put(propertyName(name), method);
            } else {
                rest.computeIfAbsent(name, k -> new ArrayList<>()).add(method);
            }
        }
        Map<String, Member> result = new LinkedHashMap<>();
        Map<String, List<Method>> methodOverloads = new LinkedHashMap<>();
        // Final identity fields such as the base class id enter the catalog as read-only members
        fields.forEach((name, field) -> result.putIfAbsent(name,
                new Member(name, MemberKind.READ_ONLY_PROPERTY, field.getType(), null, null)));
        getters.keySet().forEach(property -> {
            Method setter = setters.get(property);
            Method getter = getters.get(property);
            if (setter != null) {
                result.put(property, new Member(property, MemberKind.WRITABLE_PROPERTY, setter.getParameterTypes()[0], getter, setter));
            } else {
                result.put(property, new Member(property, MemberKind.READ_ONLY_PROPERTY, getter.getReturnType(), getter, null));
            }
        });
        // The explicit setter form b.setXxx(v) stays as a method member, backed by the same Method as the
        // property write
        setters.forEach((property, setter) -> methodOverloads.computeIfAbsent(setter.getName(), k -> new ArrayList<>()).add(setter));
        rest.forEach((name, overloads) -> methodOverloads.computeIfAbsent(name, k -> new ArrayList<>()).addAll(overloads));
        methodOverloads.forEach((name, overloads) -> {
            // getMethods() order is not guaranteed: the overload list is sorted by parameter count descending,
            // then by signature string, so the contract, declarations and golden files come out
            // deterministically. Script calls resolve by argument count and do not depend on this order.
            List<Method> stable = overloads.stream()
                    .sorted(Comparator.comparingInt((Method m) -> -m.getParameterCount())
                            .thenComparing(m -> m.getParameterTypes().length == 0 ? "" : m.toGenericString()))
                    .toList();
            Member property = result.get(name);
            if (property != null && property.getter() != null
                    && RegistryObjectBuilder.class.isAssignableFrom(property.getter().getReturnType())) {
                result.put(name, new Member(name, property.kind(), property.valueType(), property.getter(), property.setter(), stable));
            } else {
                result.putIfAbsent(name, new Member(name, MemberKind.METHOD, null, null, null, stable));
            }
        });
        Map<String, Member> sorted = new LinkedHashMap<>();
        result.values().stream()
                .sorted(Comparator.comparing(Member::name))
                .forEach(member -> sorted.put(member.name(), member));
        this.members = sorted;
        this.readOnlyFields = Map.copyOf(fields);
    }

    /** Returns the contract for a builder class, creating and caching it on first use. */
    public static RegistryBuilderContract of(Class<? extends RegistryObjectBuilder<?>> builderType) {
        return CACHE.computeIfAbsent(builderType, RegistryBuilderContract::new);
    }

    /** The Builder class the contract was reflected from. */
    public Class<?> builderType() {
        return builderType;
    }

    /** All members, sorted by name. */
    public List<Member> members() {
        return List.copyOf(members.values());
    }

    /** Returns the member with this name, or null if there is none. */
    public Member member(String name) {
        return members.get(name);
    }

    /** Reads the current value of a property member, through its getter or its final identity field. */
    public Object readMember(Object builder, Member member) {
        try {
            if (member.getter() != null) {
                return member.getter().invoke(builder);
            }
            Field field = readOnlyFields.get(member.name());
            if (field != null) {
                return field.get(builder);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read builder member '" + member.name() + "'", e);
        }
        throw new IllegalArgumentException("'" + member.name() + "' is not a readable property of " + builderType.getSimpleName());
    }

    /** The member name catalog, used for diagnostics and completion. */
    public List<String> memberNames() {
        return List.copyOf(members.keySet());
    }

    /**
     * Normalized member readings for the definition fingerprint: the current value of every property
     * is read in name order as {@code name=value}. Both write forms go through the same setter, so the
     * readings always match here.
     *
     * <p><b>Normalizing object-valued properties</b>: when the value is a
     * {@link RegistryObjectBuilder} (such as the {@code item} sub-builder of BlockBuilder),
     * {@code String.valueOf} is not used, because it embeds {@code @<identityHashCode>} and the
     * fingerprint of one declaration would drift between instances and between launches. The
     * sub-builder is expanded recursively into a <b>sub-fingerprint</b> (simple type name + id +
     * readings of all its writable properties). Nested cycles are cut off and marked with
     * {@code cycle}; the bundled builders have no cycles today, but no third-party builder is
     * promised to be acyclic. Suppression (null) and presence (a sub-fingerprint) remain
     * distinguishable.
     */
    public List<String> normalizedPropertyReadings(Object builder) {
        return normalizedReadingsOf(builder, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private List<String> normalizedReadingsOf(Object builder, Set<Object> seen) {
        List<String> readings = new ArrayList<>();
        for (Member member : members.values()) {
            if (member.kind() == MemberKind.WRITABLE_PROPERTY) {
                Object value = readMember(builder, member);
                readings.add(member.name() + "=" + canonicalValue(value, seen));
            }
        }
        return readings;
    }

    /** Canonical string for a single property value: null for null, the sub-fingerprint of a nested builder
     * (recursive), otherwise String.valueOf. */
    private String canonicalValue(Object value, Set<Object> seen) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return new com.google.gson.Gson().toJson(value);
        }
        if (value instanceof RegistryObjectBuilder<?> nested) {
            if (!seen.add(nested)) {
                return "cycle";
            }
            String inner = String.join(";", RegistryBuilderContract.of(builderClassOf(nested)).normalizedReadingsOf(nested, seen));
            seen.remove(nested);
            return "builder[" + nested.getClass().getSimpleName() + "(" + nested.id + ")" + inner + "]";
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RegistryObjectBuilder<?>> builderClassOf(Object builder) {
        return (Class<? extends RegistryObjectBuilder<?>>) builder.getClass();
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

    /** Maps Java types to TypeScript; boxed Float retains the nullable sound-range contract. */
    public static String tsTypeOf(Class<?> type) {
        if (type == com.tkisor.nekojs.wrapper.registry.FoodBuilderJS.class) {
            return "FoodBuilder";
        }
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

    /** Preserves JSON and texture map value types in reflected builder declarations. */
    public static String tsTypeOf(java.lang.reflect.Type type) {
        if (type instanceof java.lang.reflect.ParameterizedType map && map.getRawType() == Map.class) {
            return "Record<string, " + tsTypeOf(map.getActualTypeArguments()[1]) + ">";
        }
        return type instanceof Class<?> value ? tsTypeOf(value) : "any";
    }

    public static String pyTypeOf(java.lang.reflect.Type type) {
        if (type instanceof java.lang.reflect.ParameterizedType map && map.getRawType() == Map.class) {
            return "dict[str, " + pyTypeOf(map.getActualTypeArguments()[1]) + "]";
        }
        return type instanceof Class<?> value ? pyTypeOf(value) : "Any";
    }

    /** Maps the same Java types to Python, preserving nullable boxed Float independently of primitive float. */
    public static String pyTypeOf(Class<?> type) {
        if (type == com.tkisor.nekojs.wrapper.registry.FoodBuilderJS.class) {
            return "FoodBuilder";
        }
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
