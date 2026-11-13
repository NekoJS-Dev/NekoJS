package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.wrapper.registry.gen.BlockBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.BuilderSurface;
import com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.PotionBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryBuilderContract;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryObjectBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the GraalJS runtime contract: an explicit setter call and a JavaBean-style property
 * assignment invoke the <b>same setter</b> and go through the same validation, normalization,
 * definition fingerprint and registry collection path.
 *
 * <p>GraalJS does not route property writes on a <b>host object</b> to a setter, so plain bean
 * behaviour is not enough. {@link BuilderSurface} (the ProxyObject putMember hook) forwards both
 * forms to the same {@code Method}. This test pins that contract in a real GraalJS Context;
 * rerun it after a GraalJS upgrade to verify the contract again.
 */
class BuilderSetterPropertyParityTest {

    private static final String NODE = "contract-test";

    private static ItemBuilder itemBuilder(String id) {
        return new ItemBuilder(net.minecraft.resources.Identifier.parse(id));
    }

    /** Runs a script against the surface in a real GraalJS Context; the script is the only writer. */
    private static void evalOn(Object builder, String script) {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("b", BuilderSurface.of(builder));
            context.eval("js", script);
        }
    }

    @Test
    void propertyAssignmentAndExplicitSetterProduceSameNormalizedStateAndFingerprint() {
        // Both builders use the same id. The fingerprint covers the definition identity (registry + id +
        // type + normalized members), so the two forms differ only in how they write.
        ItemBuilder viaProperty = itemBuilder("mymod:parity");
        ItemBuilder viaSetter = itemBuilder("mymod:parity");

        evalOn(viaProperty, "b.maxStackSize = 16; b.rarity = 'EPIC'; b.fireResistant = true; b.groupTab = '';");
        evalOn(viaSetter, "b.setMaxStackSize(16); b.setRarity('epic'); b.setFireResistant(true); b.setGroupTab(null);");

        assertEquals(viaSetter.getMaxStackSize(), viaProperty.getMaxStackSize());
        assertEquals(viaSetter.getRarity(), viaProperty.getRarity(), "property 写法必须与显式 setter 同一规范化（小写归一）");
        assertTrue(viaProperty.isFireResistant());
        assertEquals(viaSetter.getGroupTab(), viaProperty.getGroupTab(), "空白 groupTab 与 null 同一规范化（不分配）");

        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        assertEquals(runtime.definitionFingerprint(viaSetter), runtime.definitionFingerprint(viaProperty),
                "两种写法必须产生同一 definition fingerprint");
    }

    @Test
    void invalidValuesFailWithTheSameMemberNamedErrorFromBothWriteStyles() {
        ItemBuilder propertyStyle = itemBuilder("mymod:bad_property");
        ItemBuilder setterStyle = itemBuilder("mymod:bad_setter");

        RuntimeException propertyError = assertThrows(RuntimeException.class,
                () -> evalOn(propertyStyle, "b.maxStackSize = 200;"));
        RuntimeException setterError = assertThrows(RuntimeException.class,
                () -> evalOn(setterStyle, "b.setMaxStackSize(200);"));

        assertEquals(setterError.getMessage(), propertyError.getMessage(),
                "两种写法的校验错误必须同源（同一 setter 抛出）");
        assertTrue(propertyError.getMessage().contains("maxStackSize must be in [1, 99]"),
                "错误信息带成员名与约束: " + propertyError.getMessage());
        assertEquals(64, propertyStyle.getMaxStackSize(), "写入失败不得改变现状");
    }

    @Test
    void typeMismatchedPropertyWriteIsRejectedWithMemberContext() {
        ItemBuilder builder = itemBuilder("mymod:bad_type");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.maxStackSize = 'lots';"));

        assertTrue(error.getMessage().contains("maxStackSize"), "错误信息带成员名: " + error.getMessage());
    }

    @Test
    void finalIdentityFieldIsReadOnly() {
        ItemBuilder builder = itemBuilder("mymod:identity");

        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("b", BuilderSurface.of(builder));
            Value id = context.getBindings("js").getMember("b").getMember("id");
            assertNotNull(id, "final id 是只读成员（AC3 的例外清单），必须在成员目录里可读");
            assertEquals("mymod:identity", id.toString());
        }
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.id = 'mymod:other';"));
        assertTrue(error.getMessage().contains("read-only"), "final id 不可写: " + error.getMessage());
    }

    @Test
    void unknownMemberReadYieldsUndefinedButUnknownWriteIsRejectedWithTheDirectory() {
        ItemBuilder builder = itemBuilder("mymod:unknown");

        // Unknown read: plain JS semantics for hasMember=false on the ProxyObject (undefined, no throw).
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("b", BuilderSurface.of(builder));
            assertTrue(context.eval("js", "b.nope === undefined").asBoolean(),
                    "未知成员读取按 JS 语义返回 undefined");
        }
        // Unknown write: putMember is always called, and the error lists the available members.
        RuntimeException writeError = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.nope = 1;"));
        assertTrue(writeError.getMessage().contains("has no member 'nope'"), writeError.getMessage());
        assertTrue(writeError.getMessage().contains("known:"), writeError.getMessage());
    }

