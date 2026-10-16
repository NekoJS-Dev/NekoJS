package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.core.plugin.TypeDocsPoint;
import com.tkisor.nekojs.core.plugin.TypeDocsRegister;
import net.minecraft.world.entity.Entity;

/** Contributes Minecraft declaration projections through the shared TypeDocs channel. */
@RegisterNekoJSPlugin
public final class MinecraftTypeDocsPlugin implements TypeDocsPoint.Contributor {
    @Override
    public void registerTypeDocs(TypeDocsRegister registry) {
        // The derived boolean property conflicts with LivingEntity's real attackable() method.
        registry.registerBeanAliasExclusion(new BeanAliasExclusionCatalogEntry(
                Entity.class, "isAttackable", "attackable"));
    }
}
