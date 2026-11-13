package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptType;

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
        List<String> examples,
        List<Class<?>> extraDocTypes
) {
    public BindingCatalogEntry {
        examples = List.copyOf(examples == null ? List.of() : examples);
        extraDocTypes = List.copyOf(extraDocTypes == null ? List.of() : extraDocTypes);
    }

    public static BindingCatalogEntry of(String name, ScriptType scriptType, Class<?> javaType, boolean staticClass) {
        return new BindingCatalogEntry(name, scriptType, javaType, staticClass, staticClass, true, null, null, List.of(), List.of());
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
                doc.examples().isEmpty() ? examples : doc.examples(),
                extraDocTypes
        );
    }

    /// Returns a copy that adds documentation source classes: the probe merges their public members
    /// into the .d.ts of this binding. Used for [com.tkisor.nekojs.js.DelegatingBinding] proxies,
    /// whose valueType is the helper while the proxy also delegates to the static members of
    /// targetClass, so the probe must include those members in completions too.
    public BindingCatalogEntry withExtraDocTypes(List<Class<?>> extras) {
        return new BindingCatalogEntry(
                name, scriptType, javaType, staticClass, hostClass, emit,
                typeOverride, description, examples,
                List.copyOf(extras)
        );
    }
}