//? if >=26 {
    @Test
    void nestedSubBuilderConfigGoesThroughTheSameSurface() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "block builder class init needs vanilla registries (no FML loader in bare JUnit)");
        BlockBuilder blockViaProperty = new BlockBuilder(net.minecraft.resources.Identifier.parse("mymod:prop_block"));
        BlockBuilder blockViaMethod = new BlockBuilder(net.minecraft.resources.Identifier.parse("mymod:method_block"));

        // Property form: nested child builders are surfaces too, so writes go through the same setter.
        evalOn(blockViaProperty, "b.hardness = 3; b.item.maxStackSize = 16;");
        // Method and explicit-setter form: the callback of item(cb) also receives a surface.
        evalOn(blockViaMethod, "b.setHardness(3); b.item(i => i.setMaxStackSize(16));");

        assertEquals(3.0f, blockViaProperty.getHardness());
        assertEquals(3.0f, blockViaMethod.getHardness());
        assertEquals(16, blockViaProperty.getItem().getMaxStackSize());
        assertEquals(16, blockViaMethod.getItem().getMaxStackSize());

        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        assertEquals(runtime.definitionFingerprint(blockViaMethod), runtime.definitionFingerprint(blockViaProperty));
    }

    @Test
    void nullItemPropertyAndNoItemMethodAreTheSameWrite() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tkisor.nekojs.testfixture.VanillaRegistryProbe.available(),
                "block builder class init needs vanilla registries (no FML loader in bare JUnit)");
        BlockBuilder viaProperty = new BlockBuilder(net.minecraft.resources.Identifier.parse("mymod:p"));
        BlockBuilder viaMethod = new BlockBuilder(net.minecraft.resources.Identifier.parse("mymod:m"));

        evalOn(viaProperty, "b.item = null;");
        evalOn(viaMethod, "b.noItem();");

        assertEquals(viaMethod.getItem(), viaProperty.getItem(), "b.item = null 与 noItem() 必须同一写入点（都置 null）");

        // A non-null assignment is rejected on the script side: a child builder cannot be replaced
        // from outside, only suppressed or configured. A JS object literal never reaches the
        // setter, because coercion fails first with "item expects ItemBuilder but the value
        // cannot be converted". Only a direct Java call to setItem can reach the "only accepts
        // null" check; scripts cannot.
        BlockBuilder rejected = new BlockBuilder(net.minecraft.resources.Identifier.parse("mymod:r"));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(rejected, "b.item = {};"));
        assertTrue(error.getMessage().contains("item"), "错误信息带成员名: " + error.getMessage());
        assertTrue(error.getMessage().contains("cannot be converted"), error.getMessage());
    }
