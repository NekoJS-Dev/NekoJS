//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.ui.ResourceStatus;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket44NativeTextPaintingTest {
    private static final String FONT_JSON = "{\"providers\":[{\"type\":\"space\",\"advances\":{\"A\":11}}]}";

    @AfterEach
    void clearReporter() { ScriptErrorReporter.set(null); }

    @Test
    void canonicalRootUsesTheSelectedFontForAutoLayoutAndNativePainting() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.resources.add("demo:font/wide.json", FONT_JSON);
            fixture.installRuntime();
            var width = fixture.context.eval("js", """
                    globalThis.root = UI.createRoot(() => UI.element('label', {
                      id: 'label', text: 'AAAA', font: 'demo:wide.json', width: 'auto', height: 'auto'
                    }), host, { id: 'font-root', viewport: { width: 100, height: 100 } });
                    host.bindRoot(root);
                    root.layout().nodes[0].rect.width
                    """);
            assertEquals(44, width.asInt());
            assertEquals(44, fixture.adapter.inspect().nodes().getFirst().rect().width());
            assertEquals(44, fixture.paint().getFirst().width());
            assertEquals(new FontDescription.Resource(Identifier.parse("demo:wide")), fixture.paint().getFirst().font());
        }
    }

    @Test
    void logicalFontIdsEndingInJsonAreNormalizedOnlyOnceAcrossMeasurementAndPaint() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.resources.add("demo:font/wide.json.json", FONT_JSON);
            fixture.commitLabel(Map.of("id", "label", "text", "AAAA", "font", "demo:wide.json.json"), 24, 9);
            assertEquals(new FontDescription.Resource(Identifier.parse("demo:wide.json")), fixture.paint().getFirst().font());
        }
    }

    @Test
    void selectedFontMeasuresAndPaintsTheSameGlyphWidths() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.resources.add("demo:font/wide.json", FONT_JSON);
            Map<String, Object> measured = fixture.adapter.measureText("AAAA", 9, 100, "demo:wide");
            assertEquals(44, measured.get("width"));
            assertEquals(9, measured.get("height"));
            fixture.commitLabel(Map.of("id", "label", "text", "AAAA", "font", "demo:wide", "fontSize", 9), 44, 9);
            List<NativeUiScreenFixture.DrawnText> drawn = fixture.paint();
            assertEquals(1, drawn.size());
            assertEquals("AAAA", drawn.getFirst().text());
            assertEquals(44, drawn.getFirst().width());
            assertEquals(new FontDescription.Resource(Identifier.parse("demo:wide")), drawn.getFirst().font());
        }
    }

    @Test
    void missingFontFallsBackConsistentlyAndReportsItsNodeAndRoot() throws Exception {
        List<String> diagnostics = new ArrayList<>();
        ScriptErrorReporter.set((type, phase, failure) -> diagnostics.add(failure.getMessage()));
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            assertEquals(24, fixture.adapter.measureText("AAAA", 9, 100, "demo:missing").get("width"));
            fixture.commitLabel(Map.of("id", "label", "text", "AAAA", "font", "demo:missing"), 24, 9);
            assertEquals(FontDescription.DEFAULT, fixture.paint().getFirst().font());
            assertTrue(diagnostics.stream().anyMatch(message -> message.contains("NEKO-6004")
                    && message.contains("root=text-test") && message.contains("node=label#label")
                    && message.contains("resource=demo:missing") && message.contains("generation=0")));
        }
    }

    @Test
    void invalidFontDefinitionUsesTheNativeCodecAndPreservesDecodeCause() throws Exception {
        List<Throwable> diagnostics = new ArrayList<>();
        ScriptErrorReporter.set((type, phase, failure) -> diagnostics.add(failure));
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.resources.add("demo:font/broken.json", "{\"providers\":[{\"type\":\"unsupported_provider\"}]}");
            assertEquals(24, fixture.adapter.measureText("AAAA", 9, 100, "demo:broken").get("width"));
            fixture.commitLabel(Map.of("id", "label", "text", "AAAA", "font", "demo:broken"), 24, 9);
            assertEquals(FontDescription.DEFAULT, fixture.paint().getFirst().font());
            assertTrue(diagnostics.stream().anyMatch(failure -> failure.getMessage().contains("NEKO-6007")
                    && failure.getCause() != null));
            var snapshot = fixture.adapter.inspect();
            ResourceStatus font = snapshot.nodes().getFirst().resources().getFirst();
            assertEquals(UiErrorCodes.RESOURCE_DECODE_FAILED, font.code());
        }
    }
}
//?}
