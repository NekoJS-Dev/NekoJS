package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralizes member reflection for Java objects exposed to JS, including name collection, spelling suggestions, and annotation-driven name remapping.
 * Shared by load-time static validators ({@code GlobalBindingMemberValidator}, {@code EventCallbackSourceValidator}),
 * offline tools ({@link MemberVisibilityQuery}), and runtime remapping ({@code NekoJSMemberRemapper}),
 * eliminating redundant reflection logic previously scattered across EventProxy, MemberVisibilityQuery, and NekoJSMemberRemapper.
 *
 * <p>All results collected per {@link Class} are cached in a {@link ConcurrentHashMap}; reflection runs only once per class.
 *
 * <p>This class does not handle runtime member access interception—event objects use native GraalJS host access,
 * and existence validation is performed during script loading by static scanners.
 */
public final class JavaMemberIndex {

    private JavaMemberIndex() {}

    private static final int SUGGEST_MAX_DISTANCE = 3;
    private static final Map<Class<?>, Set<String>> PROPERTY_MEMBERS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Set<String>> ALL_MEMBERS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, ExposedMembers> EXPOSED_MEMBERS_CACHE = new ConcurrentHashMap<>();

    // ==================== Typed member index (for chained expression resolution) ====================

    /**
     * Maintains a typed member index for overloads: methods are grouped by their JS-visible name while retaining all overloads.
     * Getter properties ({@code getX→x}/{@code isX→x}/{@code hasX→x}) and public instance fields
     * are grouped separately. Names are normalized via {@link #remapName} (respecting {@code @HideFromJS}/
     * {@code @Remap}/{@code @RemapByPrefix}), consistent with the exposed names in runtime {@code NekoJSMemberRemapper}.
     *
     * <p>Unlike {@link #propertyMembersOf} (a collection of names that ignores remaps and overload folding),
     * this index retains the {@code Type} of each member, allowing chained member access (e.g., {@code e.getPlayer().getServer()})
     * to be resolved by inferring return types across each hop.
     */
    public record ExposedMembers(
            Map<String, List<Method>> methods,
            Map<String, List<Method>> propertyGetters,
            Map<String, List<Field>> fields) {

        public ExposedMembers {
            methods = Map.copyOf(methods);
            propertyGetters = Map.copyOf(propertyGetters);
            fields = Map.copyOf(fields);
        }

        public boolean hasMember(String name) {
            return methods.containsKey(name) || propertyGetters.containsKey(name) || fields.containsKey(name);
        }

        /**
         * Filter method invocation candidates: filters fixed-parameter or varargs overloads by argument count.
         * Returns a conservative union of return types for all candidates, rather than selecting a single overload.
         */
        public List<Type> callReturnTypes(String name, int argCount) {
            List<Method> candidates = methods.get(name);
            if (candidates == null) return List.of();
            List<Type> returns = new java.util.ArrayList<>();
            for (Method m : candidates) {
                int fixed = m.getParameterCount();
                boolean varargs = m.isVarArgs();
                if (varargs ? argCount >= fixed - 1 : argCount == fixed) {
                    returns.add(m.getGenericReturnType());
                }
            }
            return returns;
        }

        /** Return property access candidates: types from getter properties and public instance fields with the same name. */
        public List<Type> propertyTypes(String name) {
            List<Type> types = new java.util.ArrayList<>();
            List<Method> getters = propertyGetters.get(name);
            if (getters != null) {
                for (Method g : getters) types.add(g.getGenericReturnType());
            }
            List<Field> fieldList = fields.get(name);
            if (fieldList != null) {
                for (Field f : fieldList) types.add(f.getGenericType());
            }
            return types;
        }
    }

    public static ExposedMembers exposedMembersOf(Class<?> clazz) {
        return EXPOSED_MEMBERS_CACHE.computeIfAbsent(clazz, JavaMemberIndex::collectExposedMembers);
    }

    private static ExposedMembers collectExposedMembers(Class<?> clazz) {
        Map<String, List<Method>> methods = new java.util.TreeMap<>();
        Map<String, List<Method>> propertyGetters = new java.util.TreeMap<>();
        Map<String, List<Field>> fields = new java.util.TreeMap<>();
        for (Method m : clazz.getMethods()) {
            if (isHiddenOrInternal(m)) continue;
            String jsName = remapName(m, HIDE_MARKER, m.getName());
            if (!isExposedName(m, jsName)) continue;
            methods.computeIfAbsent(jsName, ignored -> new java.util.ArrayList<>()).add(m);
            if (m.getParameterCount() == 0) {
                String remappedProp = propertyName(jsName);
                String originalProp = propertyName(m.getName());
                String jsProp = remappedProp != null
                        ? remappedProp
                        : originalProp == null ? null : remapName(m, HIDE_MARKER, originalProp);
                if (jsProp != null && !HIDE_MARKER.equals(jsProp)) {
                    propertyGetters.computeIfAbsent(jsProp, ignored -> new java.util.ArrayList<>()).add(m);
                }
            }
        }
        for (Field f : clazz.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (f.getDeclaringClass() == Object.class) continue;
            String jsName = remapName(f, HIDE_MARKER, f.getName());
            if (jsName == null) continue;
            fields.computeIfAbsent(jsName, ignored -> new java.util.ArrayList<>()).add(f);
        }
        return new ExposedMembers(methods, propertyGetters, fields);
    }

