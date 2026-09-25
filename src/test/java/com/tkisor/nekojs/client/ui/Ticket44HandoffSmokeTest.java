//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.ui.DiskPackUiResourceResolver;
import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.api.ui.TextLayout;
import com.tkisor.nekojs.api.ui.TextLayouter;
import com.tkisor.nekojs.api.ui.UiErrorCodes;
import com.tkisor.nekojs.api.ui.VisualSpec;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 44 hand-off smoke scenarios, executable without a live client: the paint-side
 * consumers (measureText via {@link TextLayouter}, visual resolution via
 * {@code JsxHostAdapter.resolveVisual}) run against a fake {@link FontAdapter} with golden
 * assertions. Real-client execution of the same scenarios on NeoForge 26.2 is NOT RUN in
 * this environment (no Minecraft client); this suite pins the contract the client run
 * would exercise.
 */
class Ticket44HandoffSmokeTest {

    /** Deterministic fake: every glyph is 6px wide at base size 12, monospace-style. */
    private static final FontAdapter FAKE_FONT = new FontAdapter() {
        @Override
        public int stringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int lineHeight() {
            return 12;
        }

        @Override
        public int ascent() {
            return 10;
        }
    };

    private final List<String> seam = new ArrayList<>();

    @BeforeEach
    void captureSeam() {
        ScriptErrorReporter.set((type, kind, throwable) -> seam.add(type + "/" + kind + ":" + throwable.getMessage()));
    }

    @AfterEach
    void restoreSeam() {
        // The facade is designed to be set once per process (no getter to restore a previous
        // instance). set(null) returns it to the NOOP default, which is equivalent to the
        // pre-test state because suites that need reporting install their own reporter.
        ScriptErrorReporter.set(null);
    }

    // (a) multi-line wrap label: line count and bounding box come from the shared layouter
    @Test
    void multiLineWrapLabelGoldenBox() {
        TextLayout layout = TextLayouter.layoutScaled(
                FAKE_FONT, "alpha beta gamma delta", 0, 72, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of("alpha beta", "gamma delta"), layout.lines());
        assertEquals(11 * 6, layout.width(), "widest line 'gamma delta' at 6px per glyph");
        assertEquals(2 * 12, layout.height(), "two line boxes of 12px");
        assertEquals(12, layout.lineHeight());
        assertEquals(10, layout.ascent());
    }

    // (a') explicit newlines are paragraph breaks, words wrap within each paragraph
    @Test
    void explicitNewlineSplitsParagraphs() {
        TextLayout layout = TextLayouter.layout(
                FAKE_FONT, "one two\nthree", 42, true, TextLayouter.Truncation.OFF);
        assertEquals(List.of("one two", "three"), layout.lines());
    }

    // (b) fontSize hierarchy: the same text scales its box with the requested size
    @Test
    void fontSizeHierarchyScalesBoxes() {
        String text = "scale me";
        TextLayout at9 = TextLayouter.layoutScaled(FAKE_FONT, text, 9, 200, true, TextLayouter.Truncation.OFF);
        TextLayout at18 = TextLayouter.layoutScaled(FAKE_FONT, text, 18, 200, true, TextLayouter.Truncation.OFF);
        // 8 glyphs * 6px = 48 at base; scale 0.75 → 36, scale 1.5 → 72 (ceil, no pixels lost)
        assertEquals(36, at9.width());
        assertEquals(72, at18.width());
        assertEquals(9, at9.height(), "0.75 of the 12px line box");
        assertEquals(18, at18.height(), "1.5 of the 12px line box");
        assertEquals(8, at9.ascent(), "round(10 * 0.75)");
        assertEquals(15, at18.ascent(), "round(10 * 1.5)");
    }

