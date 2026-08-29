package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注解文档（{@code @Doc}/{@code @Param}/{@code @Return}）→ IR docs → TS JSDoc 的回归。
 * 无注解成员 docs 必须为空（渲染零输出，守护未注解路径与旧产物逐字节一致）。
 */
class AnnotatedDocsTest {

    @Doc("Fixture type for annotation doc tests.")
    public static class Fixture {
        @Doc("Tick count in game ticks.")
        public static final int TICKS = 20;

        @Doc("Resolves an id to an item stack.")
        @Doc("Accepts plain ids and '#tag' selectors.")
        @Param(name = "id", value = "item id, tag, or item-like object")
        @Param(name = "count", value = "stack size, clamped to positive")
        @Return("the resolved stack; never null")
        public Fixture of(String id, int count) {
            return this;
        }

        public int untouched() {
            return 0;
        }
    }

    @Test
    void typeAndFieldAndMethodDocsFlowIntoIr() {
        TypeDecl decl = new TypeReflector().reflect(Fixture.class);

        assertEquals(java.util.List.of("Fixture type for annotation doc tests."), decl.docs);

        var ticks = decl.fields.stream().filter(f -> f.name.equals("TICKS")).findFirst().orElseThrow();
        assertEquals(java.util.List.of("Tick count in game ticks."), ticks.docs);

        var of = decl.methods.stream()
                .filter(m -> m.name.equals("of")).findFirst().orElseThrow();
        assertEquals(java.util.List.of(
                "Resolves an id to an item stack.",
                "Accepts plain ids and '#tag' selectors.",
                "@param id item id, tag, or item-like object",
                "@param count stack size, clamped to positive",
                "@returns the resolved stack; never null"), of.docs);

        var untouched = decl.methods.stream()
                .filter(m -> m.name.equals("untouched")).findFirst().orElseThrow();
        assertTrue(untouched.docs.isEmpty(), "无注解成员 docs 必须为空（渲染零输出）");
    }

    @Test
    void rendererEmitsJsdocForAnnotatedMembersOnly() {
        TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(
                new com.tkisor.nekojs.probe.types.TypeAliasRegistry());
        String out = renderer.render(new TypeReflector().reflect(Fixture.class));

        assertTrue(out.contains("/** Fixture type for annotation doc tests. */"), "类级 JSDoc 应输出:\n" + out);
        assertTrue(out.contains("@param id item id, tag, or item-like object"), "@param 行应输出:\n" + out);
        assertTrue(out.contains("@returns the resolved stack; never null"), "@returns 行应输出:\n" + out);
        assertFalse(out.contains("untouched()") && out.contains("*/\n    untouched"),
                "无注解方法不应带 JSDoc:\n" + out);
    }
}
