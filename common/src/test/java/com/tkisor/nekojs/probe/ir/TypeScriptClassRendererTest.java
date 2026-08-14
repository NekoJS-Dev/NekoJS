package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 渲染稳定性回归：对一组有代表性的类，验证「TypeReflector 反射 + TypeScriptClassRenderer 渲染」
 * 的输出稳定且与 IR 结构一致。旧 ClassDeclGenerator 的逐字对齐 A/B 测试已在 Phase 2.7
 * 删除（旧渲染器已移除）；字节级端到端保障由 {@code TypeScriptNoopIrGoldenTest}（C1：ir=null
 * 与 ir=未编辑列表产出树逐字一致）与 {@code ProbeOutputCompatibilityTest}（legacy 产物存在性）
 * 继续承担。
 *
 * <p>覆盖：普通类、final 类、泛型类、接口（含嵌套）、枚举、含 getter/setter 的类。
 */
class TypeScriptClassRendererTest {

    private static final List<Class<?>> SAMPLES = List.of(
            java.util.ArrayList.class,        // 泛型 class
            java.lang.String.class,           // final class，大量方法
            java.util.List.class,             // 泛型 interface
            java.lang.Runnable.class,         // 简单 interface
            java.util.Map.class,              // 泛型 interface
            java.util.concurrent.TimeUnit.class, // enum
            java.io.File.class,               // 含大量 getter/setter
            java.util.Date.class,             // 含 getTime 等 getter
            java.util.HashMap.class,          // 泛型 class
            java.lang.Number.class            // 抽象 class
    );

    @Test
    void rendererOutputIsStableForAllSamples() {
        for (Class<?> cls : SAMPLES) {
            TypeReflector reflector = new TypeReflector();
            TypeDecl decl = reflector.reflect(cls);

            TypeAliasRegistry aliases1 = new TypeAliasRegistry();
            TypeScriptClassRenderer renderer1 = new TypeScriptClassRenderer(new TypeConverter(aliases1));
            String first = renderer1.render(decl);

            // 同一次反射的 IR 渲染两次结果一致（渲染器无状态副作用）
            TypeAliasRegistry aliases2 = new TypeAliasRegistry();
            TypeScriptClassRenderer renderer2 = new TypeScriptClassRenderer(new TypeConverter(aliases2));
            String second = renderer2.render(decl);
            assertEquals(first, second, "repeated render must be deterministic for " + cls.getName());

            // 基础形态断言（沿用旧渲染器的契约）
            assertTrue(first.startsWith("    export "), "decl must start with export block: " + cls.getName());
            assertTrue(first.trim().endsWith("}"), "decl must end with closing brace: " + cls.getName());
        }
    }

    // ---------------- 确定性 getter/setter 候选选择 ----------------

    /** 协变 getter 基类：子类以 String 覆盖 Object 返回（javac 会生成返回 Object 的 bridge）。 */
    public abstract static class CovariantGetterBase {
        public abstract Object getValue();
    }

    /** 协变覆盖：getValue() 返回 String，bridge 方法 getValue() 返回 Object（synthetic）。 */
    public static class CovariantGetterSub extends CovariantGetterBase {
        @Override
        public String getValue() { return "1"; }
    }

    @Test
    void covariantGetterWinsOverItsBridge() {
        TypeDecl decl = new TypeReflector().reflect(CovariantGetterSub.class);

        List<MethodDecl> getters = decl.methods.stream().filter(m -> m.isGetter && "value".equals(m.property)).toList();
        assertEquals(1, getters.size(), "duplicate getter candidates must be deduped to one");
        // 非 bridge 候选（协变覆盖）胜出：bridge 胜出会渲染出错误的超类型返回
        assertEquals(String.class, getters.get(0).returnType.sourceType,
                "covariant override must win over its bridge method");

        // String → "string"、Object → "object"：渲染上可区分两个候选
        String out = render(decl);
        assertTrue(out.contains("get value(): string;"), out);
        assertFalse(out.contains("get value(): object;"), out);
    }

    /** getFoo()/isFoo() 并存：同一属性两个候选，确定性取 get 形式（签名键字典序）。 */
    public static class GetIsCoexistence {
        public String getActive() { return "yes"; }
        public boolean isActive() { return true; }
    }

    @Test
    void coexistingGetAndIsFormsPickDeterministically() {
        TypeDecl decl = new TypeReflector().reflect(GetIsCoexistence.class);

        List<MethodDecl> getters = decl.methods.stream().filter(m -> m.isGetter && "active".equals(m.property)).toList();
        assertEquals(1, getters.size(), "duplicate getter candidates must be deduped to one");
        assertEquals(String.class, getters.get(0).returnType.sourceType,
                "get-form must win over is-form for the same property");

        String out = render(decl);
        assertTrue(out.contains("get active(): string;"), out);
    }

    /** 同名 setter 重载：配对入参槽确定性选取（非 bridge 优先 + 参数类型字典序）。 */
    public static class OverloadedSetter {
        public Object getLevel() { return 0; }
        public void setLevel(Number level) { }
        public void setLevel(Integer level) { }
    }

    @Test
    void overloadedSetterParamSlotIsDeterministic() {
        TypeDecl decl = new TypeReflector().reflect(OverloadedSetter.class);

        MethodDecl getter = decl.methods.stream()
                .filter(m -> m.isGetter && "level".equals(m.property)).findFirst().orElseThrow();
        assertNotNull(getter.setterParamType, "paired setter must be found");
        // java.lang.Integer < java.lang.Number（字典序）→ 取 Integer，与 getDeclaredMethods 顺序无关
        assertEquals(Integer.class, getter.setterParamType.sourceType);
    }

    // ---------------- extends 原始类型省略 ----------------

    /** Number 子类：superType 经 TypeConverter 映射为 number，extends 子句必须省略。 */
    public abstract static class MyNumber extends Number {}

    @Test
    void superClassMappingToTsPrimitiveOmitsExtends() {
        TypeDecl decl = new TypeReflector().reflect(MyNumber.class);
        String out = render(decl);
        assertTrue(out.contains("export class $TypeScriptClassRendererTest$MyNumber {"),
                "extends clause must be omitted entirely:\n" + out);
        assertFalse(out.contains("extends number"), "TS primitives cannot be heritage:\n" + out);
    }

    @Test
    void nonPrimitiveSuperClassStillRendersExtends() {
        TypeDecl decl = new TypeReflector().reflect(java.util.ArrayList.class);
        String out = render(decl);
        // superType 取 getSuperclass()（erased Class），故泛型上界不渲染类型实参
        assertTrue(out.contains("export class $ArrayList<E> extends $AbstractList implements"),
                "regular super class must keep its extends clause:\n" + out);
    }

    private static String render(TypeDecl decl) {
        return new TypeScriptClassRenderer(new TypeConverter(new TypeAliasRegistry())).render(decl);
    }
}
