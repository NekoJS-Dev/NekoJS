package com.tkisor.nekojs.api.ui;

import com.tkisor.nekojs.core.NekoSharedHostAccess;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.fs.ClassFilter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Defect D6 regression (ticket 26): script-side ARGB color literals are unsigned JS
 * numbers ≥ 2³¹ ({@code 0xFFFFFF00} = 4294967040). This suite pins both halves of the
 * boundary contract on the real GraalJS engine:
 *
 * <ul>
 *   <li><b>Mechanism (characterization)</b>: a host method that declares the color as a
 *       Java {@code int} receives {@code Integer.MAX_VALUE} — the engine's number→int
 *       host conversion saturates instead of wrapping, which is why the pre-fix painters
 *       rendered {@code 0xFF......} colors as translucent white. Production code must not
 *       rely on this; script-facing color parameters declare {@link Number}.</li>
 *   <li><b>Fix seam</b>: a {@link Number}-typed color parameter receives the true value,
 *       and {@link UiColor#argbBits(Number)} reads it as ARGB bits (unsigned literals
 *       wrap to negative int32s, Java int32 bit patterns pass through unchanged).</li>
 * </ul>
 *
 * <p>Test geometry: a real GraalJS {@link Context} wired like {@code NekoSandboxFactory.build}
 * (same {@link NekoSharedHostAccess} + {@link ClassFilter} + interop options, precedent
 * {@code DataGeneratorJsJsonRealEngineTest}); the probes expose the exact seam shapes a
 * production binding goes through and are called from JS. The full painter seam over the
 * production {@code PainterJS} is covered in the version-tree suite
 * {@code PainterJSScriptColorBoundaryTest}.
 */
class ScriptColorBoundaryRealEngineTest {

    /** Host probe with the pre-fix seam shape: a raw {@code int} color parameter. */
    public static final class IntColorSeamProbe {
        public int color(int color) {
            return color;
        }
    }

    /** Host probe with the fixed seam shape: a {@link Number} color normalized to ARGB bits. */
    public static final class NumberColorSeamProbe {
        public int color(Number color) {
            return UiColor.argbBits(color);
        }
    }

    private Context newScriptContext(Object probe) {
        NekoSharedHostAccess hostAccess = new NekoSharedHostAccess(List.of());
        ClassFilter classFilter = new ClassFilter(SandboxConfig.defaultConfig());
        Context context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .allowHostAccess(hostAccess.get())
                .allowHostClassLookup(classFilter)
                .allowCreateProcess(false)
                .allowValueSharing(true)
                .option("js.foreign-object-prototype", "true")
                .option("js.nashorn-compat", "true")
                .option("js.ecmascript-version", "latest")
                .option("js.strict", "true")
                .build();
        context.getBindings("js").putMember("probe", probe);
        return context;
    }

    /**
     * Mechanism characterization: the engine's number→int host conversion saturates
     * out-of-range values to {@code Integer.MAX_VALUE} (= {@code 0x7FFFFFFF}, translucent
     * white) instead of wrapping to the int32 bits. If a Graal upgrade ever changes this,
     * this assertion flags the re-evaluation; production seams do not rely on it.
     */
    @Test
    void intParametersSaturateInsteadOfWrapping() {
        try (Context context = newScriptContext(new IntColorSeamProbe())) {
            assertEquals(2147483647, context.eval("js", "probe.color(0xFFFFFF00)").asInt(),
                    "engine saturates 0xFFFFFF00 on an int parameter");
            assertEquals(2147483647, context.eval("js", "probe.color(4294967040)").asInt(),
                    "engine saturates the same value as a decimal literal");
        }
    }

    /** The defect's headline case through the fixed seam: yellow arrives as its int32 bits. */
    @Test
    void uint32LiteralsArriveAsArgbBitsThroughNumberSeam() {
        try (Context context = newScriptContext(new NumberColorSeamProbe())) {
            Value fromHex = context.eval("js", "probe.color(0xFFFFFF00)");
            assertEquals(-256, fromHex.asInt(), "0xFFFFFF00 (yellow) bits");
            Value fromDecimal = context.eval("js", "probe.color(4294967040)");
            assertEquals(-256, fromDecimal.asInt(), "4294967040 (same value, decimal literal) bits");
            Value opaqueWhite = context.eval("js", "probe.color(0xFFFFFFFF)");
            assertEquals(-1, opaqueWhite.asInt(), "0xFFFFFFFF (opaque white) bits");
        }
    }

    /** Values already inside int32 were never broken; the fix must not disturb them. */
    @Test
    void int32RangeValuesPassThroughNumberSeamUnchanged() {
        try (Context context = newScriptContext(new NumberColorSeamProbe())) {
            assertEquals(-256, context.eval("js", "probe.color(-256)").asInt(),
                    "negative int32 from script bitwise color math");
            assertEquals(5, context.eval("js", "probe.color(5)").asInt(), "small positive");
            assertEquals(0, context.eval("js", "probe.color(0)").asInt(), "zero");
        }
    }
}
