package com.tkisor.nekojs.api;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;

/**
 * Member visibility query tool.
 * <p>
 * An external mod (a .d.ts generator, for example) can use this class to query which members of any
 * Java class NekoJS exposes to JavaScript, and their visibility.
 * Results are wrapped in {@link JSBinding}, which makes it easy to tell public from non-public
 * members and to read the other modifiers.
 * </p>
 */
public final class MemberVisibilityQuery {

    private MemberVisibilityQuery() {}

    // ==================== Data structures ====================

    /**
     * Wraps the JS-side binding information and metadata of a member.
     *
     * @param jsName the name exposed to JS after remapping
     * @param member the original Java reflection member
     * @param <T>    the concrete Member type (Method, Field, Constructor)
     */
    public record JSBinding<T extends Member>(String jsName, T member) {

        public boolean isPublic() {
            return Modifier.isPublic(member.getModifiers());
        }

        public boolean isProtected() {
            return Modifier.isProtected(member.getModifiers());
        }

        public boolean isPrivate() {
            return Modifier.isPrivate(member.getModifiers());
        }

        public boolean isStatic() {
            return Modifier.isStatic(member.getModifiers());
        }

        /** Returns whether the field is final (can mark the .d.ts property readonly). */
        public boolean isFinal() {
            return Modifier.isFinal(member.getModifiers());
        }
    }

    // ==================== Class level ====================

    /**
     * Determines whether a class is completely invisible on the JS side.
     */
    public static boolean isClassVisible(Class<?> clazz) {
        return !clazz.isAnnotationPresent(HideFromJS.class);
    }

    // ==================== Batch queries (walking the inheritance chain) ====================

    /**
     * Returns every instance method of the class that is exposed to JS, including inherited and
     * non-public ones. Keys are the remapped names. On a name collision, the subclass method
     * overrides the superclass method.
     *
     * @param clazz the Java class to query
     * @return an immutable map from remapped method name to JSBinding
     */
    public static Map<String, JSBinding<Method>> getVisibleMethods(Class<?> clazz) {
        Map<String, JSBinding<Method>> result = new LinkedHashMap<>();

        List<Class<?>> hierarchy = getHierarchy(clazz);

        for (Class<?> cls : hierarchy) {
            for (Method m : cls.getDeclaredMethods()) {
                // Skip bridge and synthetic methods.
                if (m.isBridge() || m.isSynthetic()) continue;

                String jsName = remapMember(m);
                if (jsName != null) {
                    result.put(jsName, new JSBinding<>(jsName, m));
                }
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns every static field of a class that is exposed to JS, including inherited fields and
     * non-public ones. Keys are the remapped names. A subclass field does not replace a parent
     * field that has the same name.
     *
     * @param clazz the Java class to query
     * @return an immutable map from remapped field name to JSBinding
     */
    public static Map<String, JSBinding<Field>> getVisibleFields(Class<?> clazz) {
        Map<String, JSBinding<Field>> result = new LinkedHashMap<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;

                String jsName = remapMember(f);
                if (jsName != null) {
                    // A subclass field does not replace a parent field of the same name.
                    result.putIfAbsent(jsName, new JSBinding<>(jsName, f));
                }
            }
            current = current.getSuperclass();
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns every constructor of a class that is exposed to JS, including non-public ones.
     *
     * @param clazz the Java class to query
     * @return an immutable list of bindings for the visible constructors
     */
    public static List<JSBinding<Constructor<?>>> getVisibleConstructors(Class<?> clazz) {
        if (!isClassVisible(clazz)) {
            return Collections.emptyList();
        }

        List<JSBinding<Constructor<?>>> result = new ArrayList<>();
        // getDeclaredConstructors() includes private, protected and public constructors.
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (!ctor.isSynthetic()) {
                // Constructors need no remap: the name is always the class simple name, called with new from JS.
                String name = clazz.getSimpleName();
                result.add(new JSBinding<>(name, ctor));
            }
        }
        return Collections.unmodifiableList(result);
    }

    // ==================== Helpers and internals ====================

    /**
     * Returns the superclass chain of a class, from the topmost parent (excluding Object) down to
     * the class itself. Parents come before subclasses so that map overwrites work.
     */
    private static List<Class<?>> getHierarchy(Class<?> clazz) {
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        Collections.reverse(hierarchy);
        return hierarchy;
    }

    /** Delegates to {@link JavaMemberIndex#remapName}: hidden members are dropped, unmapped ones keep their name. */
    private static @Nullable String remapMember(Member member) {
        return JavaMemberIndex.remapName(member, null, member.getName());
    }
}