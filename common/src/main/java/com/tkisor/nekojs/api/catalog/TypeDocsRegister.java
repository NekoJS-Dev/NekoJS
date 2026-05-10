package com.tkisor.nekojs.api.catalog;

public interface TypeDocsRegister {
    void register(TypeDocCatalogEntry entry);

    void registerManualDeclaration(ManualDeclarationCatalogEntry entry);
}
