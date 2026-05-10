package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.script.ScriptType;

import java.util.List;

public record BindingCatalogEntry(
        String name,
        ScriptType scriptType,
        Class<?> javaType,
        boolean staticClass,
        boolean hostClass,
        boolean emit,
        String typeOverride,
        String description,
        List<String> examples
) {
    public BindingCatalogEntry {
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    public static BindingCatalogEntry of(String name, ScriptType scriptType, Class<?> javaType, boolean staticClass) {
        return new BindingCatalogEntry(name, scriptType, javaType, staticClass, staticClass, true, null, null, List.of());
    }

    public BindingCatalogEntry withDoc(TypeDocCatalogEntry doc) {
        return new BindingCatalogEntry(
                name,
                scriptType,
                javaType,
                staticClass,
                hostClass,
                emit,
                doc.typeOverride() == null ? typeOverride : doc.typeOverride(),
                doc.description() == null ? description : doc.description(),
                doc.examples().isEmpty() ? examples : doc.examples()
        );
    }
}
