package com.tkisor.nekojs.api.spec;

import com.tkisor.nekojs.api.spec.inject.MutableComponentSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MutableComponentSpecTest {

    @Test
    void mutableComponentSpecDeclaresAllUnifiedMethods() {
        // --- colors (16, zero-arg) ---
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$black"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$darkBlue"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$darkGreen"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$darkAqua"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$darkRed"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$darkPurple"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$gold"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$gray"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$darkGray"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$blue"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$green"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$aqua"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$red"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$lightPurple"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$yellow"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$white"));

        // --- color helpers ---
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$noColor"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$hasStyle"));

        // --- styles (overloaded pairs: zero-arg + Boolean) ---
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$bold"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$bold", Boolean.class));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$italic"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$italic", Boolean.class));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$underlined"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$underlined", Boolean.class));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$strikethrough"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$strikethrough", Boolean.class));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$obfuscated"));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$obfuscated", Boolean.class));

        // --- other ---
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$insertion", String.class));
        assertDoesNotThrow(() -> MutableComponentSpec.class.getMethod("neko$font", String.class));
    }

    @Test
    void allMethodsAreDefaultWithSentinel() throws Exception {
        Object instance = new MutableComponentSpec() {};
        for (var method : MutableComponentSpec.class.getDeclaredMethods()) {
            if (!method.getName().startsWith("neko$")) continue;
            assertTrue(method.isDefault(),
                method.getName() + " should be default (sentinel)");
            // 调用哨兵 default 体应抛 UnsupportedOperationException（或被反射包裹）
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
        // default 方法的反射调用：构造正确的参数数组
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
