package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanAliasExclusionCatalogEntry;
import com.tkisor.nekojs.api.catalog.BeanMethodProjectionCatalogEntry;

public interface TypeDocsRegister {
    void register(TypeDocCatalogEntry entry);

    void registerManualDeclaration(ManualDeclarationCatalogEntry entry);

    /** Registers a global replacement for a generated Java class's TypeScript module body. */
    default void registerClassDeclaration(ClassDeclarationCatalogEntry entry) {
        throw new UnsupportedOperationException("[NEKO-4031] Class declaration registration rejected: "
                + "this type-docs collector does not support replacements");
    }

    /** Registers one exact TypeScript Bean alias exclusion while retaining its real accessor. */
    default void registerBeanAliasExclusion(BeanAliasExclusionCatalogEntry entry) {
        throw new UnsupportedOperationException("[NEKO-4047] Bean alias exclusion rejected: "
                + "this type-docs collector does not support exclusions");
    }

    /** Registers an exact TypeScript method projection onto its retained Bean getter. */
    default void registerBeanMethodProjection(BeanMethodProjectionCatalogEntry entry) {
        throw new UnsupportedOperationException("[NEKO-4053] Bean method projection rejected: "
                + "this type-docs collector does not support projections");
    }

    /**
     * Records a structured entry for the typed Builder side. The version tree derives it by
     * reflecting over a builder class's API, and the TS and Python declaration backends render
     * from the same input. The default implementation is empty, so existing implementors
     * stay compatible.
     */
    default void registerRegistryBuilderSurface(RegistryBuilderSurfaceEntry entry) {
    }
}
