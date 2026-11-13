package com.tkisor.nekojs.api.spec;

import com.tkisor.nekojs.api.spec.inject.EntitySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntitySpecTest {

    @Test
    void entitySpecDeclaresAllUnifiedMethods() {
        assertDoesNotThrow(() -> EntitySpec.class.getMethod("neko$getLevel"));
        assertDoesNotThrow(() -> EntitySpec.class.getMethod("neko$kill"));
        assertDoesNotThrow(() -> EntitySpec.class.getMethod("neko$teleport", double.class, double.class, double.class));
        assertDoesNotThrow(() -> EntitySpec.class.getMethod("neko$remove"));
    }

    @Test
    void ambiguousMethodsExcludedFromSpec() {
        // getId, getX, getY and getZ are not in the spec: they collide with native zero-argument methods on NF,
        // and Graal cannot dispatch them
        assertThrows(NoSuchMethodException.class,
            () -> EntitySpec.class.getMethod("neko$getId"));
        assertThrows(NoSuchMethodException.class,
            () -> EntitySpec.class.getMethod("neko$getX"));
        assertThrows(NoSuchMethodException.class,
            () -> EntitySpec.class.getMethod("neko$getY"));
        assertThrows(NoSuchMethodException.class,
            () -> EntitySpec.class.getMethod("neko$getZ"));
    }

    @Test
    void allMethodsAreDefaultWithSentinel() throws Exception {
        var instance = new EntitySpec() {};
        for (java.lang.reflect.Method method : EntitySpec.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("neko$")) continue;
            assertTrue(method.isDefault(), method.getName() + " should be default");
            Throwable root = null;
            try {
                invokeDefault(method, instance);
            } catch (Throwable t) {
                root = t;
                while (root.getCause() != null && root != root.getCause()) root = root.getCause();
            }
            assertTrue(root instanceof UnsupportedOperationException,
                    method.getName() + " should throw UnsupportedOperationException");
        }
    }

    private static void invokeDefault(java.lang.reflect.Method m, Object instance) throws Throwable {
        Object[] args = new Object[m.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> t = m.getParameterTypes()[i];
            if (t == int.class) args[i] = 0;
            else if (t == boolean.class) args[i] = false;
            else if (t == long.class) args[i] = 0L;
            else if (t == float.class) args[i] = 0f;
            else if (t == double.class) args[i] = 0.0;
            else args[i] = null;
        }
        m.invoke(instance, args);
    }
}
