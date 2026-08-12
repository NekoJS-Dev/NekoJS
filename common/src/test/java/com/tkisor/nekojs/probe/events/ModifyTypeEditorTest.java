package com.tkisor.nekojs.probe.events;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ClassEditor} + {@link ProbeModifyTypeEventJS} 的参数级编辑单测：
 * 反射 → 编辑 IR → 渲染，验证改名/改类型/隐藏/加参数/可选参数/import 收集均生效。
 *
 * <p>不依赖 -parameters 编译标志：按索引定位参数，按需从 IR 动态读取参数名。
 */
class ModifyTypeEditorTest {

    /** 反射用的样本类（public 方法 + public 字段，确保反射可见）。 */
    public static class Sample {
        public String nameField;
        public String greet(String name) { return "hi " + name; }
        public int compute(int x, int y) { return x + y; }
    }

    private final TypeReflector reflector = new TypeReflector();
    private final TypeScriptClassRenderer renderer =
            new TypeScriptClassRenderer(new TypeConverter(new TypeAliasRegistry()));

    // ---------------- resolveType ----------------

    @Test
    void resolveType_fqnStringBecomesSymbol() {
        ApiTypeRef ref = ProbeModifyTypeEventJS.resolveType("net.minecraft.world.item.ItemStack");
        assertEquals(ApiTypeRef.Kind.SYMBOL, ref.kind());
        assertEquals("java:net.minecraft.world.item.ItemStack", ref.name());
    }

    @Test
    void resolveType_plainNameBecomesPrimitive() {
        ApiTypeRef ref = ProbeModifyTypeEventJS.resolveType("string");
        assertEquals(ApiTypeRef.Kind.PRIMITIVE, ref.kind());
        assertEquals("string", ref.name());
    }

    @Test
    void resolveType_passesThroughApiTypeRef() {
        ApiTypeRef original = ApiTypeRef.primitive("number");
        assertSame(original, ProbeModifyTypeEventJS.resolveType(original));
    }

    @Test
    void resolveType_rejectsBlankAndUnknown() {
        assertThrows(IllegalArgumentException.class, () -> ProbeModifyTypeEventJS.resolveType(""));
        assertThrows(IllegalArgumentException.class, () -> ProbeModifyTypeEventJS.resolveType(null));
        assertThrows(IllegalArgumentException.class, () -> ProbeModifyTypeEventJS.resolveType(42));
    }

    // ---------------- override slot ----------------

    @Test
    void override_setsOverriddenFlagAndKeepsRef() {
        var slot = ProbeModifyTypeEventJS.override(null, "java.util.List");
        assertTrue(slot.overridden);
        assertNull(slot.sourceType);
        assertEquals(ApiTypeRef.Kind.SYMBOL, slot.ref.kind());
    }

    // ---------------- event: forClass / hasClass ----------------

    @Test
    void event_forClassUnknownReturnsNull() {
        TypeDecl d = reflector.reflect(Sample.class);
        ProbeModifyTypeEventJS event = new ProbeModifyTypeEventJS(Map.of(d.fqn, d));
        assertTrue(event.hasClass(d.fqn));
        assertFalse(event.hasClass("does.not.Exist"));
        assertNotNull(event.forClass(d.fqn));
        assertNull(event.forClass("does.not.Exist"));
    }

    // ---------------- rename / hide method ----------------

    @Test
    void renameMethod_reflectsInRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        String before = renderer.render(d);
        assertTrue(before.contains("greet("));

        new ClassEditor(d).renameMethod("greet", "hello");

        assertTrue(d.mutated);
        String after = renderer.render(d);
        assertFalse(after.contains("greet("), "old method name should be gone");
        assertTrue(after.contains("hello("), "renamed method should appear");
    }

    @Test
    void hideMethod_removesFromRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        assertTrue(renderer.render(d).contains("compute("));

        new ClassEditor(d).hideMethod("compute");

        assertTrue(d.mutated);
        assertFalse(renderer.render(d).contains("compute("));
    }

    @Test
    void missingMember_isSilentNoOp() {
        TypeDecl d = reflector.reflect(Sample.class);
        // 不存在的成员：no-op，不抛、不置 mutated
        new ClassEditor(d).renameMethod("nope", "x").hideField("absent");
        assertFalse(d.mutated);
    }

    // ---------------- change return type / param type ----------------

    @Test
    void changeReturnType_reflectsInRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeReturnType("compute", "java.lang.String");
        String rendered = renderer.render(d);
        // compute 的返回类型由 number 改为 $String
        assertTrue(rendered.contains("compute(") && rendered.contains("$String"));
    }

    @Test
    void changeParamType_byIndex_reflectsInRenderAndImports() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeParamType("compute", 0, "java.lang.String");

        String rendered = renderer.render(d);
        assertTrue(rendered.contains("$String"), "edited param type should render as $Symbol");

        // import 收集：compute 的首个参数改为 java.lang.String（跨包），应被收集
        Set<String> imps = ProbeModifyTypeEventJS.collectEditedSymbolFqns(d, pkgOf(d.fqn));
        assertTrue(imps.contains("java.lang.String"));
    }

    @Test
    void changeParamType_excludesSamePackageSymbol() {
        TypeDecl d = reflector.reflect(Sample.class);
        // 改成一个与 Sample 同包的「合成」类型名（仅验证同包剔除逻辑，无需真实类）
        d.methods.stream().filter(m -> m.name.equals("compute")).findFirst()
                .ifPresent(m -> m.params.get(0).type = ProbeModifyTypeEventJS.override(m.params.get(0).type, pkgOf(d.fqn) + ".Sibling"));
        Set<String> imps = ProbeModifyTypeEventJS.collectEditedSymbolFqns(d, pkgOf(d.fqn));
        assertFalse(imps.contains(pkgOf(d.fqn) + ".Sibling"), "same-package symbol must not produce a self-import");
    }

    // ---------------- add param ----------------

    @Test
    void addParam_appendsToAllOverloads() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).addParam("compute", "flag", "boolean");

        String rendered = renderer.render(d);
        assertTrue(rendered.contains("flag: boolean"), "added param should render");
    }

    // ---------------- mark optional (dynamic param name) ----------------

    @Test
    void markOptional_rendersQuestionMark() {
        TypeDecl d = reflector.reflect(Sample.class);
        MethodDecl compute = d.methods.stream().filter(m -> m.name.equals("compute")).findFirst().orElseThrow();
        String p0 = compute.params.get(0).name;

        new ClassEditor(d).markOptional("compute", p0);

        assertTrue(renderer.render(d).contains(p0 + "?:"),
                "optional param should render with trailing '?'");
    }

    // ---------------- field edits ----------------

    @Test
    void changeFieldType_reflectsInRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).changeFieldType("nameField", "number");
        String rendered = renderer.render(d);
        assertTrue(rendered.contains("nameField: number"));
    }

    @Test
    void hideField_removesFromRender() {
        TypeDecl d = reflector.reflect(Sample.class);
        assertTrue(renderer.render(d).contains("nameField"));
        new ClassEditor(d).hideField("nameField");
        assertFalse(renderer.render(d).contains("nameField"));
    }

    // ---------------- hide whole class ----------------

    @Test
    void hideClass_rendersEmpty() {
        TypeDecl d = reflector.reflect(Sample.class);
        new ClassEditor(d).hide();
        assertEquals("", renderer.render(d));
    }

    // ---------------- helpers ----------------

    private static String pkgOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }
}
