package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the three {@code probe.*} events: applying assign_type to the IR, collecting add_global and snippets,
 * and rendering global types to TS.
 */
class ProbePhase5EventsTest {

    /** Has one instance method that returns java.io.File (a SYMBOL), so assign_type can retarget it. */
    public static class AssignSample {
        public java.io.File obtain() { return null; }
    }

    private final TypeScriptClassRenderer tsRenderer =
            new TypeScriptClassRenderer(new TypeAliasRegistry());

    // -------------------- assign_type --------------------

    @Test
    void assignType_applyToOverridesSymbolSlotsAndMarksMutated() {
        TypeDecl d = new TypeReflector().reflect(AssignSample.class);
        // Before applying: obtain returns $File, a SYMBOL rendered through sourceType and TypeConverter
        String before = tsRenderer.render(d);
        assertTrue(before.contains("obtain()"), "method present before: " + before);

        int count = ProbeAssignTypeEventJS.applyTo(d,
                Map.of("java.io.File", ApiTypeRef.primitive("string")));
        assertTrue(count >= 1, "at least one slot reassigned");
        assertTrue(d.mutated, "decl must be marked mutated so TS re-renders it");

        // After applying: obtain's return slot is overridden and renders as the primitive "string"
        String after = tsRenderer.render(d);
        assertTrue(after.contains("obtain(): string"),
                "reassigned return type should render as string: " + after);
    }

    @Test
    void assignType_applyToNoOpForUnassignedFqn() {
        TypeDecl d = new TypeReflector().reflect(AssignSample.class);
        int count = ProbeAssignTypeEventJS.applyTo(d, Map.of("nonexistent.X", ApiTypeRef.primitive("int")));
        assertEquals(0, count);
        assertFalse(d.mutated, "untouched decl must not be mutated");
    }

    @Test
    void assignType_eventCollectsAssignments() {
        var ev = new ProbeAssignTypeEventJS();
        ev.assign("java.io.File", "string");
        ev.assign("net.x.Foo", ApiTypeRef.symbol(new ApiSymbolId("java", "net.y.Bar")));
        assertEquals(ApiTypeRef.primitive("string"), ev.assignments().get("java.io.File"));
        assertTrue(ev.has("net.x.Foo"));
    }

    // -------------------- add_global --------------------

    @Test
    void addGlobal_collectsAndResolvesTypes() {
        var ev = new ProbeAddGlobalEventJS();
        ev.add("MyFlag", "boolean");
        ev.add("Helper", "com.example.Helper");
        var globals = ev.globals();
        assertEquals(2, globals.size());
        assertEquals("MyFlag", globals.get(0).name());
        assertEquals(ApiTypeRef.Kind.PRIMITIVE, globals.get(0).type().kind());
        assertEquals(ApiTypeRef.Kind.SYMBOL, globals.get(1).type().kind());
        // TS rendering: a primitive renders as a literal such as bool, a symbol renders as $Name
        assertEquals("boolean", TypeScriptClassRenderer.renderTypeRef(globals.get(0).type()));
        assertEquals("$Helper", TypeScriptClassRenderer.renderTypeRef(globals.get(1).type()));
    }

    @Test
    void addGlobal_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new GlobalDecl("", ApiTypeRef.primitive("int")));
    }

    // -------------------- snippets --------------------

    @Test
    void snippets_collects() {
        var ev = new ProbeSnippetEventJS();
        ev.add("log", "lg", "console.log(1)", "log it");
        ev.add("if", "iff", "if (x) {}");
        var snips = ev.snippets();
        assertEquals(2, snips.size());
        assertEquals("lg", snips.get(0).prefix());
        assertEquals("log it", snips.get(0).description());
        assertNull(snips.get(1).description(), "description defaults null when omitted");
    }
}
