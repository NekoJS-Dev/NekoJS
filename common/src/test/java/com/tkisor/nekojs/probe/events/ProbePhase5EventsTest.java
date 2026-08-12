package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5 三个 {@code probe.*} 事件的核心逻辑单测：assign_type 的 IR 应用、add_global/snippets 收集、
 * 以及全局类型的 TS 渲染。
 */
class ProbePhase5EventsTest {

    /** 含一个返回 java.io.File（SYMBOL）的实例方法，供 assign_type 重定向。 */
    public static class AssignSample {
        public java.io.File obtain() { return null; }
    }

    private final TypeScriptClassRenderer tsRenderer =
            new TypeScriptClassRenderer(new TypeConverter(new TypeAliasRegistry()));

    // -------------------- assign_type --------------------

    @Test
    void assignType_applyToOverridesSymbolSlotsAndMarksMutated() {
        TypeDecl d = new TypeReflector().reflect(AssignSample.class);
        // 应用前：obtain 返回 $File（SYMBOL，经 sourceType→TypeConverter 渲染）
        String before = tsRenderer.render(d);
        assertTrue(before.contains("obtain()"), "method present before: " + before);

        int count = ProbeAssignTypeEventJS.applyTo(d,
                Map.of("java.io.File", ApiTypeRef.primitive("string")));
        assertTrue(count >= 1, "at least one slot reassigned");
        assertTrue(d.mutated, "decl must be marked mutated so TS re-renders it");

        // 应用后：obtain 的返回槽 overridden → renderRef(primitive "string") → "string"
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
        // TS 渲染：primitive→bool 字面、symbol→$Name
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
