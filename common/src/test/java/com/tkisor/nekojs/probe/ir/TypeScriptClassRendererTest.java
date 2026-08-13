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
}
