package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraalJS runtime contract fixture（ticket 39 AC8）：固定「显式 setter 与 JavaBean-style
 * property assignment 调用<b>同一个 setter</b>，并进入同一校验、规范化、声明与
 * definition fingerprint 路径」。
 *
 * <p>事实依据（旧路径 characterization，重构前实测）：宿主视图的 property 写在
 * GraalJS 上被静默丢弃（{@code assigned,read=undefined,pending=null}，不落 setter）——
 * javadoc 示例形态从未生效。{@link ModificationViewSurface}（ProxyObject putMember seam，
 * 票 15 BuilderSurface 同款手法）把两种写法转发到同一 {@code Method}。本测试在真实
 * GraalJS Context 里钉住该契约；无 vanilla 注册表依赖（视图 + 声明指纹不触碰注册表），
 * 五节点全部真跑（26.x 视图含 food/tool 面，1.21.1 视图只有四个基础属性——成员差异
 * 由守卫表达，基础属性 parity 面两侧一致）。
 */
class ModificationSetterPropertyParityTest {

    /** 在真实 GraalJS Context 里对 surface 化的视图执行一段脚本（脚本是第一书写者）。 */
    private static ItemModificationJS evalOn(String script) {
        ItemModificationJS view = new ItemModificationJS();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("item", ModificationViewSurface.of(view));
            context.eval("js", script);
        }
        return view;
    }

    /** 视图 → 声明（固定 target；不触碰注册表——注册表解析归 vanilla-gated E2E）。 */
    private static ModificationDeclaration declarationOf(ItemModificationJS view) {
        return new ModificationDeclaration("item", "parity:target", view.normalizedProperties(), "parity.js");
    }

    private static String fingerprintOf(ModificationDeclaration declaration) {
        return ModificationCandidatePlan.fingerprintOf("parity-adapter", List.of(declaration), null);
    }

    @Test
    void propertyAssignmentAndExplicitSetterProduceSameNormalizedDeclarationAndFingerprint() {
        ModificationDeclaration viaProperty = declarationOf(
                evalOn("item.maxStackSize = 16; item.rarity = 'EPIC'; item.maxDamage = 0;"));
        ModificationDeclaration viaSetter = declarationOf(
                evalOn("item.setMaxStackSize(16); item.setRarity('epic'); item.setMaxDamage(0);"));

        assertEquals(viaSetter.properties(), viaProperty.properties(),
                "两种写法必须产生同一规范化声明（rarity 小写归一）");
        assertEquals("epic", viaProperty.properties().get("rarity"), "rarity 按序列名归一");
        assertEquals(16, viaProperty.properties().get("maxStackSize"));
        assertEquals(fingerprintOf(viaSetter), fingerprintOf(viaProperty),
                "两种写法必须产生同一 definition fingerprint");
    }

    @Test
    void numericShapesNormalizeToTheSameFingerprint() {
        // setter 侧 int/double 收窄差异不得产生不同计划：16 与 16.0 同指纹
        ModificationDeclaration intShape = declarationOf(evalOn("item.maxStackSize = 16;"));
        ModificationDeclaration doubleShape = declarationOf(evalOn("item.maxStackSize = 16.0;"));
        assertEquals(fingerprintOf(intShape), fingerprintOf(doubleShape));

        // 真值不同的声明指纹必须可区分（不是恒等字符串）
        ModificationDeclaration different = declarationOf(evalOn("item.maxStackSize = 17;"));
        assertNotEquals(fingerprintOf(intShape), fingerprintOf(different));
    }

    @Test
    void invalidValuesFailWithTheSameMemberNamedErrorFromBothWriteStyles() {
        RuntimeException propertyError = assertThrows(RuntimeException.class,
                () -> evalOn("item.rarity = 'legendary';"));
        RuntimeException setterError = assertThrows(RuntimeException.class,
                () -> evalOn("item.setRarity('legendary');"));

        assertTrue(propertyError.getMessage().contains("legendary"),
                "property 写错误带成员上下文: " + propertyError.getMessage());
        assertEquals(setterError.getMessage(), propertyError.getMessage(),
                "两种写法的校验错误必须同源（同一 setter 抛出）");
    }

    @Test
    void unknownMemberWriteIsRejectedWithMemberContext() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("item.definitelyNotAMember = 1;"));
        assertTrue(error.getMessage().contains("definitelyNotAMember"),
                "未知成员错误列出成员名: " + error.getMessage());
        assertTrue(error.getMessage().contains("maxStackSize"),
                "未知成员错误给出已知成员目录: " + error.getMessage());
    }

    @Test
    void typeMismatchedPropertyWriteIsRejectedWithMemberContext() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn("item.maxStackSize = 'lots';"));
        assertTrue(error.getMessage().contains("maxStackSize"),
                "类型不匹配错误带成员名: " + error.getMessage());
    }

    // ---- 26.x 独有成员面（food/tool）：对象字面量经 property 写进入同一 setter ----
//? if >=26 {
    @Test
    void foodObjectLiteralViaPropertyWriteReachesTheSameSetter() {
        ModificationDeclaration viaProperty = declarationOf(evalOn(
                "item.food = { nutrition: 4, saturation: 0.6, canAlwaysEat: true, eatSeconds: 1.6 };"));
        @SuppressWarnings("unchecked")
        Map<String, Object> food = (Map<String, Object>) viaProperty.properties().get("food");
        assertEquals(4, ((Number) food.get("nutrition")).intValue());
        assertEquals(0.6f, ((Number) food.get("saturation")).floatValue(), 1.0e-6f);
        assertEquals(Boolean.TRUE, food.get("canAlwaysEat"));
        assertEquals(1.6f, ((Number) food.get("eatSeconds")).floatValue(), 1.0e-6f);

        // null property 写 = 移除请求（与旧 setFood(null) 语义一致）
        ModificationDeclaration removal = declarationOf(evalOn("item.food = null;"));
        assertTrue(removal.properties().containsKey("food") && removal.properties().get("food") == null,
                "item.food = null 记录移除请求（不是未设置）");
    }

    @Test
    void invalidFoodSpecFailsFromBothWriteStylesWithSameError() {
        RuntimeException propertyError = assertThrows(RuntimeException.class,
                () -> evalOn("item.food = { nutrition: -1 };"));
        RuntimeException setterError = assertThrows(RuntimeException.class,
                () -> evalOn("item.setFood({ nutrition: -1 });"));
        assertEquals(setterError.getMessage(), propertyError.getMessage());
        assertTrue(propertyError.getMessage().contains("nutrition"),
                "错误带选项名: " + propertyError.getMessage());
    }
//?}
}
