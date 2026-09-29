package com.tkisor.nekojs.wrapper.client;

import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.core.NekoSharedHostAccess;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.PolyglotException;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect D6 regression (ticket 26): script-side ARGB color literals are unsigned JS
 * numbers ≥ 2³¹ ({@code 0xFFFFFF00} = 4294967040). Declaring painter color parameters
 * as Java {@code int} saturates them to {@code 0x7FFFFFFF} (translucent white) at the
 * engine conversion boundary, so the painter seam accepts {@link Number} and reads the
 * bits through {@code UiColor.argbBits} instead.
 *
 * <p>Test geometry: a real GraalJS {@link Context} (production-shaped
 * {@link NekoSharedHostAccess} + interop options) drives the <b>production
 * {@link PainterJS}</b> through its measurement-only test seam; the colors are asserted
 * at the graphics sink via a recording subclass of the platform graphics type. The
 * platform constructor dereferences a live {@code Minecraft} client, so the recorder is
 * allocated without running any constructor ({@code Unsafe.allocateInstance}) — its
 * only state is the recorded colors, which never touch platform fields.
 */
class PainterJSScriptColorBoundaryTest {

    /** Deterministic fake: every glyph is 6px wide at a 12px line box (same as the text-members suite). */
    private static final FontAdapter FAKE_FONT = new FontAdapter() {
        @Override public int stringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override public int lineHeight() {
            return 12;
        }

        @Override public int ascent() {
            return 10;
        }
    };

//? if >=26 {
    private static final class RecordingGraphics extends net.minecraft.client.gui.GuiGraphicsExtractor {
        // Records the colors reaching the platform graphics sink (26.x render-state model).
        Integer lastText;
        Integer lastFill;
        Integer lastGradientTop;
        Integer lastGradientBottom;
        Integer lastOutline;

        // Never invoked: instances come from Unsafe.allocateInstance (see below). Declared
        // only so the default constructor does not reference a nonexistent super form.
        private RecordingGraphics() {
            super(null, null, 0, 0);
        }

        @Override public void text(Font font, String text, int x, int y, int color) {
            this.lastText = color;
        }

        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            this.lastFill = color;
        }

        @Override public void fillGradient(int x1, int y1, int x2, int y2, int colorTop, int colorBottom) {
            this.lastGradientTop = colorTop;
            this.lastGradientBottom = colorBottom;
        }

        @Override public void outline(int x, int y, int width, int height, int color) {
            this.lastOutline = color;
        }
    }
