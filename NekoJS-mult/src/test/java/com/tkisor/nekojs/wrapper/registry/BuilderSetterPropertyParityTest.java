package com.tkisor.nekojs.wrapper.registry;

import com.tkisor.nekojs.wrapper.registry.gen.BlockBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.BuilderSurface;
import com.tkisor.nekojs.wrapper.registry.gen.ItemBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.RegistryObjectBuilder;
import com.tkisor.nekojs.wrapper.registry.gen.StartupRegistryRuntime;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        // 非 null 赋值在脚本面被拒（子 builder 不能从外部替换，只能抑制或配置）
        BlockBuilder rejected = new BlockBuilder(net.minecraft.resources.Identifier.parse("mymod:r"));
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> evalOn(rejected, "b.item = {};"));
        assertTrue(error.getMessage().contains("only accepts null"), error.getMessage());
    }
//?}

    @Test
    void builderImplementsSupplierSoDrainCanLazilyBuild() {
        ItemBuilder builder = itemBuilder("mymod:supplier");
        evalOn(builder, "b.maxStackSize = 8;");
        RegistryObjectBuilder<?> asBuilder = builder;
        assertEquals(builder, asBuilder, "surface 不改变 Java 侧 Supplier 身份（先攒后建的懒构建点不变）");
    }
}
