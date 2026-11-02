package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.ScriptOptionSchema;
import graal.graalvm.polyglot.Context;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderOptionsTest {
    private final ScriptOptionSchema schema = new ScriptOptionSchema(
            ScriptOptionSchema.choice("layer", "background", "normal", "foreground"),
            ScriptOptionSchema.integer("priority", Integer.MIN_VALUE, Integer.MAX_VALUE));

    @Test
    void strictFieldsRetainCompatibleCaseAndNumericShorthands() {
        assertEquals(Map.of("layer", "foreground", "priority", -12),
                schema.validate(Map.of("layer", " FOREGROUND ", "priority", -12.0)));
        assertEquals(Map.of(), schema.validate(Map.of()));
        for (Object priority : new Object[] {1.2, Double.NaN, Double.POSITIVE_INFINITY, 2147483648L, "1", true}) {
            assertThrows(IllegalArgumentException.class, () -> schema.validate(Map.of("priority", priority)));
        }
        assertThrows(IllegalArgumentException.class, () -> schema.validate(Map.of("layer", "late")));
        assertThrows(IllegalArgumentException.class, () -> schema.validate(Map.of("ignored", 1)));
        assertThrows(IllegalArgumentException.class, () -> schema.validate(null));
    }

    @Test
    void guestObjectsDoNotSilentlyCoerceOrDropInvalidFields() {
        try (Context context = Context.newBuilder("js").build()) {
            assertEquals(Map.of("priority", 4), schema.read(context.eval("js", "({priority:4})")));
            for (String expression : new String[] {"null", "[]", "()=>{}", "({priority:1.1})", "({priority:NaN})",
                    "({priority:2147483648})", "({priority:'4'})", "({priority:null})", "({layer:3})", "({extra:true})"}) {
                assertThrows(IllegalArgumentException.class, () -> schema.read(context.eval("js", expression)), expression);
            }
            assertEquals("normal", schema.readField("layer", context.eval("js", "'Normal'")));
            assertEquals(Integer.MIN_VALUE, schema.readField("priority", context.eval("js", "-2147483648")));
        }
    }

    @Test
    void sourceMapsAreDetachedAndInvalidEntriesAreNotFiltered() {
        ScriptOptionSchema shaders = new ScriptOptionSchema(ScriptOptionSchema.strings("sources"));
        Map<String, String> source = new java.util.LinkedHashMap<>(Map.of("nekojs:shader", "source"));
        Map<String, Object> result = shaders.validate(Map.of("sources", source));
        source.clear();
        assertEquals(Map.of("nekojs:shader", "source"), result.get("sources"));
        assertThrows(IllegalArgumentException.class, () -> shaders.validate(Map.of("sources", Map.of("shader", 1))));
    }
}