    private static final String HIDE_MARKER = "\u0000nekojs-hide";

    private static boolean isHiddenOrInternal(Method m) {
        String name = m.getName();
        if ("getClass".equals(name)) return true;
        if (m.getDeclaringClass() == Object.class) return true;
        if (m.isBridge() || m.isSynthetic()) return true;
        return false;
    }

    /**
     * Internal prefixed methods remain hidden unless an annotation explicitly maps them to a JS name.
     * The remap must be evaluated before this check because interface-injected methods are reflected
     * from the Minecraft host class, while their {@code @RemapByPrefix} lives on the injected interface.
     */
    private static boolean isExposedName(Method method, String jsName) {
        return jsName != null && !jsName.isBlank() && !HIDE_MARKER.equals(jsName)
                && (!method.getName().startsWith("neko$") || !jsName.startsWith("neko$"));
    }

    /** Resolve return {@code Type} into a set of classes for member lookup; return empty when indeterminate (caller marks as unknown). */
    public static List<Class<?>> typeClasses(Type type) {
        List<Class<?>> result = new java.util.ArrayList<>();
        if (type instanceof Class<?> c) {
            if (c == void.class || c.isPrimitive()) return List.of();
            result.add(c);
        } else if (type instanceof java.lang.reflect.ParameterizedType pt
                && pt.getRawType() instanceof Class<?> c) {
            result.add(c);
        } else if (type instanceof java.lang.reflect.TypeVariable<?> tv) {
            for (Type bound : tv.getBounds()) {
                result.addAll(typeClasses(bound));
            }
        } else if (type instanceof java.lang.reflect.WildcardType wt) {
            for (Type upper : wt.getUpperBounds()) {
                result.addAll(typeClasses(upper));
            }
        }
        return result;
    }

    // ==================== Member name set (for load-time validation) ====================

    /**
     * Collection of member names for read-only event object callback arguments: includes public method names (as-is),
     * parameterless getter property names ({@code getX→x}, {@code isX→x}, {@code hasX→x}), and public field names.
     * Excludes {@code getClass}, names with {@code neko$} prefix, and methods declared in {@code Object.class}.
     */
    public static Set<String> propertyMembersOf(Class<?> clazz) {
        return PROPERTY_MEMBERS_CACHE.computeIfAbsent(clazz, JavaMemberIndex::collectPropertyMembers);
    }

    /**
     * Broad collection of member names for global bindings ({@code Utils}, {@code Items}, {@code Platform}): includes public method names (as-is),
     * getter property names, and public field names. Global bindings primarily involve parameterized method calls;
     * false-positive method identification is worse than false-negative, so a union is used.
     *
     * <p><b>Historical asymmetry</b> (do not "fix", or validation error sets will change): unlike {@link #collectPropertyMembers},
     * this method does <b>not</b> exclude methods declared in {@code Object.class} and infers property names for <b>all</b> methods (including parameterized ones).
     */
    public static Set<String> allMembersOf(Class<?> clazz) {
        return ALL_MEMBERS_CACHE.computeIfAbsent(clazz, JavaMemberIndex::collectAllMembers);
    }

    /** Spelling suggestion based on known member set: returns the nearest member with Levenshtein distance ≤ {@value #SUGGEST_MAX_DISTANCE}, or null if none. */
    public static String suggestMember(Set<String> members, String key) {
        return suggest(key, members);
    }

    /** Spelling suggestion based on a class's {@link #propertyMembersOf}. */
    public static String suggestMember(Class<?> clazz, String key) {
        return suggest(key, propertyMembersOf(clazz));
    }

    /** Standard error message for unknown members (includes Did-You-Mean suggestions). */
    public static String unknownMemberMessage(Class<?> clazz, String key) {
        String suggest = suggestMember(clazz, key);
        return "Event '" + clazz.getSimpleName() + "' has no member '" + key + "'." +
                (suggest != null ? " Did you mean '" + suggest + "'?" : "");
    }

    /** Infer property type: the return type of {@code getEntity()} for {@code entity}, or the type of a public field with the same name; null if none. */
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

    // ==================== Annotation-driven member name remapping (shared primitives) ====================

    /**
     * Cache key for remapName. Both markers must be included: each caller's markers have different semantics
     * (visibility queries pass null/original name; runtime remapper passes {@code MemberRemapper.HIDE_MEMBER}/
     * {@code MemberRemapper.FALL_THROUGH}; ExposedMembers uses {@code HIDE_MARKER}).
     * Caching solely by member would bleed one caller's markers into another. Results are naturally bounded by member
     * (at most a few caller combinations per member).
     */
    private record RemapKey(Member member, String hideMarker, String fallThroughMarker) {}

    private static final Map<RemapKey, String> REMAP_NAME_CACHE = new ConcurrentHashMap<>();

