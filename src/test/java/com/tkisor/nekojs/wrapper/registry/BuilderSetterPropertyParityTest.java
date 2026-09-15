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
 * GraalJS runtime contract fixture（ticket 15 AC3/AC4）：固定「显式 setter 与
 * JavaBean-style property assignment 调用<b>同一个 setter</b>，并进入同一校验、
 * 规范化、definition fingerprint 与注册收集路径」。
 *
 * <p>事实依据（fixture 化前的 characterization 探测）：GraalJS 对<b>宿主对象</b>的
 * property 写入不会落到 setter——靠天然 Bean 行为不可靠；{@link BuilderSurface}
 * （ProxyObject putMember seam）把两种写法转发到同一 {@code Method}。本测试在
 * 真实 GraalJS Context 里钉住该契约，GraalJS 坐标升级时重跑此处即可重新验证
 * （spec 08 Implementation Decisions 的既定流程）。
 */
class BuilderSetterPropertyParityTest {

    private static final String NODE = "contract-test";

    private static ItemBuilder itemBuilder(String id) {
        return new ItemBuilder(net.minecraft.resources.Identifier.parse(id));
    }

    /** 在真实 GraalJS Context 里对 surface 执行一段脚本（脚本是第一书写者）。 */
    private static void evalOn(Object builder, String script) {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("b", BuilderSurface.of(builder));
            context.eval("js", script);
        }
    }

    @Test
    void propertyAssignmentAndExplicitSetterProduceSameNormalizedStateAndFingerprint() {
        // 同一 id：fingerprint 覆盖定义身份（registry+id+类型+规范化成员），两种写法只比写入路径
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

        // 未知读：ProxyObject hasMember=false 的常规 JS 语义（undefined，不抛）
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("b", BuilderSurface.of(builder));
            assertTrue(context.eval("js", "b.nope === undefined").asBoolean(),
                    "未知成员读取按 JS 语义返回 undefined");
        }
        // 未知写：putMember 一定被调用，错误带成员目录
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

        // 属性形态：嵌套子 builder 也是 surface，写入走同一 setter
        evalOn(blockViaProperty, "b.hardness = 3; b.item.maxStackSize = 16;");
        // 方法/显式 setter 形态：item(cb) 回调里拿到的也是 surface
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

        // 非 null 赋值在脚本面被拒（子 builder 不能从外部替换，只能抑制或配置）：
        // JS 对象字面量在 coerce 期就到不了 setter——"item expects ItemBuilder but the
        // value cannot be converted"（审查 F2：旧断言的 "only accepts null" 只在 Java 直调
        // setItem 时可达，脚本面不可达）
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
        // surface 不改变 Java 侧对象身份：包上再解包仍是同一实例（drain 期 get() 的懒构建点不变）
        assertSame(builder, BuilderSurface.unwrap(BuilderSurface.of(builder)));

        // Supplier 语义实质断言：get() 恰构建一次并缓存（先攒后建）
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
    // 对象值可写属性的指纹稳定性（审查 F2；合成 builder，无 vanilla 依赖，全节点真跑）
    // ------------------------------------------------------------------

    /** 合成子 builder：一个 int 属性（模拟 ItemBuilder 之于 BlockBuilder.item）。 */
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

    /** 合成父 builder：对象值可写属性 child（模拟 BlockBuilder 的 item 抑制/存在语义）。 */
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
        // 两棵独立实例树、同一配置：指纹必须一致（旧实现内嵌 identityHashCode 会漂移）
        SyntheticParent first = new SyntheticParent(net.minecraft.resources.Identifier.parse("mymod:host"));
        first.setChild(new SyntheticChild(net.minecraft.resources.Identifier.parse("mymod:host")));
        SyntheticParent second = new SyntheticParent(net.minecraft.resources.Identifier.parse("mymod:host"));
        second.setChild(new SyntheticChild(net.minecraft.resources.Identifier.parse("mymod:host")));
        // 经脚本面写入子属性（覆盖 surface 的嵌套路径，与 Java 直写同指纹）
        evalOn(first.getChild(), "b.charge = 7;");
        second.getChild().setCharge(7);

        StartupRegistryRuntime runtime = new StartupRegistryRuntime(NODE);
        assertEquals(runtime.definitionFingerprint(second), runtime.definitionFingerprint(first),
                "对象值属性按子指纹规范化：同声明的不同实例树指纹一致");

        // 抑制（null）与存在（子指纹）必须可区分
        SyntheticParent suppressed = new SyntheticParent(net.minecraft.resources.Identifier.parse("mymod:host"));
        assertNotEquals(runtime.definitionFingerprint(first), runtime.definitionFingerprint(suppressed),
                "child = null（抑制）与 child 存在是不同定义");

        // 子配置变化要反映进父指纹（连带声明的全规范化读数，spec 08）
        second.getChild().setCharge(9);
        assertNotEquals(runtime.definitionFingerprint(first), runtime.definitionFingerprint(second),
                "子 builder 配置变化必须改变父指纹");
    }

    // ------------------------------------------------------------------
    // 同名方法重载双形态可达（审查 F1；真实 PotionBuilder，3 参/5 参 effect）
    // ------------------------------------------------------------------

    @Test
    void potionEffectOverloadsAreBothCallableFromScript() {
        // effect 的 effect 参数传非 String/Holder 值（Integer）时 resolveEffect 返回 null、
        // 不触碰 vanilla 注册表——裸 JUnit 可验证「两种参数个数都解析到各自重载并成功执行」；
        // 修复前（单 Method 收集）其中一个形态会抛 "expects [...] but got N argument(s)"
        PotionBuilder builder = new PotionBuilder(net.minecraft.resources.Identifier.parse("mymod:elixir"));

        evalOn(builder, "b.effect(123, 100, 1);");
        evalOn(builder, "b.effect(123, 100, 1, false, true);");

        // 错误形态（错误参数个数）仍要给出带期望签名的可诊断错误
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(builder, "b.effect(123);"));
        assertTrue(error.getMessage().contains("effect"), error.getMessage());
        assertTrue(error.getMessage().contains("argument"), error.getMessage());
    }

    /** 契约层：重载列表真实收集（同名多签名进 Member.overloads，顺序确定）。 */
    @Test
    void contractCollectsRealOverloadLists() {
        RegistryBuilderContract contract = RegistryBuilderContract.of(PotionBuilder.class);
        RegistryBuilderContract.Member effect = contract.member("effect");
        assertNotNull(effect);
        assertEquals(RegistryBuilderContract.MemberKind.METHOD, effect.kind());
        assertEquals(2, effect.overloads().size(), "3 参/5 参两个 effect 都要进契约: " + effect.overloads());
        // 确定性排序：参数最多者在前
        assertEquals(5, effect.overloads().get(0).getParameterCount());
        assertEquals(3, effect.overloads().get(1).getParameterCount());
    }
}
