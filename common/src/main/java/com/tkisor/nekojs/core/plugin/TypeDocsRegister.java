package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;

public interface TypeDocsRegister {
    void register(TypeDocCatalogEntry entry);

    void registerManualDeclaration(ManualDeclarationCatalogEntry entry);
}
