package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.api.catalog.RegistryBuilderSurfaceEntry;
import com.tkisor.nekojs.probe.backend.python.RegistryBuilderPyRenderer;
import com.tkisor.nekojs.probe.backend.typescript.RegistryBuilderTsRenderer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * typed Builder 契约面 golden（ticket 15 AC7/AC9）：真实 builder 类经
 * {@link RegistryBuilderContract} 反射 → {@link RegistryBuilderSurfaces#derive}
 * （生产 {@code registerTypeDocs} 的同一派生函数）→ TS declaration
 * （{@link RegistryBuilderTsRenderer}，probe 后端 {@code @registry-builders/index.d.ts}
 * 的同一渲染器）。runtime member（{@link BuilderSurface}）、definition fingerprint、
 * TS/Python declaration 与本 golden 由<b>同一契约反射输入</b>派生，不手写第二份成员表。
 *
 * <p>golden 只冻结 <b>loader 无关面</b>（12 个内置 builder 类型）：流体体系是 NeoForge 面
 * （{@code FluidBuilder} 整文件 loader 守卫），其条目不进 golden，NeoForge 侧由守卫内的
 * 内存断言覆盖。26.x 与 1.21.1 的成员面有真实差异（1.21.1 的六个版本化 builder 未实现
 * TaggableBuilder、painting 无 author/title、potion effect 重载形状不同），按版本各冻一份
 * golden（守卫选择）。普通测试只读 golden；更新路径＝改输入（builder 类）→ 重跑本测试取得
 * 实际输出 → 手工比对并更新 golden → 在 baseline REPORT「golden 差异」小节留旧新 diff、
 * 原因、影响与审阅记录（root 树 golden 无 regenerate 开关，query 域 ticket 25 同款流程，
 * 见 managed-surface REGENERATE.md §1 表尾注）。
 */
class RegistryBuilderSurfaceGoldenTest {

    /** loader 无关的注册表键（golden 冻结面）；fluid 走 NeoForge 守卫内断言。 */
    private static final String FLUID_KEY = "minecraft:fluid";
    private static final String GOLDEN;
    static {
//? if >=26 {
        GOLDEN = "/golden/registry/startup-builders.d.ts";
//?} else {
/*        // 1.21.1 的 builder 成员面与 26.x 有真实差异（TaggableBuilder 未实现、painting 无
        // author/title、potion effect 重载形状），golden 按版本各冻一份（REPORT 五节点差异表）
        GOLDEN = "/golden/registry/startup-builders-1.21.1.d.ts";
*///?}
    }

    /** 与生产 NekoRegistryPointsPlugin.registerRegistryTypes 同一注册清单/顺序（builder 类是契约输入）。 */
    private static RegistryTypesPoint.RegistryTypes productionShapeTypes() {
        RegistryTypesPoint.RegistryTypesCollector collector = new RegistryTypesPoint.RegistryTypesCollector();
        collector.registerType(net.minecraft.core.registries.Registries.SOUND_EVENT, "basic", SoundEventBuilder.class, SoundEventBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.SOUND_EVENT, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.MOB_EFFECT, "basic", MobEffectBuilder.class, MobEffectBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.MOB_EFFECT, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.POTION, "basic", PotionBuilder.class, PotionBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.POTION, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.PAINTING_VARIANT, "basic", PaintingVariantBuilder.class, PaintingVariantBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.PAINTING_VARIANT, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.VILLAGER_TYPE, "basic", VillagerTypeBuilder.class, VillagerTypeBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.VILLAGER_TYPE, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.ITEM, "basic", ItemBuilder.class, ItemBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.ITEM, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.BLOCK, "basic", BlockBuilder.class, BlockBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.BLOCK, "basic");
//? if neoforge {
        collector.registerType(net.minecraft.core.registries.Registries.FLUID, "basic", FluidBuilder.class, FluidBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.FLUID, "basic");
//?}
        collector.registerType(net.minecraft.core.registries.Registries.ENTITY_TYPE, "basic", EntityTypeBuilder.class, EntityTypeBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.ENTITY_TYPE, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.ENCHANTMENT, "basic", EnchantmentBuilder.class, EnchantmentBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.ENCHANTMENT, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.PARTICLE_TYPE, "basic", ParticleTypeBuilder.class, ParticleTypeBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.PARTICLE_TYPE, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, "basic", CreativeTabBuilder.class, CreativeTabBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, "basic");
        return new RegistryTypesPoint.RegistryTypes(collector.byRegistry, collector.defaults, collector.builderClasses);
    }

    /** loader 无关条目（golden 冻结面）。 */
    private static List<RegistryBuilderSurfaceEntry> loaderIndependentEntries() {
        List<RegistryBuilderSurfaceEntry> entries = new ArrayList<>();
        for (RegistryBuilderSurfaceEntry entry : RegistryBuilderSurfaces.derive(productionShapeTypes())) {
            if (!FLUID_KEY.equals(entry.registryKey())) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    @Test
    void tsDeclarationIsByteStableAndMatchesGolden() throws IOException {
        String first = RegistryBuilderTsRenderer.render(loaderIndependentEntries());
        String second = RegistryBuilderTsRenderer.render(loaderIndependentEntries());
        assertEquals(first, second, "同一契约输入的重复渲染必须逐字节稳定");

        // 字节级为主 gate（审查 F4：只做 normalize 比较会放过空白/注释漂移）；
        // 行尾统一 LF（checkout autocrlf 容忍），失败时降级为内容级 diff 便于定位
        String golden = readGolden().replace("\r\n", "\n");
        if (!golden.equals(first)) {
            org.junit.jupiter.api.Assertions.fail(byteLevelMismatch(golden, first));
        }
    }

    /** 字节级不一致时的诊断信息：首个差异行 + 内容级（去空白/注释）是否一致的结论。 */
    private static String byteLevelMismatch(String golden, String actual) {
        String[] goldenLines = golden.split("\n", -1);
        String[] actualLines = actual.split("\n", -1);
        int line = 0;
        while (line < goldenLines.length && line < actualLines.length && goldenLines[line].equals(actualLines[line])) {
            line++;
        }
        String at = "line " + (line + 1) + "\n  golden: " + (line < goldenLines.length ? goldenLines[line] : "<end>")
                + "\n  actual: " + (line < actualLines.length ? actualLines[line] : "<end>");
        boolean contentOnly = normalize(golden).equals(normalize(actual));
        return "typed Builder 契约面与 golden 字节级不一致（" + at + "）。"
                + (contentOnly ? "差异仅为空白/行尾——byte-level gate 拒绝，请修正渲染或以再生成路径更新。"
                        : "内容级差异——确认是刻意变更后，更新 " + GOLDEN + " 并在 baseline REPORT golden 差异小节留记录。");
    }

    @Test
    void pythonDeclarationRendersTheSameMemberSemanticsFromTheSameEntries() {
        List<RegistryBuilderSurfaceEntry> entries = loaderIndependentEntries();
        String ts = RegistryBuilderTsRenderer.render(entries);
        String py = RegistryBuilderPyRenderer.render(entries);
        assertTrue(!py.isEmpty(), "Python 声明与 TS 同一输入派生，非空");
        assertEquals(headerNames(ts, "interface "), headerNames(py, "class "),
                "两侧 builder 列表同源同序");
        assertEquals(memberNames(ts), memberNames(py),
                "两侧成员名序列逐条相同（同一契约条目驱动，AC4）");
        // Python 不入 golden（ticket 09 惯例：Python 确定性/parity 在内存断言）——但稳定性仍钉住
        assertEquals(py, RegistryBuilderPyRenderer.render(entries), "Python 渲染逐字节稳定");
    }

    @Test
    void derivedSurfaceCoversTheDocumentedBuilderSet() {
        List<String> names = loaderIndependentEntries().stream()
                .map(RegistryBuilderSurfaceEntry::builderName).toList();
        assertEquals(List.of(
                "BlockBuilder", "CreativeTabBuilder", "EnchantmentBuilder", "EntityTypeBuilder",
                "ItemBuilder", "MobEffectBuilder", "PaintingVariantBuilder", "ParticleTypeBuilder",
                "PotionBuilder", "SoundEventBuilder", "VillagerTypeBuilder"), names,
                "派生顺序＝注册表键字典序（minecraft:block 最前）；面＝内置 12 loader 无关类型（fluid 另计）");
    }

//? if neoforge {
    @Test
    void fluidSurfaceIsDerivedOnNeoforgeOnlyAndNotFrozenInGolden() {
        List<RegistryBuilderSurfaceEntry> fluid = RegistryBuilderSurfaces.derive(productionShapeTypes())
                .stream().filter(entry -> FLUID_KEY.equals(entry.registryKey())).toList();
        assertEquals(1, fluid.size(), "流体体系是 NeoForge 面：neoforge 节点派生 FluidBuilder 条目");
        assertEquals("FluidBuilder", fluid.get(0).builderName());
        assertTrue(fluid.get(0).members().stream().anyMatch(member -> "noBucket".equals(member.name())),
                "已裁定的连带抑制面（noBucket）进入契约成员");
    }
//?}

    private static String normalize(String value) {
        return value.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("//") && !line.startsWith("#"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String readGolden() throws IOException {
        try (InputStream stream = RegistryBuilderSurfaceGoldenTest.class.getResourceAsStream(GOLDEN)) {
            if (stream == null) {
                throw new IllegalStateException("缺少 golden 资源 " + GOLDEN);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 声明头（TS {@code interface X} / Py {@code class X}）的 builder 名序列。 */
    private static List<String> headerNames(String rendered, String header) {
        List<String> names = new ArrayList<>();
        for (String line : rendered.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith(header)) {
                names.add(trimmed.substring(header.length()).split("[({ ]")[0]);
            }
        }
        return names;
    }

    /** 4 空格缩进成员行归一为成员名（readonly/def 前缀剥掉；取 ':' 或 '(' 前的首 token）。 */
    private static List<String> memberNames(String rendered) {
        List<String> names = new ArrayList<>();
        for (String line : rendered.split("\n", -1)) {
            if (!line.startsWith("    ") || line.startsWith("     ")) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("/**") || trimmed.startsWith("*")
                    || trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("from ")) {
                continue;
            }
            if (trimmed.startsWith("def ")) {
                names.add(trimmed.substring(4).split("[(]")[0]);
                continue;
            }
            if (trimmed.startsWith("readonly ")) {
                trimmed = trimmed.substring("readonly ".length());
            }
            names.add(trimmed.split("[:\\s(]")[0]);
        }
        return names;
    }
}
