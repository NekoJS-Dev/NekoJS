package com.tkisor.nekojs.api.catalog;

import java.lang.reflect.Modifier;

/**
 * Projects one declared zero-argument instance method onto an existing same-name Bean property.
 * The TypeScript renderer applies the rule only while the visible, unedited getter is retained.
 * Java methods, runtime access and other target classes remain unchanged.
 */
public record BeanMethodProjectionCatalogEntry(Class<?> targetType, String methodName, String getterName) {
    public BeanMethodProjectionCatalogEntry {
        if (targetType == null || targetType.isPrimitive() || targetType.isArray() || targetType.isInterface()
                || targetType.isLocalClass() || targetType.getSimpleName().isEmpty() || targetType.getPackageName().isEmpty()
                || methodName == null || methodName.isBlank() || getterName == null || getterName.isBlank()) {
            throw new IllegalArgumentException("[NEKO-4053] Bean method projection rejected: "
                    + "a named Java class, method name and getter name are required");
        }
        try {
            var method = targetType.getDeclaredMethod(methodName);
            var getter = targetType.getMethod(getterName);
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || Modifier.isStatic(getter.getModifiers()) || method.isBridge() || method.isSynthetic()
                    || getter.isBridge() || getter.isSynthetic() || method.getReturnType() == void.class
                    || method.getReturnType() != getter.getReturnType() || methodName.equals(getterName)) {
                throw new IllegalArgumentException("[NEKO-4053] Bean method projection rejected: "
                        + "public instance methods with matching non-void results are required");
            }
        } catch (NoSuchMethodException failure) {
            throw new IllegalArgumentException("[NEKO-4053] Bean method projection rejected: "
                    + "the declared method and visible getter must exist without parameters", failure);
        }
    }
}
