package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GraalJS runtime contract fixture（ticket 39 AC8）：固定「显式 setter 与 JavaBean-style
 * property assignment 调用<b>同一个 setter</b>，并进入同一校验、规范化、声明与
 * definition fingerprint 路径」。
 *
 * <p>事实依据（旧路径 characterization，重构前实测）：宿主视图的 property 写在
 * GraalJS 上被静默丢弃（{@code assigned,read=undefined,pending=null}，不落 setter）——
 * javadoc 示例形态从未生效（现行复现见 {@link ModificationLegacyCharacterizationTest} 的
 * 探针：普通宿主对象仍然如此）。{@link ModificationViewSurface}（ProxyObject putMember
 * seam，票 15 BuilderSurface 同款手法）把两种写法转发到同一 {@code Method}。
 *
 * <p>本测试在真实 GraalJS Context 里钉住契约，并同时覆盖<b>两种到达形态</b>：
 * <ul>
 *   <li>{@code surface} 包装形态（{@code ModificationViewSurface.of(view)} 投给脚本）；</li>
 *   <li><b>生产投递形态</b>：脚本函数经<b>沙盒 HostAccess</b>
 *       （{@code NekoSharedHostAccess}，{@code allowAllImplementations}）实现成
 *       {@code Consumer}，宿主以<b>裸视图</b>调用 {@code callback.accept(view)}——与
 *       {@code ItemModificationEventJS#modify} 的 {@code modifier.accept(view)} 完全同形。
 *       裸视图靠自身实现的 ProxyObject 被 GraalJS honor。</li>
 * </ul>
 *
 * <p>无 vanilla 注册表依赖（视图 + 声明指纹不触碰注册表）。真跑节点：本机实测
 * 26.1.2 / 26.1.2-fabric / 1.21.1 / 26.2.0 / 26.2.0-fabric（26.x 视图含 food/tool 面，
 * 1.21.1 视图只有四个基础属性——成员差异由守卫表达，基础属性 parity 面两侧一致）。
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

    /**
     * 生产投递形态（AC8 最危险的静默失败形态）：JS 函数 → 沙盒 {@code Consumer} → 裸视图。
     * 与 {@code ItemModificationEventJS#modify(String, Consumer)} 的调用形状一致。
     */
    @SuppressWarnings("unchecked")
    private static ItemModificationJS evalProductionDelivery(String functionSource) {
        ItemModificationJS view = new ItemModificationJS();
        Context context = Context.newBuilder("js")
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(List.of()).get())
                .build();
        try {
            Value function = context.eval("js", functionSource);
            assertTrue(function.canExecute(), "fixture script must evaluate to a function");
            Consumer<ItemModificationJS> callback = function.as(Consumer.class);
            assertNotNull(callback,
                    "sandbox HostAccess must implement the functional interface for a JS function "
                            + "(production delivery form: raw view via Consumer)");
            callback.accept(view);
        } finally {
            context.close();
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

    /**
     * F1 真跑证据：生产投递形态（JS 函数 → 沙盒 Consumer → <b>裸视图</b>）与
     * surface 包装形态产生同一规范化声明与同一 fingerprint。
     *
     * <p>这是最危险的静默失败形态：若 GraalJS 不 honor 裸视图的 ProxyObject（或视图没实现
     * 它），property 写会在生产路径上被静默丢弃，而 surface 包装形态的测试仍会全绿。
     */
    @Test
    void productionDeliveryThroughSandboxConsumerReachesTheSameSetterAndFingerprint() {
        ModificationDeclaration viaProductionProperty = declarationOf(evalProductionDelivery(
                "(v) => { v.maxStackSize = 16; v.rarity = 'EPIC'; v.maxDamage = 0; }"));
        ModificationDeclaration viaProductionSetter = declarationOf(evalProductionDelivery(
                "(v) => { v.setMaxStackSize(16); v.setRarity('epic'); v.setMaxDamage(0); }"));
        ModificationDeclaration viaSurface = declarationOf(
                evalOn("item.maxStackSize = 16; item.rarity = 'EPIC'; item.maxDamage = 0;"));

        assertEquals(viaSurface.properties(), viaProductionProperty.properties(),
                "裸视图必须与 surface 包装形态产生同一规范化声明（生产投递形态的真跑证据）");
        assertEquals(viaSurface.properties(), viaProductionSetter.properties(),
                "生产投递形态下显式 setter 与 property 写也必须等价");
        assertEquals(fingerprintOf(viaSurface), fingerprintOf(viaProductionProperty),
                "裸视图的 property 写必须进入同一 modification plan fingerprint");
        assertEquals(fingerprintOf(viaSurface), fingerprintOf(viaProductionSetter));
    }

    @Test
    void productionDeliveryInvalidWriteFailsWithTheSameMemberNamedError() {
        RuntimeException viaProduction = assertThrows(RuntimeException.class,
                () -> evalProductionDelivery("(v) => { v.rarity = 'legendary'; }"));
        RuntimeException viaSurface = assertThrows(RuntimeException.class,
                () -> evalOn("item.rarity = 'legendary';"));

        assertTrue(viaProduction.getMessage().contains("legendary"),
                "生产投递形态的错误必须带成员上下文: " + viaProduction.getMessage());
        assertEquals(viaSurface.getMessage(), viaProduction.getMessage(),
                "两种到达形态的校验错误必须同源（同一 setter 抛出）");
    }

    @Test
    void writeOnlyMemberReadIsRejectedInsteadOfReturningTheSetterObject() {
        // 视图成员当前都有配套 getter；F7 钉住表面契约本身（用一个 setter-only 替身类，
        // ModificationViewSurface 对任意视图类反射派生成员目录）。
        WriteOnlyView view = new WriteOnlyView();
        ModificationViewSurface surface = ModificationViewSurface.of(view);
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            surface.putMember("value", context.eval("js", "7"));
        }
        assertEquals(7, view.value.intValue(), "setter-only 成员的写面仍走同一 setter");

        IllegalArgumentException readError = assertThrows(IllegalArgumentException.class,
                () -> surface.getMember("value"));
        assertTrue(readError.getMessage().contains("write-only"),
                "setter-only 成员读面必须显式报 write-only，而不是返回 setter 函数对象: "
                        + readError.getMessage());
        assertTrue(readError.getMessage().contains("readable:"), readError.getMessage());
    }

    /** setter-only 视图（F7 替身）：只有 setter，没有配套 getter。 */
    public static final class WriteOnlyView {
        Integer value;

        public void setValue(int value) {
            this.value = value;
        }
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
