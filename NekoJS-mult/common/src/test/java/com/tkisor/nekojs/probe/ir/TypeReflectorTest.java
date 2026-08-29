package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TypeReflector} 的 JS 名重映射与 bridge 过滤测试：
 * probe 声明的名字必须与运行时 Graal remapper（{@code JavaMemberIndex.remapName}）一致。
 */
class TypeReflectorTest {

    @RemapByPrefix("neko$")
    public static class RemapSample {
        public String neko$data() { return "x"; }
        public String neko$getId() { return "id"; }
        public void neko$setId(String id) {}
        public String plainMethod() { return "p"; }
        @HideFromJS
        public void hiddenMethod() {}
    }

    /** 接口协变返回覆盖：javac 会生成 Object bridge（同参不同返回），必须过滤。 */
    public interface BaseSpec {
        default Object value() { return null; }
    }

    public interface ConcreteSpec extends BaseSpec {
        @Override
        String value();
    }

    @Test
    void remapByPrefixStripsPrefixIntoRenameTo() {
        TypeDecl decl = new TypeReflector().reflect(RemapSample.class);

        MethodDecl data = method(decl, "neko$data");
        assertEquals("data", data.effectiveName(), "neko$data must remap to data");
        assertFalse(data.isGetter, "data is not getter-shaped");

        MethodDecl plain = method(decl, "plainMethod");
        assertEquals("plainMethod", plain.effectiveName());
        assertNull(plain.renameTo, "plain method must keep null renameTo (byte-identical legacy output)");
    }

    @Test
    void getterSetterDetectionUsesRemappedName() {
        TypeDecl decl = new TypeReflector().reflect(RemapSample.class);

        // neko$getId → getId → getter 属性 id（与运行时 Graal getter 语义一致：脚本访问 .id）
        MethodDecl getter = decl.methods.stream()
                .filter(m -> m.isGetter && "id".equals(m.property))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected getter for property id in " + decl.methods));
        assertEquals("getId", getter.effectiveName());

        // neko$setId → setId → setter 配对
        MethodDecl setter = decl.methods.stream()
                .filter(m -> m.isSetter && m.effectiveName().equals("setId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected setter setId in " + decl.methods));
        assertEquals(1, setter.params.size());
    }

    @Test
    void hideFromJsExcludesMethod() {
        TypeDecl decl = new TypeReflector().reflect(RemapSample.class);
        // @HideFromJS 方法不进 IR（jsName == null 直接跳过），与运行时可见性一致
        assertTrue(decl.methods.stream().noneMatch(m -> m.name.equals("hiddenMethod")),
                "@HideFromJS methods must be excluded from probe output: " + decl.methods);
    }

    @Test
    void interfaceBridgeMethodsAreFiltered() {
        TypeDecl decl = new TypeReflector().reflect(ConcreteSpec.class);

        // ConcreteSpec.value() 的协变覆盖会让 javac 生成 Object value() bridge（同参不同返回，
        // Python 桩里是非法重载）——必须过滤，只留 String value()
        List<MethodDecl> values = decl.methods.stream().filter(m -> m.effectiveName().equals("value")).toList();
        assertEquals(1, values.size(), "bridge must be filtered: " + decl.methods);
        assertEquals("string", values.get(0).returnType.ref.name(), "non-bridge declaration must win");
    }

    // ==================== mixin/interface-injection 宿主类场景 ====================

    /** 注入接口：注解在接口上；宿主类实现它（mixin 注入后方法反射自宿主类）。 */
    @RemapByPrefix("neko$")
    public interface InjectedSpec {
        Object neko$data();
    }

    public static class InjectedHost implements InjectedSpec {
        @Override
        public String neko$data() { return "x"; }
        public void neko$setId(String id) {}
    }

    @Test
    void hostClassMethodsRemapViaInjectedInterfaceAnnotation() {
        // 运行时 Graal 经接口方法路径命中 @RemapByPrefix → JS 名 data；probe 反射宿主类
        // 方法（declaringClass=InjectedHost）也必须 remap，声明与运行时一致
        TypeDecl decl = new TypeReflector().reflect(InjectedHost.class);

        MethodDecl data = decl.methods.stream()
                .filter(m -> m.effectiveName().equals("data"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("neko$data must remap to data: " + decl.methods));
        assertEquals("neko$data", data.name, "java name preserved");
        // bridge 过滤：同一 neko$data 只保留一个声明（Object bridge 被剔除）
        assertEquals(1, decl.methods.stream().filter(m -> m.effectiveName().equals("data")).count(),
                "bridge must be filtered on host classes too: " + decl.methods);
    }

    private static MethodDecl method(TypeDecl decl, String javaName) {
        return decl.methods.stream()
                .filter(m -> m.name.equals(javaName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("method " + javaName + " not found in " + decl.methods));
    }
}
