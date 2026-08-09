package com.tkisor.nekojs.api.spec;

import com.tkisor.nekojs.api.spec.inject.ItemStackSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemStackSpecTest {

    @Test
    void itemStackSpecDeclaresAllUnifiedMethods() {
        assertDoesNotThrow(() -> ItemStackSpec.class.getMethod("neko$withCount", int.class));
        assertDoesNotThrow(() -> ItemStackSpec.class.getMethod("neko$hasEnchantment", String.class, int.class));
        assertDoesNotThrow(() -> ItemStackSpec.class.getMethod("neko$isUnbreakable"));
        assertDoesNotThrow(() -> ItemStackSpec.class.getMethod("neko$setUnbreakable", boolean.class));
        assertDoesNotThrow(() -> ItemStackSpec.class.getMethod("neko$matches", Object.class));
    }

    @Test
    void ambiguousMethodsExcludedFromSpec() {
        // copy / getItem 因两平台原生同名零参碰撞而不在 spec 中
        assertThrows(NoSuchMethodException.class,
            () -> ItemStackSpec.class.getMethod("neko$copy"));
        assertThrows(NoSuchMethodException.class,
            () -> ItemStackSpec.class.getMethod("neko$getItem"));
    }

    @Test
    void allMethodsAreDefaultWithSentinel() throws Exception {
        var instance = new ItemStackSpec() {};
        for (java.lang.reflect.Method method : ItemStackSpec.class.getDeclaredMethods()) {
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
