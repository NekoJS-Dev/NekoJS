package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftTypeDocsPluginTest {
    @Test
    void contributesOnlyTheLocalEntityRuleThroughTheNodeDiscoveryPath() throws Exception {
        assertNotNull(MinecraftTypeDocsPlugin.class.getAnnotation(RegisterNekoJSPlugin.class));
        //? if fabric {
            Class<?> loader = Class.forName("com.tkisor.nekojs.fabric.FabricPluginLoader");
            var field = loader.getDeclaredField("BUILTIN_PLUGINS");
            field.setAccessible(true);
            assertTrue(((List<?>) field.get(null)).contains(MinecraftTypeDocsPlugin.class),
                    "Fabric discovery must include the shared Minecraft TypeDocs contributor");
        //?}
        List<BeanAliasExclusionCatalogEntry> rules = new ArrayList<>();
        new MinecraftTypeDocsPlugin().registerTypeDocs(new TypeDocsRegister() {
            @Override
            public void register(TypeDocCatalogEntry entry) {
                fail("Unexpected documentation entry");
            }

            @Override
            public void registerManualDeclaration(ManualDeclarationCatalogEntry entry) {
                fail("Unexpected manual declaration");
            }

            @Override
            public void registerBeanAliasExclusion(BeanAliasExclusionCatalogEntry entry) {
                rules.add(entry);
            }
        });
        assertEquals(List.of(new BeanAliasExclusionCatalogEntry(Entity.class, "isAttackable", "attackable")), rules);
    }
}
