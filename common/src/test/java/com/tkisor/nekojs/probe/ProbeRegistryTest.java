package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProbeRegistryTest {
    @Test
    void fallbackIsUsedWithoutThirdPartyProvider() throws Exception {
        resetRegistry();
        ProbeGenerator fallback = generator("fallback");

        ProbeRegistry.setFallback(fallback, "builtin");
        ProbeRegistry.lock();

        assertSame(fallback, ProbeRegistry.getGenerator());
    }

    @Test
    void thirdPartyProviderReplacesFallback() throws Exception {
        resetRegistry();
        ProbeGenerator fallback = generator("fallback");
        ProbeGenerator replacement = generator("replacement");

        ProbeRegistry.setFallback(fallback, "builtin");
        ProbeRegistry.setGenerator(replacement, "plugin");
        ProbeRegistry.lock();

        assertSame(replacement, ProbeRegistry.getGenerator());
        assertEquals(List.of("replacement (plugin)"), ProbeRegistry.getRegistrars());
    }

    @Test
    void multipleThirdPartyProvidersConflict() throws Exception {
        resetRegistry();
        ProbeRegistry.setFallback(generator("fallback"), "builtin");
        ProbeRegistry.setGenerator(generator("first"), "plugin-a");
        ProbeRegistry.setGenerator(generator("second"), "plugin-b");

        assertThrows(IllegalStateException.class, ProbeRegistry::lock);
    }

    @Test
    void registrationAfterLockFails() throws Exception {
        resetRegistry();
        ProbeRegistry.setFallback(generator("fallback"), "builtin");
        ProbeRegistry.lock();

        assertThrows(IllegalStateException.class,
                () -> ProbeRegistry.setGenerator(generator("late"), "plugin"));
    }

    private static ProbeGenerator generator(String name) {
        return new ProbeGenerator() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public GenerateResult generate(com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot snapshot,
                                           java.nio.file.Path outputDir) {
                return GenerateResult.success(0, 0L);
            }
        };
    }

    private static void resetRegistry() throws Exception {
        setField("generator", null);
        setField("fallbackGenerator", null);
        setField("fallbackRegistrar", null);
        setField("locked", false);
        Field registrars = ProbeRegistry.class.getDeclaredField("registrars");
        registrars.setAccessible(true);
        ((List<?>) registrars.get(null)).clear();
    }

    private static void setField(String name, Object value) throws Exception {
        Field field = ProbeRegistry.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
