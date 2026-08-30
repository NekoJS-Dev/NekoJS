package com.tkisor.nekojs.api.spec;

import com.tkisor.nekojs.api.spec.inject.LivingEntitySpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LivingEntitySpecTest {

    @Test
    void livingEntitySpecDeclaresAllUnifiedMethods() {
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$damage", float.class));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$getOffHandItem"));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$getHeadItem"));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$getChestItem"));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$getLegsItem"));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$getFeetItem"));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$addEffect", String.class, int.class, int.class));
        assertDoesNotThrow(() -> LivingEntitySpec.class.getMethod("neko$removeEffect", String.class));
    }

    @Test
    void ambiguousMethodsExcludedFromSpec() {
        // getHealth / setHealth / getMaxHealth / heal / getMainHandItem 因两平台原生同名碰撞而不在 spec 中
        assertThrows(NoSuchMethodException.class,
            () -> LivingEntitySpec.class.getMethod("neko$getHealth"));
        assertThrows(NoSuchMethodException.class,
            () -> LivingEntitySpec.class.getMethod("neko$setHealth", float.class));
        assertThrows(NoSuchMethodException.class,
            () -> LivingEntitySpec.class.getMethod("neko$getMaxHealth"));
        assertThrows(NoSuchMethodException.class,
            () -> LivingEntitySpec.class.getMethod("neko$heal", float.class));
        assertThrows(NoSuchMethodException.class,
            () -> LivingEntitySpec.class.getMethod("neko$getMainHandItem"));
    }

    @Test
    void allMethodsAreDefaultWithSentinel() throws Exception {
        var instance = new LivingEntitySpec() {};
        for (java.lang.reflect.Method method : LivingEntitySpec.class.getDeclaredMethods()) {
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
