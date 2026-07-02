package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.AdapterInputShape;
import graal.graalvm.polyglot.HostAccess;

import java.util.List;

/**
 * 适配器目录条目：携带 {@link JSTypeAdapter} 的目标类型、声明的输入形状与优先级。
 *
 * <p>纯内存传输（不序列化），由 {@code NekoScriptCatalog.adapters()} 构建，
 * 经 {@code NekoScriptCatalogSnapshot} 传给 probe，由 {@code AdapterAliasGenerator} 消费。
 */
public record AdapterCatalogEntry(
        Class<?> targetType,
        List<AdapterInputShape> shapes,
        HostAccess.TargetMappingPrecedence precedence
) {
    public static AdapterCatalogEntry of(Class<?> targetType, HostAccess.TargetMappingPrecedence precedence) {
        return new AdapterCatalogEntry(targetType, List.of(), precedence);
    }
}
