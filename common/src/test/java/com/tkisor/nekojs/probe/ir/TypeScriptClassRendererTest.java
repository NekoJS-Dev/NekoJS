package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.probe.ClassDeclGenerator;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B 回归：对一组有代表性的类，验证「TypeReflector 反射 + TypeScriptClassRenderer 渲染」的输出
 * 与旧 {@link ClassDeclGenerator}（直接反射 Class→String）逐字一致。这是 Phase 2 IR 不破坏 TS 产出的核心保障。
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
    void rendererMatchesClassDeclGeneratorForAllSamples() {
        for (Class<?> cls : SAMPLES) {
            // 旧路径：ClassDeclGenerator 直接渲染
            TypeAliasRegistry aliases1 = new TypeAliasRegistry();
            TypeConverter tc1 = new TypeConverter(aliases1);
            ClassDeclGenerator gen = new ClassDeclGenerator(tc1);
            String expected = gen.generate(cls);

            // 新路径：TypeReflector → IR → TypeScriptClassRenderer
            TypeAliasRegistry aliases2 = new TypeAliasRegistry();
            TypeConverter tc2 = new TypeConverter(aliases2);
            TypeReflector reflector = new TypeReflector();
            TypeDecl decl = reflector.reflect(cls);
            TypeScriptClassRenderer renderer = new TypeScriptClassRenderer(tc2);
            String actual = renderer.render(decl);

            assertEquals(expected, actual,
                    "IR render must byte-match ClassDeclGenerator for " + cls.getName());
        }
    }
}
