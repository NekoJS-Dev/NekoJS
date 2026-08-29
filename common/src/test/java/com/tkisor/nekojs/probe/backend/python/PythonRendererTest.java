package com.tkisor.nekojs.probe.backend.python;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.ir.FieldDecl;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ApiTypeRefPyRenderer} + {@link PythonClassRenderer} 单测：类型映射 + IR→.pyi 渲染。
 */
class PythonRendererTest {

    /** 反射样本：实例字段、getter、实例方法、静态方法、构造器（含 Java 重载）。 */
    public static class Sample {
        public String nameField;
        public Sample() {}
        public Sample(String name) { this.nameField = name; }   // 构造器重载
        public String getName() { return nameField; }   // getter → @property name
        public void greet(String who) {}
        public void greet(String who, int times) {}     // 与 greet(String) 构成重载
        public static int helper(int n) { return n; }
    }

    // -------------------- ApiTypeRefPyRenderer --------------------

    @Test
    void render_primitives() {
        ApiTypeRefPyRenderer r = new ApiTypeRefPyRenderer(Set.of());
        assertEquals("str", r.render(ApiTypeRef.primitive("string")));
        assertEquals("str", r.render(ApiTypeRef.primitive("char")));
        assertEquals("bool", r.render(ApiTypeRef.primitive("boolean")));
        assertEquals("int", r.render(ApiTypeRef.primitive("int")));
        assertEquals("float", r.render(ApiTypeRef.primitive("double")));
        assertEquals("Any", r.render(ApiTypeRef.primitive("object")));
        assertEquals("None", r.render(ApiTypeRef.voidType()));
    }

    @Test
    void render_arrayUnionTypeVar() {
        ApiTypeRefPyRenderer r = new ApiTypeRefPyRenderer(Set.of());
        assertEquals("list[str]", r.render(ApiTypeRef.array(ApiTypeRef.primitive("string"))));
        // union 成员在 ApiTypeRef 构造时按 compatibilityKey 排序：int < string → "int | str"
        assertEquals("int | str",
                r.render(ApiTypeRef.union(java.util.List.of(ApiTypeRef.primitive("string"), ApiTypeRef.primitive("int")))));
        assertEquals("Any", r.render(ApiTypeRef.typeVariable("T")));
    }

    @Test
    void render_symbolAvailability() {
        ApiTypeRefPyRenderer r = new ApiTypeRefPyRenderer(Set.of("net.x.Foo"));
        assertEquals("Foo", r.render(ApiTypeRef.symbol(new ApiSymbolId("java", "net.x.Foo"))));
        // 未收集的 SYMBOL → Any（避免悬空引用）
        assertEquals("Any", r.render(ApiTypeRef.symbol(new ApiSymbolId("java", "net.x.Bar"))));
    }

    @Test
    void simplePyName_nestedDollarToUnderscore() {
        assertEquals("Outer_Inner", ApiTypeRefPyRenderer.simplePyName("pkg.Outer$Inner"));
        assertEquals("Foo", ApiTypeRefPyRenderer.simplePyName("net.x.Foo"));
    }

    // -------------------- PythonClassRenderer --------------------

    @Test
    void renderClass_emitsFieldsGetterMethodsCtor() {
        TypeReflector reflector = new TypeReflector();
        TypeDecl d = reflector.reflect(Sample.class);
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(Set.of(d.fqn));
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        String out = classR.render(d);
        // 类声明（嵌套类名末尾为 _Sample）
        assertTrue(out.contains("class "), "class header: " + out);
        assertTrue(out.trim().startsWith("class "), out);
        // 实例字段
        assertTrue(out.contains("nameField: str"), out);
        // getter → @property + def name(self) -> str
        assertTrue(out.contains("@property"), out);
        assertTrue(out.contains("def name(self) -> str"), out);
        // 实例方法
        assertTrue(out.contains("def greet(self,"), out);
        assertTrue(out.contains(": str"), out);   // greet 的 String 参数
        // 静态方法
        assertTrue(out.contains("@staticmethod"), out);
        assertTrue(out.contains("def helper("), out);
        assertTrue(out.contains("-> int"), out);  // helper 返回 int
        // 构造器 → __init__
        assertTrue(out.contains("def __init__(self) -> None"), out);
    }

