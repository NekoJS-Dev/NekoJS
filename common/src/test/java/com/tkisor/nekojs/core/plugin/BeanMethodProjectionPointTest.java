package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies rule ownership through bootstrap, catalog snapshots and sealed collection. */
class BeanMethodProjectionPointTest {
    public static class Coordinates {
        public int getX() { return 1; }
        public int x() { return 1; }
        public int wrong() { return 1; }
        public String getWrong() { return "wrong"; }
        public static int staticMethod() { return 1; }
    }

    @Test
    void mergesCommonAndNodeRulesWithoutLeakingToTheNextRuntime() {
        com.tkisor.nekojs.testfixture.TestPlatformInit.ensureInitialized();
        var rule = new BeanMethodProjectionCatalogEntry(Coordinates.class, "x", "getX");
        class Common implements TypeDocsPoint.Contributor {
            TypeDocsRegister collector;
            @Override public void registerTypeDocs(TypeDocsRegister registry) {
                collector = registry;
                registry.registerBeanMethodProjection(rule);
            }
        }
        var common = new Common();
        var node = new NodeTypeDocsPoint.Contributor() {
            @Override public void registerNodeTypeDocs(TypeDocsRegister registry) {
                registry.registerBeanMethodProjection(rule);
            }
        };
        var runtime = NekoPluginBootstrap.bootstrap(List.of(common, node), new ScriptPropertyRegistry.Impl());
        assertEquals(List.of(rule), runtime.beanMethodProjections());
        assertEquals(List.of(rule), NekoScriptCatalog.snapshot(runtime).beanMethodProjections());
        for (ScriptType side : ScriptType.values()) {
            var rules = NekoScriptCatalog.snapshot(runtime, side).beanMethodProjections();
            assertEquals(List.of(rule), rules);
            assertThrows(UnsupportedOperationException.class, rules::clear);
        }
        assertThrows(IllegalStateException.class, () -> common.collector.registerBeanMethodProjection(rule));
        var next = NekoPluginBootstrap.bootstrap(List.of(), new ScriptPropertyRegistry.Impl());
        assertTrue(next.beanMethodProjections().isEmpty());
        assertEquals(List.of(rule), runtime.beanMethodProjections());
    }

    @Test
    void rejectsRulesWithoutTheRequiredRealInstanceSignatures() {
        for (String[] names : List.of(new String[]{"missing", "getX"}, new String[]{"x", "missing"},
                new String[]{"staticMethod", "getX"}, new String[]{"wrong", "getWrong"}, new String[]{"x", "x"})) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> new BeanMethodProjectionCatalogEntry(Coordinates.class, names[0], names[1]))
                    .getMessage().contains("[NEKO-4053]"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BeanMethodProjectionCatalogEntry(int.class, "x", "getX"));
    }
}
