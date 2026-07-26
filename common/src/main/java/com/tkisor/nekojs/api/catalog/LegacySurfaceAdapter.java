package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only adapter that converts existing catalog entries into {@link ApiSymbol} with
 * {@link com.tkisor.nekojs.api.surface.ApiTier#LEGACY_PREVIEW} tier.
 *
 * <p>This adapter does NOT modify existing catalog entries and does NOT treat legacy symbols
 * as normative stable. Java FQN is used only as diagnostic metadata, not as managed type refs.
 */
public final class LegacySurfaceAdapter {

    private LegacySurfaceAdapter() {
    }

    public static List<ApiSymbol> convert(NekoScriptCatalogSnapshot snapshot) {
        List<ApiSymbol> result = new ArrayList<>();
        result.addAll(fromBindings(snapshot.bindings()));
        result.addAll(fromEvents(snapshot.events()));
        result.addAll(fromAdapters(snapshot.adapters()));
        result.addAll(fromHostExtensions(snapshot.hostExtensions()));
        return List.copyOf(result);
    }

    public static List<ApiSymbol> fromBindings(List<BindingCatalogEntry> bindings) {
        List<ApiSymbol> result = new ArrayList<>();
        for (BindingCatalogEntry entry : bindings) {
            ApiSymbolId id = new ApiSymbolId("global", entry.name());
            ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());
            result.add(new ApiSymbol(id, List.of(sig)));
        }
        return List.copyOf(result);
    }

    public static List<ApiSymbol> fromEvents(List<EventCatalogEntry> events) {
        List<ApiSymbol> result = new ArrayList<>();
        for (EventCatalogEntry entry : events) {
            String qualifiedName = entry.group() + "." + entry.name();
            ApiSymbolId id = new ApiSymbolId("event", qualifiedName);
            ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());
            result.add(new ApiSymbol(id, List.of(sig)));
        }
        return List.copyOf(result);
    }

    public static List<ApiSymbol> fromAdapters(List<AdapterCatalogEntry> adapters) {
        List<ApiSymbol> result = new ArrayList<>();
        for (AdapterCatalogEntry entry : adapters) {
            ApiSymbolId id = new ApiSymbolId("adapter", entry.targetType().getName());
            ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());
            result.add(new ApiSymbol(id, List.of(sig)));
        }
        return List.copyOf(result);
    }

    public static List<ApiSymbol> fromHostExtensions(List<HostExtensionCatalogEntry> hostExtensions) {
        List<ApiSymbol> result = new ArrayList<>();
        for (HostExtensionCatalogEntry entry : hostExtensions) {
            String qualifiedName = entry.targetClass().getName() + "." + entry.jsName();
            ApiSymbolId id = new ApiSymbolId("hostExt", qualifiedName);
            ApiSignature sig = ApiSignature.function(List.of(), ApiTypeRef.voidType());
            result.add(new ApiSymbol(id, List.of(sig)));
        }
        return List.copyOf(result);
    }
}
