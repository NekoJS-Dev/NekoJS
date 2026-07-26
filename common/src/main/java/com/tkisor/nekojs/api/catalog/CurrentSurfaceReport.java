package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

/**
 * Deterministic audit report of the current legacy surface.
 * This report is for migration auditing, not a normative contract.
 *
 * <p>Outputs globals/events/adapters/hostExtensions with stable ID sorted alphabetically.
 */
public final class CurrentSurfaceReport {

    private CurrentSurfaceReport() {
    }

    public static String generate(NekoScriptCatalogSnapshot snapshot) {
        List<ApiSymbol> symbols = new ArrayList<>(snapshot.legacySurface());
        symbols.sort(Comparator.comparing(s -> s.id().value()));

        StringBuilder sb = new StringBuilder();
        sb.append("# Current Surface Report\n");
        sb.append("# This report is for migration auditing, not a normative contract.\n\n");

        StringJoiner joiner = new StringJoiner("\n");
        for (ApiSymbol symbol : symbols) {
            joiner.add(formatSymbol(symbol));
        }
        sb.append(joiner);

        return sb.toString();
    }

    private static String formatSymbol(ApiSymbol symbol) {
        ApiSymbolId id = symbol.id();
        return id.kind() + ":" + id.qualifiedName();
    }
}
