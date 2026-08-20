package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.ir.MethodDecl;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;
import com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer;
import com.tkisor.nekojs.probe.ir.TypeSlot;
import com.tkisor.nekojs.probe.types.TypeAliasRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IndexFileGenerator} 枚举字面量输入别名（{@code $Enum_ = $Enum | "A" | ...}）：
 * 声明发射（常量序对齐 renderEnum）、参数放宽（{@link TypeAliasRegistry} 对枚举 FQN 的
 * 惰性别名解析，input-only）、跨包 import 追加（对齐适配器别名 {@code $Foo_} 的导入机制）。
 */
class IndexFileGeneratorTest {

    private static IndexFileGenerator generator(TypeAliasRegistry registry) {
        return new IndexFileGenerator(new TypeScriptClassRenderer(registry),
                new AdapterAliasGenerator(registry));
    }

    @Test
    void enumAliasDeclarationEmittedAfterEnumClass() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        TypeDecl color = new TypeReflector().reflect(ProbeTestColor.class);
        gen.predeclareClass(color.fqn, color, Set.of());

        String out = gen.generate("com.tkisor.nekojs.probe", List.of("ProbeTestColor"),
                List.of(), Set.of(color.fqn));
        assertTrue(out.contains("export class $ProbeTestColor {"), out);
        // 常量序 = renderEnum 的静态常量序（TypeReflector 已按名字稳定排序：BLUE, GREEN, RED）
        assertTrue(out.contains("export type $ProbeTestColor_ = $ProbeTestColor | \"BLUE\" | \"GREEN\" | \"RED\";"),
                "enum literal alias declaration missing: " + out);
    }

    @Test
    void enumParamWidenedToEnumAlias() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        TypeDecl user = new TypeReflector().reflect(ProbeTestColorUser.class);
        gen.predeclareClass(user.fqn, user, Set.of());

        String out = gen.generate("com.tkisor.nekojs.probe", List.of("ProbeTestColorUser"),
                List.of(), Set.of(user.fqn));
        // 枚举参数放宽为 $ProbeTestColor_（同包：别名声明在枚举所在模块内，无需 import）
        assertTrue(out.contains(": $ProbeTestColor_)"), "enum param should widen to alias: " + out);
        assertFalse(out.contains(": $ProbeTestColor)"), "unwidened enum param leaked: " + out);
    }

    @Test
    void crossPackageEnumAliasImportedAlongsideClass() {
        IndexFileGenerator gen = generator(new TypeAliasRegistry());
        TypeDecl color = new TypeReflector().reflect(ProbeTestColor.class);
        gen.predeclareClass(color.fqn, color, Set.of());

        // 合成跨包类：paint(ProbeTestColor) → 放宽为 $ProbeTestColor_ → import 需一并带别名（对齐适配器别名机制）
        TypeDecl user = new TypeDecl(TypeDecl.Kind.CLASS, null, "other.pkg.ColorUser");
        MethodDecl paint = new MethodDecl("paint");
        paint.returnType = TypeSlot.of(void.class, ApiTypeRef.voidType());
        paint.params.add(new MethodDecl.MethodParam("color",
                TypeSlot.of(ProbeTestColor.class,
                        ApiTypeRef.symbol(new ApiSymbolId("java", ProbeTestColor.class.getName()))),
                false));
        user.methods.add(paint);
        gen.predeclareClass(user.fqn, user, Set.of());

        String out = gen.generate("other.pkg", List.of("ColorUser"), List.of(),
                Set.of(user.fqn, color.fqn));
        assertTrue(out.contains("paint(color: $ProbeTestColor_)"), out);
        assertTrue(out.contains("import { $ProbeTestColor, $ProbeTestColor_ } from \"java:com/tkisor/nekojs/probe\";"),
                "enum alias must be imported cross-package: " + out);
    }

    @Test
    void registryResolvesEnumAliasesLazilyInputOnly() {
        TypeAliasRegistry registry = new TypeAliasRegistry();
        // 惰性解析：枚举 FQN 恒有 $<TS名>_ 输入别名（嵌套枚举为 $Parent$Child_）
        assertTrue(registry.hasAlias("java.time.DayOfWeek"));
        assertEquals("$DayOfWeek_", registry.getAlias("java.time.DayOfWeek"));
        assertEquals("$Thread$State_", registry.getAlias("java.lang.Thread$State"));
        // 非枚举且无显式别名 → 无别名（不放宽）
        assertFalse(registry.hasAlias("java.util.ArrayList"));

        // 输入别名消费：仅 input（参数）放宽，返回值保持完整类型（经渲染器，TypeConverter 已删）
        assertEquals("$DayOfWeek_", com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer.renderTypeRef(
                com.tkisor.nekojs.probe.ir.TypeReflector.toRef(java.time.DayOfWeek.class), registry, true));
        assertEquals("$DayOfWeek", com.tkisor.nekojs.probe.ir.TypeScriptClassRenderer.renderTypeRef(
                com.tkisor.nekojs.probe.ir.TypeReflector.toRef(java.time.DayOfWeek.class), registry, false));

        // 显式注册别名优先于枚举惰性别名（与适配器别名同一注册表出口）
        registry.registerClassAlias("java.time.DayOfWeek", "string");
        assertEquals("string", registry.getAlias("java.time.DayOfWeek"));
    }
}

/** 枚举夹具：真实反射路径（常量按名字排序 → BLUE, GREEN, RED）。 */
enum ProbeTestColor {
    RED, GREEN, BLUE
}

/** 枚举参数夹具：paint 的参数应被放宽为 $ProbeTestColor_。 */
class ProbeTestColorUser {
    public void paint(ProbeTestColor color) {}
}
