package com.tkisor.nekojs.core.module;

import com.tkisor.nekojs.core.compiler.NekoJavaScriptLanguagePlugin;
import com.tkisor.nekojs.core.compiler.NekoModuleMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinModuleTransformCacheTest {
    private static final Path FILE = Path.of("probe.js");
    private static final NekoModulePipeline.LanguageBinding BINDING = new NekoModulePipeline.LanguageBinding(
            new NekoModuleIdentity("javascript", NekoModuleMode.AUTO), NekoJavaScriptLanguagePlugin.INSTANCE, 1);

    @Test
    void leastRecentlyUsedEntryIsEvictedAtCapacity() throws Exception {
        BuiltinModuleTransformCache cache = new BuiltinModuleTransformCache(2, 1000);
        AtomicInteger calls = new AtomicInteger();
        var preparation = preparation(calls);
        cache.prepare(FILE, "a", BINDING, preparation);
        cache.prepare(Path.of("second.js"), "a", BINDING, preparation);
        cache.prepare(FILE, "a", BINDING, preparation);
        cache.prepare(Path.of("third.js"), "a", BINDING, preparation);
        cache.prepare(FILE, "a", BINDING, preparation);
        assertEquals(3, calls.get());
        cache.prepare(Path.of("second.js"), "a", BINDING, preparation);
        assertEquals(4, calls.get());
    }

    @Test
    void characterBudgetEvictsAndOversizeResultsAreNotRetained() throws Exception {
        BuiltinModuleTransformCache cache = new BuiltinModuleTransformCache(10, 5);
        AtomicInteger calls = new AtomicInteger();
        var preparation = preparation(calls);
        cache.prepare(FILE, "aaaa", BINDING, preparation);
        cache.prepare(FILE, "aaaa", BINDING, preparation);
        assertEquals(2, calls.get(), "source plus generated code exceeds the character budget");
        cache.prepare(FILE, "a", BINDING, preparation);
        cache.prepare(Path.of("second.js"), "a", BINDING, preparation);
        cache.prepare(FILE, "a", BINDING, preparation);
        assertEquals(5, calls.get(), "total retained characters must also stay bounded");
    }

    @Test
    void failuresAndChangedRegistryRevisionAreNeverReused() throws Exception {
        BuiltinModuleTransformCache cache = new BuiltinModuleTransformCache();
        assertThrows(Exception.class, () -> cache.prepare(FILE, "a", BINDING,
                () -> { throw new Exception("test failure"); }));
        AtomicInteger calls = new AtomicInteger();
        cache.prepare(FILE, "a", BINDING, preparation(calls));
        cache.prepare(FILE, "a", new NekoModulePipeline.LanguageBinding(BINDING.identity(), BINDING.plugin(), 2),
                preparation(calls));
        assertEquals(2, calls.get());
    }

    @Test
    void invalidationDuringPreparationAndTerminalCloseCannotRepopulateCache() throws Exception {
        BuiltinModuleTransformCache cache = new BuiltinModuleTransformCache();
        AtomicInteger calls = new AtomicInteger();
        cache.prepare(FILE, "a", BINDING, () -> {
            cache.invalidate(FILE::equals);
            return preparation(calls).prepare();
        });
        cache.prepare(FILE, "a", BINDING, preparation(calls));
        assertEquals(2, calls.get());
        cache.clear();
        cache.prepare(FILE, "a", BINDING, () -> {
            cache.close();
            return preparation(calls).prepare();
        });
        cache.prepare(FILE, "a", BINDING, preparation(calls));
        cache.prepare(FILE, "a", BINDING, preparation(calls));
        assertEquals(5, calls.get());
    }

    private static BuiltinModuleTransformCache.Preparation preparation(AtomicInteger calls) {
        return () -> {
            calls.incrementAndGet();
            return NekoPreparedModule.commonJs("javascript", "probe.js", "code", null, null);
        };
    }
}
