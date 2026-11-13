package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.probe.backend.typescript.AdapterAliasGenerator;
import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.ConversionPrecedence;

import java.util.List;
import java.util.Optional;

/**
 * An adapter catalog entry: the target type of a {@link JSTypeAdapter}, its declared input shapes
 * and its precedence.
 *
 * <p>It stays in memory and is never serialized. {@code NekoScriptCatalog.adapters()} builds these
 * entries, {@code NekoScriptCatalogSnapshot} hands them to the probe, and
 * {@code AdapterAliasGenerator} consumes them.
 */
public record AdapterCatalogEntry(
        Class<?> targetType,
        List<AdapterInputShape> shapes,
        ConversionPrecedence precedence,
        Optional<String> syntaxDoc
) {
    public static AdapterCatalogEntry of(Class<?> targetType, ConversionPrecedence precedence) {
        return new AdapterCatalogEntry(targetType, List.of(), precedence, Optional.empty());
    }
}