//?}

    @Test
    void builderImplementsSupplierSoDrainCanLazilyBuild() {
        ItemBuilder builder = itemBuilder("mymod:supplier");
        evalOn(builder, "b.maxStackSize = 8;");
        // The surface does not change object identity on the Java side: wrapping and unwrapping
        // returns the same instance, so the lazy build point in drain-time get() stays the same.
        assertSame(builder, BuilderSurface.unwrap(BuilderSurface.of(builder)));

        // The real Supplier assertion: get() builds exactly once and caches the result, so the builder
        // is configured first and built later.
        int[] builds = {0};
        RegistryObjectBuilder<String> counting = new RegistryObjectBuilder<>(
                net.minecraft.resources.Identifier.parse("mymod:counting")) {
            @Override
            public String build() {
                builds[0]++;
                return "built-once";
            }
        };
        java.util.function.Supplier<String> supplier = counting;
        assertEquals("built-once", supplier.get());
        assertEquals("built-once", supplier.get(), "第二次取值走缓存");
        assertEquals(1, builds[0], "build() 恰好执行一次");
    }

    // ------------------------------------------------------------------
    // Fingerprint stability for writable object-valued properties (synthetic builders, no
    // vanilla dependency, runs on every node)
    // ------------------------------------------------------------------

    /** Synthetic child builder with one int property, standing in for ItemBuilder in BlockBuilder.item. */
    public static final class SyntheticChild extends RegistryObjectBuilder<Object> {
        private int charge = 0;

        public SyntheticChild(net.minecraft.resources.Identifier id) {
            super(id);
        }

        public int getCharge() {
            return charge;
        }

        public void setCharge(int charge) {
            this.charge = charge;
        }

        @Override
        public Object build() {
            return "child:" + id + ":" + charge;
        }
    }

    /** Synthetic parent builder with the writable child property, standing in for BlockBuilder's item. */
    public static final class SyntheticParent extends RegistryObjectBuilder<Object> {
        private SyntheticChild child;

        public SyntheticParent(net.minecraft.resources.Identifier id) {
            super(id);
        }

        public SyntheticChild getChild() {
            return child;
        }

        public void setChild(SyntheticChild child) {
            this.child = child;
        }

        @Override
        public Object build() {
            return "parent:" + id;
        }
    }

    @Test
    void objectValuedPropertyFingerprintIsStableAcrossInstancesAndDistinguishesSuppression() {
        // Two independent instance trees with the same configuration must produce the same
        // fingerprint; an identityHashCode inside the fingerprint would drift between them.
        SyntheticParent first = new SyntheticParent(net.minecraft.resources.Identifier.parse("mymod:host"));
        first.setChild(new SyntheticChild(net.minecraft.resources.Identifier.parse("mymod:host")));
        SyntheticParent second = new SyntheticParent(net.minecraft.resources.Identifier.parse("mymod:host"));
        second.setChild(new SyntheticChild(net.minecraft.resources.Identifier.parse("mymod:host")));
        // Write the child property through the script surface, which covers the nested path of the
        // surface and must produce the same fingerprint as a direct Java write.
        evalOn(first.getChild(), "b.charge = 7;");
        second.getChild().setCharge(7);

        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        assertEquals(runtime.definitionFingerprint(second), runtime.definitionFingerprint(first),
                "对象值属性按子指纹规范化：同声明的不同实例树指纹一致");

        // Suppression (null) and presence (the child fingerprint) must stay distinguishable.
        SyntheticParent suppressed = new SyntheticParent(net.minecraft.resources.Identifier.parse("mymod:host"));
        assertNotEquals(runtime.definitionFingerprint(first), runtime.definitionFingerprint(suppressed),
                "child = null（抑制）与 child 存在是不同定义");

        // A child configuration change must show up in the parent fingerprint, which normalizes the
        // whole declaration including the child.
        second.getChild().setCharge(9);
        assertNotEquals(runtime.definitionFingerprint(first), runtime.definitionFingerprint(second),
                "子 builder 配置变化必须改变父指纹");
    }

    // ------------------------------------------------------------------
    // Both overloads of a same-named method are reachable (real PotionBuilder, 3-arg and
    // 5-arg effect)
    // ------------------------------------------------------------------

    @Test
    void potionEffectOverloadsAreBothCallableFromScript() {
        // Passing an Integer (not a String or Holder) as the effect value makes resolveEffect return
        // null and touches no vanilla registry, so a bare JUnit run can check that both argument
        // counts resolve to their own overload and execute. With the earlier single-Method
        // collection, one of the forms threw "expects [...] but got N argument(s)".
        PotionBuilder builder = new PotionBuilder(net.minecraft.resources.Identifier.parse("mymod:elixir"));

        evalOn(builder, "b.effect(123, 100, 1);");
        evalOn(builder, "b.effect(123, 100, 1, false, true);");

        // A wrong argument count must still produce a diagnosable error that includes the expected
        // signature.
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.effect(123);"));
        assertTrue(error.getMessage().contains("effect"), error.getMessage());
        assertTrue(error.getMessage().contains("argument"), error.getMessage());
    }

    /** Contract layer: collects the real overload lists; overloads land in Member.overloads in a fixed order. */
    @Test
    void contractCollectsRealOverloadLists() {
        RegistryBuilderContract contract = RegistryBuilderContract.of(PotionBuilder.class);
        RegistryBuilderContract.Member effect = contract.member("effect");
        assertNotNull(effect);
        assertEquals(RegistryBuilderContract.MemberKind.METHOD, effect.kind());
        assertEquals(2, effect.overloads().size(), "3 参/5 参两个 effect 都要进契约: " + effect.overloads());
        // Deterministic order: the overload with the most parameters comes first.
        assertEquals(5, effect.overloads().get(0).getParameterCount());
        assertEquals(3, effect.overloads().get(1).getParameterCount());
    }
}
