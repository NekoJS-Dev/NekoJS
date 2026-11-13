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
 * GraalJS runtime fixture: an explicit setter and a JavaBean-style property
 * assignment call <b>the same setter</b> and go through the same validation, normalization,
 * declaration and definition fingerprint path.
 *
 * <p>The evidence (characterization of the old path): GraalJS silently dropped a property
 * write on a host view ({@code assigned,read=undefined,pending=null}, never reaching the
 * setter), so the javadoc example never worked (see the probe in
 * {@link ModificationLegacyCharacterizationTest}; a plain host object still behaves that way
 * today). {@link ModificationViewSurface} forwards both forms to the same {@code Method}
 * through a ProxyObject putMember hook.
 *
 * <p>This test pins that behavior in a real GraalJS Context and covers <b>both delivery
 * forms</b>:
 * <ul>
 *   <li>The {@code surface} wrapped form ({@code ModificationViewSurface.of(view)} is handed
 *       to the script);</li>
 *   <li>The <b>production delivery form</b>: a script function is implemented as a
 *       {@code Consumer} through the sandbox {@code HostAccess}
 *       ({@code NekoSharedHostAccess}, {@code allowAllImplementations}) and the host calls
 *       {@code callback.accept(view)} with a <b>bare view</b>, exactly like
 *       {@code ItemModificationEventJS#modify}'s {@code modifier.accept(view)}. The bare view
 *       works because it implements ProxyObject itself, which GraalJS honors.</li>
 * </ul>
 *
 * <p>No vanilla registry dependency: the view and the declaration fingerprint never touch a
 * registry. Nodes actually run: 26.1.2 / 26.1.2-fabric / 1.21.1 / 26.2.0 / 26.2.0-fabric.
 * The 26.x view has the food and tool members; the 1.21.1 view has only the four basic
 * properties. The guards express that member difference, and the basic-property parity face
 * is the same on both sides.
 */
class ModificationSetterPropertyParityTest {

    /** Runs a script against the surface-wrapped view in a real GraalJS Context; the script writes first. */
    private static ItemModificationJS evalOn(String script) {
        ItemModificationJS view = new ItemModificationJS();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("item", ModificationViewSurface.of(view));
            context.eval("js", script);
        }
        return view;
    }

    /**
     * The production delivery form, where a silent failure is most dangerous: a JS function
     * becomes a sandbox {@code Consumer} that receives a bare view. The call shape matches
     * {@code ItemModificationEventJS#modify(String, Consumer)}.
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

    /**
     * View to declaration with a fixed target. It does not touch a registry; registry
     * resolution belongs to the vanilla-gated end-to-end tests.
     */
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
     * Production delivery (a JS function becomes a sandbox Consumer that receives a <b>bare
     * view</b>) and the surface-wrapped form produce the same normalized declaration and the
     * same fingerprint.
     *
     * <p>This is the most dangerous silent-failure shape: if GraalJS did not honor the bare
     * view's ProxyObject, or the view did not implement it, the property write would be
     * silently dropped on the production path while tests that use the wrapped surface would
     * still pass.
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
        // The view members all have a matching getter right now; this test pins the surface behavior
        // itself using a setter-only stand-in class, since ModificationViewSurface derives the
        // member catalog of any view class by reflection.
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

    /** A setter-only view: only a setter, no matching getter. */
    public static final class WriteOnlyView {
        Integer value;

        public void setValue(int value) {
            this.value = value;
        }
    }

    @Test
    void numericShapesNormalizeToTheSameFingerprint() {
        // Narrowing int versus double on the setter side must not produce different plans:
        // 16 and 16.0 give the same fingerprint
        ModificationDeclaration intShape = declarationOf(evalOn("item.maxStackSize = 16;"));
        ModificationDeclaration doubleShape = declarationOf(evalOn("item.maxStackSize = 16.0;"));
        assertEquals(fingerprintOf(intShape), fingerprintOf(doubleShape));

        // Declarations with different values must have distinguishable fingerprints (the fingerprint
        // is not a constant string)
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

    // ---- Members only on 26.x (food/tool): an object literal written as a property reaches the
    // same setter ----
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

        // A null property write is a removal request (same semantics as setFood(null))
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
