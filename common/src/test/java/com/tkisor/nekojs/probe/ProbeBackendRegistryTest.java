package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 覆盖 {@link ProbeBackendRegistry}：注册、按 priority 排序、(语言, 名字) 冲突 fail-fast、lock 后禁止注册。
 * 注意：用独立 {@code new ProbeBackendRegistry()} 实例测试，不触碰静态 {@code INSTANCE}。
 */
class ProbeBackendRegistryTest {

    @Test
    void registerAndResolveByLanguage() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        ProbeBackend ts = backend("typescript", "builtin", 0);
        reg.register(ts, "test");

        assertEquals(List.of(ts), reg.backendsFor("typescript"));
        assertTrue(reg.defaultBackend("typescript").get() == ts);
        assertTrue(reg.backend("typescript", "builtin").get() == ts);
        assertEquals(1, reg.languages().size());
        assertTrue(reg.languages().contains("typescript"));
    }

    @Test
    void defaultBackendPicksHighestPriority() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        ProbeBackend low = backend("python", "a", 1);
        ProbeBackend high = backend("python", "b", 10);
        reg.register(low, "test");
        reg.register(high, "test");

        // highest priority first
        assertEquals(high, reg.backendsFor("python").get(0));
        assertEquals(high, reg.defaultBackend("python").get());
    }

    @Test
    void sameLanguageAndNameConflictsAtLock() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        reg.register(backend("typescript", "builtin", 0), "mod-a");
        reg.register(backend("typescript", "builtin", 0), "mod-b"); // duplicate (lang,name)

        assertThrows(IllegalStateException.class, reg::lock);
    }

    @Test
    void differentNamesSameLanguageCoexist() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        reg.register(backend("python", "builtin", 0), "core");
        reg.register(backend("python", "enhanced", 5), "third-party");
        reg.lock(); // no conflict — different names

        assertEquals(2, reg.backendsFor("python").size());
    }

    @Test
    void registerAfterLockFails() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        reg.register(backend("typescript", "builtin", 0), "core");
        reg.lock();

        assertThrows(IllegalStateException.class,
                () -> reg.register(backend("typescript", "late", 0), "late"));
    }

    @Test
    void blankLanguageIdOrNameRejected() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> reg.register(backend("", "x", 0), "test"));
        assertThrows(IllegalArgumentException.class,
                () -> reg.register(backend("typescript", "  ", 0), "test"));
    }

    @Test
    void missingLanguageResolvesEmpty() {
        ProbeBackendRegistry reg = new ProbeBackendRegistry();
        reg.register(backend("typescript", "builtin", 0), "core");
        assertTrue(reg.backendsFor("python").isEmpty());
        assertTrue(reg.defaultBackend("python").isEmpty());
        assertTrue(reg.backend("python", "builtin").isEmpty());
    }

    private static ProbeBackend backend(String languageId, String name, int priority) {
        return new ProbeBackend() {
            @Override public String languageId() { return languageId; }
            @Override public String name() { return name; }
            @Override public int priority() { return priority; }
            @Override
            public ProbeGenerator.GenerateResult generate(ProbeContext ctx) {
                return ProbeGenerator.GenerateResult.success(0, 0L);
            }
        };
    }
}