    /**
     * Perform side-effect-free member name remapping, ordered by priority:
     * {@code @HideFromJS} (member or class level) &gt; {@code @Remap} (member level) &gt;
     * {@code @RemapByPrefix} (member level) &gt; {@code @RemapByPrefix} (class level) &gt; original name.
     *
     * <p>Callers can maintain their own miss semantics using markers, avoiding treating the original name as the terminal result of the Graal remapper chain.
     * @param hideMarker The value returned when {@code @HideFromJS} hits. {@link MemberVisibilityQuery} passes {@code null}
     *                   (to exclude from the visible set); {@code NekoJSMemberRemapper} passes
     *                   the {@code MemberRemapper.HIDE_MEMBER} constant (satisfying the graal.mod.api SPI contract).
     * @param fallThroughMarker The value returned when no remapping hits. Visibility queries pass {@link Member#getName()};
     *                          Graal remapper chains pass {@code MemberRemapper.FALL_THROUGH}.
     */
    public static @Nullable String remapName(Member member, String hideMarker, String fallThroughMarker) {
        RemapKey key = new RemapKey(member, hideMarker, fallThroughMarker);
        String cached = REMAP_NAME_CACHE.get(key);
        if (cached != null) return cached;
        // Do not cache null results (visibility queries excluding hidden members): ConcurrentHashMap does not allow null values,
        // and this path only occurs during script loading, not on the runtime remapper hot path.
        String result = computeRemapName(member, hideMarker, fallThroughMarker);
        if (result != null) {
            String raced = REMAP_NAME_CACHE.putIfAbsent(key, result);
            if (raced != null) return raced;
        }
        return result;
    }

    private static @Nullable String computeRemapName(Member member, String hideMarker, String fallThroughMarker) {
        AccessibleObject ao = (AccessibleObject) member;

        if (ao.isAnnotationPresent(HideFromJS.class)
                || member.getDeclaringClass().isAnnotationPresent(HideFromJS.class)) {
            return hideMarker;
        }

        Remap remap = ao.getAnnotation(Remap.class);
        if (remap != null) {
            return remap.value();
        }

        String original = member.getName();

        RemapByPrefix memberPrefix = ao.getAnnotation(RemapByPrefix.class);
        if (memberPrefix != null) {
            String stripped = findAndRemovePrefix(original, memberPrefix.value());
            if (stripped != null) return stripped;
        }

        // Class-level annotations include "interface inheritance": methods injected by mixin/interface-injection reflect the host class
        // (e.g., MinecraftServer.neko$data, declaringClass=MinecraftServer), while @RemapByPrefix
        // is annotated on the injected interface (ServerExtension)—runtime Graal hits the annotation via the interface method path,
        // so probe must do the same via the host class method path, or the declaration (neko$data) and runtime JS name (data) will desynchronize.
        RemapByPrefix classPrefix = findRemapByPrefixOnClassOrInterfaces(member.getDeclaringClass());
        if (classPrefix != null) {
            String stripped = findAndRemovePrefix(original, classPrefix.value());
            if (stripped != null) return stripped;
        }

        return fallThroughMarker;
    }

    /** Look up {@code @RemapByPrefix} on the class and its direct/indirect interfaces (depth-first; returns on first hit). */
    private static @Nullable RemapByPrefix findRemapByPrefixOnClassOrInterfaces(Class<?> cls) {
        RemapByPrefix direct = cls.getAnnotation(RemapByPrefix.class);
        if (direct != null) return direct;
        for (Class<?> iface : cls.getInterfaces()) {
            RemapByPrefix found = findRemapByPrefixOnClassOrInterfaces(iface);
            if (found != null) return found;
        }
        return null;
    }

    // ==================== Internal primitives ====================

    private static @Nullable String findAndRemovePrefix(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.length() > prefix.length() && name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return null;
    }

    private static Set<String> collectPropertyMembers(Class<?> clazz) {
        Set<String> members = new LinkedHashSet<>();
        for (Method m : clazz.getMethods()) {
            if ("getClass".equals(m.getName()) || m.getDeclaringClass() == Object.class) continue;
            String name = remapName(m, null, m.getName());
            if (!isExposedName(m, name)) continue;
            members.add(name);
            if (m.getParameterCount() == 0) {
                String prop = propertyName(name);
                if (prop != null) members.add(prop);
            }
        }
        for (Field f : clazz.getFields()) {
            members.add(f.getName());
        }
        return members;
    }

    private static Set<String> collectAllMembers(Class<?> clazz) {
        Set<String> members = new LinkedHashSet<>();
        for (Method m : clazz.getMethods()) {
            if ("getClass".equals(m.getName())) continue;
            String name = remapName(m, null, m.getName());
            if (!isExposedName(m, name)) continue;
            members.add(name);
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

    private static String suggest(String key, Set<String> members) {
        if (key == null || key.isEmpty()) return null;
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String member : members) {
            int d = levenshtein(key, member);
            if (d < bestDist) {
                bestDist = d;
                best = member;
            }
        }
        return bestDist <= SUGGEST_MAX_DISTANCE ? best : null;
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
}
