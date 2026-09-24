package com.tkisor.nekojs.api.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualStyleResolverTest {
    private static final UiDiagnostic.Location LOCATION =
            new UiDiagnostic.Location("root-1", "panel", "main-panel", 12L);

    @Test
    void resolvesPanelVisualProps() {
        Map<String, Object> props = Map.of(
                "background", "#80FF0000",
                "borderColor", "red",
                "borderWidth", 2,
                "radius", 4,
                "opacity", 0.5);
        VisualSpec spec = VisualStyleResolver.resolve(props, LOCATION);
        assertEquals(0x80FF0000, spec.background().argb());
        assertEquals(0xFFFF0000, spec.borderColor().argb());
        assertEquals(2, spec.borderWidth());
        assertEquals(4, spec.radius());
        assertEquals(0.5, spec.opacity());
        assertTrue(spec.diagnostics().isEmpty());
    }

    @Test
    void resolvesLabelAndImageProps() {
        Map<String, Object> props = Map.of(
                "color", "#00ff00",
                "fontSize", 12,
                "resource", "mymod:gui/panel",
                "fit", "contain",
                "crop", Map.of("x", 1, "y", 2, "width", 8, "height", 8),
                "icon", "mymod:icons/star");
        VisualSpec spec = VisualStyleResolver.resolve(props, LOCATION);
        assertEquals(0xFF00FF00, spec.color().argb());
        assertEquals(12.0, spec.fontSize());
        assertEquals("mymod:gui/panel", spec.image().toString());
        assertEquals(VisualSpec.ImageFit.CONTAIN, spec.fit());
        assertEquals(new VisualSpec.CropRect(1, 2, 8, 8), spec.crop());
        assertEquals("mymod:icons/star", spec.icon().toString());
        assertTrue(spec.diagnostics().isEmpty());
    }

    @Test
    void acceptsCropAsPositionList() {
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("crop", List.of(0, 4, 16, 16)), LOCATION);
        assertEquals(new VisualSpec.CropRect(0, 4, 16, 16), spec.crop());
    }

    @Test
    void invalidColorBecomesDiagnosticInsteadOfException() {
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("background", "rgb(1, 2, 3)"), LOCATION);
        assertNull(spec.background());
        assertEquals(1, spec.diagnostics().size());
        assertEquals("NEKO-6001", spec.diagnostics().get(0).code());
    }

    @Test
    void outOfRangeValuesReportOneDiagnosticEach() {
        VisualSpec spec = VisualStyleResolver.resolve(Map.of(
                "opacity", 1.5, "fontSize", 0, "radius", -1, "fit", "tile"), LOCATION);
        assertNull(spec.opacity());
        assertNull(spec.fontSize());
        assertNull(spec.radius());
        assertNull(spec.fit());
        assertEquals(4, spec.diagnostics().size());
        assertEquals(4, spec.diagnostics().stream().filter(d -> "NEKO-6002".equals(d.code())).count());
    }

    @Test
    void invalidCropReportsSizeDiagnostic() {
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("crop", Map.of("x", 0, "y", 0, "width", 0, "height", 8)), LOCATION);
        assertNull(spec.crop());
        assertEquals("NEKO-6006", spec.diagnostics().get(0).code());
    }

    @Test
    void invalidResourceIdReportsIdDiagnostic() {
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("resource", "Bad:Id"), LOCATION);
        assertNull(spec.image());
        assertEquals("NEKO-6003", spec.diagnostics().get(0).code());
        assertEquals("Bad:Id", spec.diagnostics().get(0).resourceId());
    }

    @Test
    void missingResourceProducesLocatableDiagnostic() {
        DiskPackUiResourceResolver missing = new DiskPackUiResourceResolver(java.nio.file.Path.of("nowhere"));
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("resource", "mymod:gui/absent"), LOCATION, missing);
        assertEquals("mymod:gui/absent", spec.image().toString());
        UiDiagnostic diagnostic = spec.diagnostics().get(0);
        assertEquals("NEKO-6004", diagnostic.code());
        assertEquals("root-1", diagnostic.rootId());
        assertEquals("panel", diagnostic.nodeType());
        assertEquals("main-panel", diagnostic.nodeKey());
        assertEquals("mymod:gui/absent", diagnostic.resourceId());
        assertEquals(12L, diagnostic.generation());
        String line = diagnostic.logLine();
        assertTrue(line.startsWith("[NEKO-6004]"));
        assertTrue(line.contains("root=root-1"));
        assertTrue(line.contains("node=panel#main-panel"));
        assertTrue(line.contains("resource=mymod:gui/absent"));
        assertTrue(line.contains("generation=12"));
    }

    @Test
    void resolvedResourceStaysQuiet() {
        UiResourceResolver resolver = new ExistingFileResolver();
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("icon", "mymod:icons/star"), LOCATION, resolver);
        assertEquals("mymod:icons/star", spec.icon().toString());
        assertTrue(spec.diagnostics().isEmpty());
    }

    /** Resolver stub that reports every id as resolved. */
    private static final class ExistingFileResolver implements UiResourceResolver {
        @Override
        public ResourceStatus resolveTexture(String id) {
            return UiResourceId.parse(id)
                    .<ResourceStatus>map(parsed -> ResourceStatus.resolved(parsed.toString(), "memory:" + parsed))
                    .orElseGet(() -> ResourceStatus.invalid(id));
        }

        @Override
        public ResourceStatus resolveFont(String id) {
            return resolveTexture(id);
        }
    }
}
