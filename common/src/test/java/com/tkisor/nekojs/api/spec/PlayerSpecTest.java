package com.tkisor.nekojs.api.spec;

import com.tkisor.nekojs.api.spec.inject.PlayerSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerSpecTest {

    @Test
    void playerSpecDeclaresAllUnifiedMethods() {
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$isOp"));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$give", Object.class));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$setGamemode", String.class));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$getGamemode"));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$addXpLevels", int.class));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$addXpPoints", int.class));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$getXpLevel"));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$setXpLevel", int.class));
        assertDoesNotThrow(() -> PlayerSpec.class.getMethod("neko$kick", Object.class));
    }

    @Test
    void ambiguousMethodsExcludedFromSpec() {
        // neko$addXp(int) is removed from the spec because its meaning is ambiguous: NF adds levels, CR adds points
        assertThrows(NoSuchMethodException.class,
            () -> PlayerSpec.class.getMethod("neko$addXp", int.class));
        // isCreative and sendMessage do not enter the spec: they collide with native zero-argument methods
        assertThrows(NoSuchMethodException.class,
            () -> PlayerSpec.class.getMethod("neko$isCreative"));
        assertThrows(NoSuchMethodException.class,
            () -> PlayerSpec.class.getMethod("neko$sendMessage", Object.class));
    }

    @Test
    void allMethodsAreDefaultWithSentinel() throws Exception {
        Object instance = new PlayerSpec() {};
        for (var method : PlayerSpec.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("neko$")) continue;
            assertTrue(method.isDefault(),
                method.getName() + " should be default (sentinel)");
            // Calling the sentinel default body must throw UnsupportedOperationException (or the reflection wrapper of it)
            Throwable root = null;
            try {
                invokeDefault(method, instance);
            } catch (Throwable t) {
                root = t;
                while (root.getCause() != null && root != root.getCause()) root = root.getCause();
            }
            assertTrue(root instanceof UnsupportedOperationException,
                method.getName() + " should throw UnsupportedOperationException, got " + root);
        }
    }

    private static void invokeDefault(java.lang.reflect.Method m, Object instance) throws Throwable {
        // Reflective call of a default method: build the argument array with the right types
        Object[] args = new Object[m.getParameterCount()];
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            args[i] = params[i] == int.class ? 0
                    : params[i] == boolean.class ? false
                    : null;
        }
        m.invoke(instance, args);
    }
}