    @Test
    void renderClass_emitsMethodAndFieldDocs() {
        TypeReflector reflector = new TypeReflector();
        TypeDecl d = reflector.reflect(Sample.class);
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(Set.of(d.fqn));
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        // 类级 docs
        d.docs.add("Class doc line1");
        d.docs.add("Class doc line2");
        // 构造器 docs
        d.constructors.get(0).docs.add("Creates a sample.");
        // getter docs（@property 同样处理）
        MethodDecl getter = d.methods.stream().filter(m -> m.isGetter).findFirst().orElseThrow();
        getter.docs.add("The name.");
        // 实例方法 docs（多行）——docs 加到单参 greet（重载组内任一实例；断言按单参签名匹配）
        MethodDecl greet = d.methods.stream()
                .filter(m -> m.name.equals("greet") && m.params.size() == 1)
                .findFirst()
                .orElseThrow();
        greet.docs.add("Greets someone.");
        greet.docs.add("Second line.");
        // 字段 docs（多行 → 首行行尾注释 + 后续 `# ` 行）
        d.fields.get(0).docs.add("Field doc.");
        d.fields.get(0).docs.add("Second field line.");

        String out = classR.render(d);
        // 类级 docstring（多行）
        assertTrue(out.contains("    \"\"\"Class doc line1\nClass doc line2\"\"\""), out);
        // 构造器 docstring：def 行后换行缩进 8 空格
        assertTrue(out.contains("def __init__(self) -> None:\n        \"\"\"Creates a sample.\"\"\"\n        ..."), out);
        // getter（@property）docstring
        assertTrue(out.contains("def name(self) -> str:\n        \"\"\"The name.\"\"\"\n        ..."), out);
        // 实例方法 docstring（多行）——根 build.gradle 已全局开启 -parameters，参数名为真实名 who
        assertTrue(out.contains("def greet(self, who: str) -> None:\n        \"\"\"Greets someone.\nSecond line.\"\"\"\n        ..."), out);
        // 字段：行尾 `  # ...` + 后续行 `# ` 前缀
        assertTrue(out.contains("nameField: str  # Field doc.\n    # Second field line."), out);
    }

    @Test
    void renderClass_javaOverloadsUseOverloadDecorator() {
        // Sample 的 greet(String) / greet(String,int) 重载 + 双构造器 → 同名 def 全部标注 @overload，
        // 否则 Pylance 报「方法声明被同名声明遮盖」
        TypeDecl d = new TypeReflector().reflect(Sample.class);
        ApiTypeRefPyRenderer typeR = new ApiTypeRefPyRenderer(Set.of(d.fqn));
        PythonClassRenderer classR = new PythonClassRenderer(typeR);

        String out = classR.render(d);
        assertEquals(4, countOccurrences(out, "@overload"),
                "two greet overloads + two __init__ overloads must all carry @overload: " + out);
        assertTrue(out.contains("@overload\n    def greet(self, who: str) -> None"), out);
        assertTrue(out.contains("@overload\n    def greet(self, who: str, times: int) -> None"), out);
        assertTrue(out.contains("@overload\n    def __init__(self) -> None"), out);
        assertTrue(out.contains("@overload\n    def __init__(self, name: str) -> None"), out);

        assertTrue(PythonClassRenderer.hasOverloads(d));
        assertFalse(PythonClassRenderer.hasOverloads(new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Solo")));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    // -------------------- ClassEditor：renameClass / addMethod --------------------

    @Test
    void renameClass_usesNewPyName() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Foo");
        editorFor(d).renameClass("Renamed");

        String out = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of("pkg.Foo"))).render(d);
        assertTrue(out.contains("class Renamed:"), out);
        assertFalse(out.contains("class Foo"), "old class name should be gone: " + out);
    }

    @Test
    void renameClass_enumSelfRefsFollowInPy() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.ENUM, null, "pkg.Color");
        FieldDecl red = new FieldDecl("RED", TypeSlot.of(null, ApiTypeRef.symbol(new ApiSymbolId("java", "pkg.Color"))));
        red.isStatic = true;
        red.isEnumConstant = true;
        d.fields.add(red);

        editorFor(d).renameClass("Color2");

        String out = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of("pkg.Color"))).render(d);
        assertTrue(out.contains("class Color2:"), out);
        assertTrue(out.contains("RED: Color2"), "enum constant self-ref must follow rename: " + out);
    }

    @Test
    void addMethod_emitsPythonDef() {
        TypeDecl d = new TypeDecl(TypeDecl.Kind.CLASS, null, "pkg.Foo");
        // "a:string" 具名参数 + "int" 纯类型（参数名自动 arg1）；静态方法无 self
        editorFor(d).addMethod("foo", "boolean", "a:string", "int");
        editorFor(d).addStaticMethod("create", "string");

        String out = new PythonClassRenderer(new ApiTypeRefPyRenderer(Set.of("pkg.Foo"))).render(d);
        assertTrue(out.contains("def foo(self, a: str, arg1: int) -> bool: ..."), out);
        assertTrue(out.contains("@staticmethod"), out);
        assertTrue(out.contains("def create() -> str: ..."), out);
    }

    /** 经事件取得 ClassEditor（ClassEditor 构造器包私有，外部包只能走 forClass）。 */
    private static com.tkisor.nekojs.probe.events.ClassEditor editorFor(TypeDecl d) {
        return new ProbeModifyTypeEventJS(Map.of(d.fqn, d)).forClass(d.fqn);
    }
}
