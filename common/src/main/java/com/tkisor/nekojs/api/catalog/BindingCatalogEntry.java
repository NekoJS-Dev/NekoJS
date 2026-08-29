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

    /// 追加额外的 doc 来源类：其 public 成员会被 probe 合并进本绑定的 .d.ts。
    /// 用于 [com.tkisor.nekojs.js.DelegatingBinding] 代理——valueType 是 helper，
    /// 但运行时还委托 targetClass 的静态成员，probe 需把后者也纳入补全。
    public BindingCatalogEntry withExtraDocTypes(List<Class<?>> extras) {
        return new BindingCatalogEntry(
                name, scriptType, javaType, staticClass, hostClass, emit,
                typeOverride, description, examples,
                List.copyOf(extras)
        );
    }
}
