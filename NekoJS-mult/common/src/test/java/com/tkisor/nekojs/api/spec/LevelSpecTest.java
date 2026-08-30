package com.tkisor.nekojs.api.spec;

import com.tkisor.nekojs.api.spec.inject.LevelSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LevelSpecTest {

    @Test
    void levelSpecDeclaresAllUnifiedMethods() {
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$getBlockState", int.class, int.class, int.class));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$getId"));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$setBlock", int.class, int.class, int.class, Object.class));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$getTime"));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$setTime", long.class));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$getPlayers"));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$isRaining"));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$setRaining", boolean.class));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$isDay"));
        assertDoesNotThrow(() -> LevelSpec.class.getMethod("neko$data"));
    }

    @Test
    void allMethodsAreDefaultWithSentinel() throws Exception {
        var instance = new LevelSpec() {};
        for (java.lang.reflect.Method method : LevelSpec.class.getDeclaredMethods()) {
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
