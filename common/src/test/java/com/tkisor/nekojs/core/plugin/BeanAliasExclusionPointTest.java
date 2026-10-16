package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.script.prop.ScriptPropertyRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeanAliasExclusionPointTest {
    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void commonAndNodeRulesReachEverySnapshotAndFreezeWithTheirBuckets() {
        var commonRule = new BeanAliasExclusionCatalogEntry(String.class, "isEmpty", "empty");
        var nodeRule = new BeanAliasExclusionCatalogEntry(java.io.File.class, "getPath", "path");
        var common = new CommonContributor(commonRule);
        NodeTypeDocsPoint.Contributor node = new NodeTypeDocsPoint.Contributor() {
            @Override
            public void registerNodeTypeDocs(TypeDocsRegister registry) {
                registry.registerBeanAliasExclusion(commonRule);
                registry.registerBeanAliasExclusion(nodeRule);
            }
        };
        var runtime = NekoPluginBootstrap.bootstrap(List.of(common, node), new ScriptPropertyRegistry.Impl());
        var expected = List.of(commonRule, nodeRule);
        assertEquals(expected, runtime.beanAliasExclusions());
        assertEquals(expected, NekoScriptCatalog.snapshot(runtime).beanAliasExclusions());
        for (ScriptType type : ScriptType.values()) {
            var snapshot = NekoScriptCatalog.snapshot(runtime, type);
            assertEquals(expected, snapshot.beanAliasExclusions());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.beanAliasExclusions().clear());
        }
        assertThrows(IllegalStateException.class, () -> common.registry.registerBeanAliasExclusion(commonRule));
        var next = NekoPluginBootstrap.bootstrap(List.of(), new ScriptPropertyRegistry.Impl());
        assertTrue(next.beanAliasExclusions().isEmpty());
        assertEquals(expected, runtime.beanAliasExclusions());
    }

    @Test
    void rejectsInvalidTargetsAndEmptyMatchKeys() {
        for (Class<?> target : List.of(int.class, String[].class)) {
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> new BeanAliasExclusionCatalogEntry(target, "isReady", "ready"))
                    .getMessage().contains("[NEKO-4047]"));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BeanAliasExclusionCatalogEntry(String.class, " ", "empty"));
        assertThrows(IllegalArgumentException.class,
                () -> new BeanAliasExclusionCatalogEntry(String.class, "isEmpty", ""));
    }

    private static final class CommonContributor implements TypeDocsPoint.Contributor {
        private final BeanAliasExclusionCatalogEntry entry;
        private TypeDocsRegister registry;

        private CommonContributor(BeanAliasExclusionCatalogEntry entry) {
            this.entry = entry;
        }

        @Override
        public void registerTypeDocs(TypeDocsRegister registry) {
            this.registry = registry;
            registry.registerBeanAliasExclusion(entry);
        }
    }
}