    // (c) image resource present resolves silently; missing reaches the seam as NEKO-6004
    // with root/node/generation stamped
    @Test
    void missingImageResourceReachesDiagnosticsSeam(@TempDir Path packRoot) throws IOException {
        Path texture = packRoot.resolve("assets/mymod/textures/gui/panel.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] { 1 });
        DiskPackUiResourceResolver resolver = new DiskPackUiResourceResolver(packRoot);

        VisualSpec present = JsxHostAdapter.resolveVisual(
                Map.of("resource", "mymod:gui/panel"), "image", "hero", "ui-root-1", 7, resolver);
        assertTrue(present.image() != null && present.image().toString().equals("mymod:gui/panel"));
        assertTrue(seam.isEmpty(), "a present resource reports nothing: " + seam);

        VisualSpec missing = JsxHostAdapter.resolveVisual(
                Map.of("resource", "mymod:gui/absent"), "image", "hero", "ui-root-1", 7, resolver);
        assertTrue(missing.image() != null, "the id still lands in the spec so the host paints a placeholder");
        assertEquals(1, seam.size(), "exactly one diagnostic reaches the seam");
        String line = seam.getFirst();
        assertTrue(line.contains(UiErrorCodes.MISSING_RESOURCE), "missing resource code: " + line);
        assertTrue(line.contains("root=ui-root-1"), "diagnostic names the UI root: " + line);
        assertTrue(line.contains("node=image#hero"), "diagnostic names the node: " + line);
        assertTrue(line.contains("generation=7"), "diagnostic names the generation: " + line);
    }

    // (d) opacity/border/radius panel: controlled values parse into the spec, bad ones diagnose
    @Test
    void panelVisualPropsResolveWithDiagnosticsForBadValues() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("background", "#20RRGG");
        props.put("borderColor", "aqua");
        props.put("borderWidth", 2);
        props.put("radius", 4);
        props.put("opacity", 0.5);
        VisualSpec spec = JsxHostAdapter.resolveVisual(props, "panel", "card", "ui-root-2", 9, null);

        assertNull(spec.background(), "invalid color parses to absent");
        assertEquals(0xFF00FFFF, spec.borderColor().argb(), "CSS basic named color aqua");
        assertEquals(2, spec.borderWidth());
        assertEquals(4, spec.radius());
        assertEquals(0.5, spec.opacity());
        assertEquals(1, seam.size(), "the one bad prop is reported");
        assertTrue(seam.getFirst().contains(UiErrorCodes.INVALID_COLOR), seam.getFirst());
    }

    // (e) cropped image: the crop rect parses into the spec; non-positive sizes diagnose
    @Test
    void croppedImageParsesCropRectAndRejectsIllegalSizes() {
        Map<String, Object> crop = new LinkedHashMap<>();
        crop.put("x", 4);
        crop.put("y", 8);
        crop.put("width", 16);
        crop.put("height", 24);
        VisualSpec spec = JsxHostAdapter.resolveVisual(
                Map.of("resource", "mymod:gui/sprite", "crop", crop), "image", "icon", "ui-root-3", 11, null);
        assertEquals(new VisualSpec.CropRect(4, 8, 16, 24), spec.crop());
        assertTrue(seam.isEmpty(), "a legal crop reports nothing: " + seam);

        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("x", 0);
        bad.put("y", 0);
        bad.put("width", 0);
        bad.put("height", -1);
        JsxHostAdapter.resolveVisual(Map.of("crop", bad), "image", "icon", "ui-root-3", 11, null);
        assertTrue(seam.getFirst().contains(UiErrorCodes.INVALID_RESOURCE_SIZE), seam.getFirst());
    }

