package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ConversionPrecedence;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegacySurfaceAdapterTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void bindingMapsToGlobalWithLegacyPreview() {
        BindingCatalogEntry entry = BindingCatalogEntry.of("Item", ScriptType.SERVER, String.class, true);
        List<ApiSymbol> symbols = LegacySurfaceAdapter.fromBindings(List.of(entry));

        assertEquals(1, symbols.size());
        ApiSymbol symbol = symbols.get(0);
        assertEquals("global:Item", symbol.id().value());
        assertEquals("global", symbol.id().kind());
        assertEquals("Item", symbol.id().qualifiedName());
    }

    @Test
    void eventMapsToEventKind() {
        EventCatalogEntry entry = EventCatalogEntry.of(
                "Server", "tick", ScriptType.SERVER,
                String.class, null, false, false);
        List<ApiSymbol> symbols = LegacySurfaceAdapter.fromEvents(List.of(entry));

        assertEquals(1, symbols.size());
        ApiSymbol symbol = symbols.get(0);
        assertEquals("event:Server.tick", symbol.id().value());
        assertEquals("event", symbol.id().kind());
        assertEquals("Server.tick", symbol.id().qualifiedName());
    }

    @Test
    void adapterMapsToAdapterKind() {
        AdapterCatalogEntry entry = AdapterCatalogEntry.of(String.class,
                ConversionPrecedence.LOWEST);
        List<ApiSymbol> symbols = LegacySurfaceAdapter.fromAdapters(List.of(entry));

        assertEquals(1, symbols.size());
        ApiSymbol symbol = symbols.get(0);
        assertEquals("adapter:java.lang.String", symbol.id().value());
        assertEquals("adapter", symbol.id().kind());
    }

    @Test
    void javaFqnOnlyAsDiagnosticMetadata() {
        BindingCatalogEntry entry = BindingCatalogEntry.of("Item", ScriptType.SERVER, String.class, true);
        List<ApiSymbol> symbols = LegacySurfaceAdapter.fromBindings(List.of(entry));

        ApiSymbol symbol = symbols.get(0);
        // The stable ID uses the JS name, not the Java FQN
        assertEquals("global:Item", symbol.id().value());
        // Java FQN should NOT appear as a managed type ref in signatures
        // Signatures should use primitive/void types, not symbol refs to Java classes
        symbol.signatures().forEach(sig -> {
            if (sig.returnType().kind() != com.tkisor.nekojs.api.surface.ApiTypeRef.Kind.VOID) {
                // If not void, should be a primitive
                assertEquals(com.tkisor.nekojs.api.surface.ApiTypeRef.Kind.PRIMITIVE, sig.returnType().kind());
            }
        });
    }

    @Test
    void convertCombinesAllSources() {
        BindingCatalogEntry binding = BindingCatalogEntry.of("Item", ScriptType.SERVER, String.class, true);
        EventCatalogEntry event = EventCatalogEntry.of(
                "Server", "tick", ScriptType.SERVER,
                String.class, null, false, false);
        AdapterCatalogEntry adapter = AdapterCatalogEntry.of(String.class,
                ConversionPrecedence.LOWEST);
        HostExtensionCatalogEntry hostExt = new HostExtensionCatalogEntry(
                String.class, Runnable.class, "run", "run",
                Runnable.class.getMethods()[0], ScriptType.SERVER, false);

        NekoScriptCatalogSnapshot snapshot = new NekoScriptCatalogSnapshot(
                ScriptType.all(),
                List.of(binding),
                List.of(event),
                List.of(adapter),
                List.of(),
                List.of(hostExt),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new TypeOutputLayout(java.nio.file.Path.of("types"), java.nio.file.Path.of("snippets")),
                Map.of(),
                List.of()
        );

        List<ApiSymbol> symbols = LegacySurfaceAdapter.convert(snapshot);
        assertFalse(symbols.isEmpty());
        // Should contain at least the binding, event, adapter, and host extension
        assertTrue(symbols.size() >= 4);
    }
}
