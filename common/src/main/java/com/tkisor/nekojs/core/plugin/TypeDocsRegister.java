package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.catalog.TypeDocCatalogEntry;
import com.tkisor.nekojs.api.catalog.ManualDeclarationCatalogEntry;
import com.tkisor.nekojs.api.catalog.ClassDeclarationCatalogEntry;

public interface TypeDocsRegister {
    void register(TypeDocCatalogEntry entry);

    void registerManualDeclaration(ManualDeclarationCatalogEntry entry);

    /**
     * 注册**类声明替换**：把指定类的 probe 生成结果整体换成手写声明。
     *
     * <p>见 {@link ClassDeclarationCatalogEntry}——反射表达不了的泛型形状靠它补齐。
     * 默认抛异常而非静默忽略：忘了实现会让注册悄悄不生效，比直接报错难查。
     */
    default void registerClassDeclaration(ClassDeclarationCatalogEntry entry) {
        throw new UnsupportedOperationException(
                "本 TypeDocsRegister 实现未支持类声明替换：" + getClass().getName());
    }
}