//?} else {
/*    private static final class RecordingGraphics extends net.minecraft.client.gui.GuiGraphics {
        // Records the colors reaching the platform graphics sink (1.21.1 model).
        Integer lastText;
        Integer lastFill;
        Integer lastGradientTop;
        Integer lastGradientBottom;
        Integer lastOutline;

        // Never invoked: instances come from Unsafe.allocateInstance (see below). Declared
        // only so the default constructor does not reference a nonexistent super form.
        private RecordingGraphics() {
            super(null, null);
        }

        @Override public int drawString(Font font, String text, int x, int y, int color) {
            this.lastText = color;
            return 0;
        }

        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            this.lastFill = color;
        }

        @Override public void fillGradient(int x1, int y1, int x2, int y2, int colorTop, int colorBottom) {
            this.lastGradientTop = colorTop;
            this.lastGradientBottom = colorBottom;
        }

        @Override public void hLine(int x1, int x2, int y, int color) {
            this.lastOutline = color;
        }

        @Override public void vLine(int x1, int y1, int y2, int color) {
            this.lastOutline = color;
        }
    }
*///?}

    /** The defect's headline case: yellow must reach the sink with its ARGB bits intact. */
    @Test
    void uint32TextColorLiteralReachesGraphicsSinkAsArgbBits() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        try (Context context = newScriptContext(graphics)) {
            context.eval("js", "painter.text('T26 yellow', 4, 4, 0xFFFFFF00)");
        }
        assertEquals(-256, boxed(graphics.lastText), "0xFFFFFF00 must arrive as int32 bits, not 0x7FFFFFFF");
    }

    /** The hudRender smoke shape (green badge) on rect and outline. */
    @Test
    void uint32RectAndOutlineColorsReachGraphicsSink() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        try (Context context = newScriptContext(graphics)) {
            context.eval("js", "painter.rect(4, 18, 140, 12, 0xFF55FF55)");
            context.eval("js", "painter.outline(4, 18, 140, 12, 0x80FF0000)");
        }
        assertEquals(0xFF55FF55, boxed(graphics.lastFill), "0xFF55FF55 (green) bits");
        assertEquals(0x80FF0000, boxed(graphics.lastOutline), "0x80FF0000 (translucent red) bits");
    }

    /** Gradient ends keep independent uint32 bit patterns. */
    @Test
    void gradientEndsKeepIndependentArgbBits() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        try (Context context = newScriptContext(graphics)) {
            context.eval("js", "painter.gradient(0, 0, 4, 4, 0xFFFFFF00, 0xFF0000FF)");
        }
        assertEquals(-256, boxed(graphics.lastGradientTop), "gradient top 0xFFFFFF00 bits");
        assertEquals(0xFF0000FF, boxed(graphics.lastGradientBottom), "gradient bottom 0xFF0000FF bits");
    }

    /** The current-color setter feeds color-omitted draws with the same normalization. */
    @Test
    void currentColorSetterNormalizesUint32ForColorlessDraws() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        try (Context context = newScriptContext(graphics)) {
            context.eval("js", "painter.color(0xFFFFFF00).rect(0, 0, 4, 4)");
        }
        assertEquals(-256, boxed(graphics.lastFill), "color(0xFFFFFF00) then rect() must paint yellow");
    }

    /** Script bitwise color math already yields negative int32s; those must pass through unchanged. */
    @Test
    void negativeInt32FromBitwiseMathPassesThrough() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        try (Context context = newScriptContext(graphics)) {
            context.eval("js", "painter.text('hi', 0, 0, 0xFFFFFF00 | 0)");
        }
        assertEquals(-256, boxed(graphics.lastText), "(0xFFFFFF00 | 0) is already -256 in JS");
    }

    /** JVM callers passing Java ints (negative from uint32 literals) stay behavior-identical. */
    @Test
    void javaIntCallersStayBehaviorCompatible() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        PainterJS painter = new PainterJS(graphics, FAKE_FONT, 0F);
        painter.text("a", 0, 0, 0xFFFFFF00); // Java int literal = -256
        assertEquals(-256, boxed(graphics.lastText));
        painter.text("b", 0, 0, -256);
        assertEquals(-256, boxed(graphics.lastText));
        painter.color(0xFFFFFF00).rect(0, 0, 4, 4);
        assertEquals(-256, boxed(graphics.lastFill));
    }

    /** Bad color forms fail loudly in English instead of painting a wrong color. */
    @Test
    void badColorFormsAreRejectedWithEnglishMessages() {
        RecordingGraphics graphics = allocateRecordingGraphics();
        try (Context context = newScriptContext(graphics)) {
            PolyglotException fromNull = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "painter.text('x', 0, 0, null)"));
            assertTrue(fromNull.getMessage().contains("color must not be null"),
                    "expected explicit null rejection, got: " + fromNull.getMessage());
            PolyglotException fractional = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "painter.text('x', 0, 0, 1.5)"));
            assertTrue(fractional.getMessage().contains("int32/uint32 ARGB range"),
                    "expected range rejection, got: " + fractional.getMessage());
            PolyglotException oversized = assertThrows(PolyglotException.class,
                    () -> context.eval("js", "painter.text('x', 0, 0, 0x1FFFFFFFF)"));
            assertTrue(oversized.getMessage().contains("int32/uint32 ARGB range"),
                    "expected range rejection, got: " + oversized.getMessage());
        }
    }

    private Context newScriptContext(RecordingGraphics graphics) {
        NekoSharedHostAccess hostAccess = new NekoSharedHostAccess(List.of());
        Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(hostAccess.get())
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.strict", "true")
                .build();
        context.getBindings("js").putMember("painter", new PainterJS(graphics, FAKE_FONT, 0F));
        return context;
    }

    /**
     * Allocates the recorder without running any constructor: the only reachable platform
     * constructor dereferences a live {@code Minecraft} client, which no headless test has.
     * The recorder's own state is the recorded colors (boxed so the no-constructor default
     * null marks "nothing painted yet").
     */
    @SuppressWarnings("removal")
    private static RecordingGraphics allocateRecordingGraphics() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            return (RecordingGraphics) unsafe.allocateInstance(RecordingGraphics.class);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to allocate the recording graphics fake", e);
        }
    }

    private static int boxed(Integer recorded) {
        assertNotNull(recorded, "expected a recorded color at the graphics sink");
        return recorded;
    }
}
