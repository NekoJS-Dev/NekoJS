package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;

public interface TypeDocsRegister {
    void register(TypeDocCatalogEntry entry);

    void registerManualDeclaration(ManualDeclarationCatalogEntry entry);

    /**
     * 登记 typed Builder 面的结构化契约条目（ticket 15）：由版本树对 builder 类的
     * 契约反射派生，TS/Python declaration 后端从同一输入渲染。default 空实现保持
     * 既有实现方兼容。
     */
    default void registerRegistryBuilderSurface(RegistryBuilderSurfaceEntry entry) {
    }
}
