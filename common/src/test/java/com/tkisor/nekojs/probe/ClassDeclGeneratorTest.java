package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import com.tkisor.nekojs.probe.types.TypeConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClassDeclGenerator} 测试：Java 的 getXxx()/isXxx() getter 在 TS 声明里同时以
 * 属性（{@code get prop(): T}）与方法（{@code getXxx(): T}）两种形式暴露，
 * 与 GraalJS host object 运行时（如 {@code event.getEntity()}）保持一致——
 * 否则 TS getter 仅允许 {@code .xxx} 属性访问，调用 {@code getXxx()} 会补全缺失并报错。
 */
class ClassDeclGeneratorTest {

    /** 样例类：含 getXxx()/isXxx() getter 与普通实例方法。 */
    public static class Sample {
        public String getName() { return "x"; }
        public boolean isActive() { return false; }
        public void doWork() {}
    }

    /** 样例类：含重载方法与额外 public helper。 */
    public static class OverloadedSample {
        public String find(String id) { return id; }
        public String find(int index) { return String.valueOf(index); }
        public String internalHelper() { return "legacy-visible"; }
    }

    @Test
    void getterEmittedAsBothPropertyAndMethod() {
        ClassDeclGenerator gen = new ClassDeclGenerator(new TypeConverter(new TypeAliasRegistry()));
        String decl = gen.generate(Sample.class);

        // TS getter（属性访问 .name / .active）
        assertTrue(decl.contains("get name():"), decl);
        assertTrue(decl.contains("get active():"), decl);
        // 同名方法形式（getEntity() 式调用，与 GraalJS 运行时一致；此前缺失导致调用报错）
        assertTrue(decl.contains("getName():"), decl);
        assertTrue(decl.contains("isActive():"), decl);
        // getter 与方法形式返回类型必须一致（同一 TypeConverter 输出）
        assertEquals(typeAfter(decl, "get name():"), typeAfter(decl, "getName():"));
        assertEquals(typeAfter(decl, "get active():"), typeAfter(decl, "isActive():"));
        // 普通实例方法不受影响
        assertTrue(decl.contains("doWork():"), decl);
    }

    @Test
    void legacyGeneratorKeepsBothOverloads() {
        String decl = new ClassDeclGenerator(new TypeConverter(new TypeAliasRegistry()))
                .generate(OverloadedSample.class);
        assertEquals(2, count(decl, "find("), decl);
        assertTrue(decl.contains("internalHelper():"), decl);
    }

    /** 取 decl 中 prefix 之后的类型文本（到分号为止），用于比较 getter/方法形式类型是否一致。 */
    private static String typeAfter(String decl, String prefix) {
        int i = decl.indexOf(prefix);
        String rest = decl.substring(i + prefix.length());
        return rest.substring(0, rest.indexOf(';')).trim();
    }

    /** 计算 decl 中 target 出现的次数。 */
    private static int count(String decl, String target) {
        int n = 0;
        int idx = 0;
        while ((idx = decl.indexOf(target, idx)) != -1) {
            n++;
            idx += target.length();
        }
        return n;
    }
}