    // (f) script-path visual props: guest objects materialize at the adapter boundary, so
    // opacity/icon/crop set from a script resolve into the spec fields (the whitelist and
    // the fake-host proof cover the TS side; this pins the host boundary + resolver side)
    @Test
    void scriptPathVisualPropsReachResolverSpecFields() {
        try (Context context = Context.create("js")) {
            Value guestProps = context.eval("js",
                    "({ resource: 'mymod:gui/hero', fit: 'contain', opacity: 0.25,"
                            + " icon: 'mymod:gui/icon', crop: { x: 1, y: 2, width: 8, height: 9 } })");
            Map<String, Object> props = JsxHostAdapter.props(guestProps);
            VisualSpec spec = JsxHostAdapter.resolveVisual(props, "image", "hero", "ui-script", 3, null);
            assertEquals(0.25, spec.opacity());
            assertEquals("mymod:gui/hero", spec.image().toString());
            assertEquals("mymod:gui/icon", spec.icon().toString());
            assertEquals(new VisualSpec.CropRect(1, 2, 8, 9), spec.crop());
            assertTrue(seam.isEmpty(), "valid script props report nothing: " + seam);

            // the array crop shape from the TS type materializes through the same boundary
            Value arrayCrop = context.eval("js", "({ crop: [1, 2, 8, 9] })");
            VisualSpec arraySpec = JsxHostAdapter.resolveVisual(
                    JsxHostAdapter.props(arrayCrop), "image", "hero", "ui-script", 3, null);
            assertEquals(new VisualSpec.CropRect(1, 2, 8, 9), arraySpec.crop());
        }
    }

    // (g) label truncate: the resolved prop selects ellipsis truncation for painted text
    @Test
    void labelTruncatePropSelectsEllipsisTruncation() {
        VisualSpec on = JsxHostAdapter.resolveVisual(
                Map.of("text", "too long", "truncate", true), "label", "t1", "ui-root-4", 5, null);
        assertEquals(Boolean.TRUE, on.truncate());
        assertEquals(TextLayouter.Truncation.ELLIPSIS, JsxHostAdapter.truncationFor(on));
        VisualSpec off = JsxHostAdapter.resolveVisual(Map.of(), "label", "t2", "ui-root-4", 5, null);
        assertNull(off.truncate());
        assertEquals(TextLayouter.Truncation.OFF, JsxHostAdapter.truncationFor(off));
        assertEquals(TextLayouter.Truncation.OFF, JsxHostAdapter.truncationFor(null), "#text paint stays truncation-off");
        // A truncating label is a single line (wrap off) so the ellipsis actually cuts;
        // wrapped output always fits and would never report truncation.
        TextLayout truncated = TextLayouter.layout(
                FAKE_FONT, "way too long for the box", 30, false, TextLayouter.Truncation.ELLIPSIS);
        assertTrue(truncated.truncated(), "the selected truncation actually cuts with an ellipsis");
        assertEquals(1, truncated.lines().size());
        assertTrue(truncated.lines().getFirst().endsWith("..."), truncated.lines().getFirst());
    }
    // (h) opacity composes with explicit colors, not only with the painter fallbacks
    @Test
    void opacityComposesWithExplicitColors() {
        VisualSpec spec = JsxHostAdapter.resolveVisual(
                Map.of("background", "#FF0000", "opacity", 0.5), "panel", "card", "ui-root-5", 2, null);
        assertEquals(0x80FF0000, JsxHostAdapter.applyOpacity(spec, spec.background().argb()),
                "opacity halves the explicit color's alpha");
        VisualSpec translucent = JsxHostAdapter.resolveVisual(
                Map.of("background", 0x80FF0000, "opacity", 0.5), "panel", "card", "ui-root-5", 2, null);
        assertEquals(0x40FF0000, JsxHostAdapter.applyOpacity(translucent, translucent.background().argb()),
                "an explicit alpha channel is scaled, not kept");
        VisualSpec opaque = JsxHostAdapter.resolveVisual(
                Map.of("background", "#FF0000"), "panel", "card", "ui-root-5", 2, null);
        assertEquals(0xFFFF0000, JsxHostAdapter.applyOpacity(opaque, opaque.background().argb()),
                "no opacity prop leaves the winning color untouched");
    }
}
//?}
