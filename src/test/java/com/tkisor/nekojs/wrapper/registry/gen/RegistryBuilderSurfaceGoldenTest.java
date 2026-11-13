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
 * Golden for the typed Builder contract surface. Real builder classes go through
 * {@link RegistryBuilderContract} reflection into {@link RegistryBuilderSurfaces#derive}, the
 * same derive function production {@code registerTypeDocs} uses, and then into a TS declaration
 * through {@link RegistryBuilderTsRenderer}, the same renderer the probe backend uses for
 * {@code @registry-builders/index.d.ts}. Runtime members ({@link BuilderSurface}), the definition
 * fingerprint, the TS and Python declarations and this golden all derive from that one contract
 * reflection input, so no second member table is written by hand.
 *
 * <p>The golden freezes only the loader-independent surface, the 12 built-in builder types. The
 * fluid system is NeoForge-only ({@code FluidBuilder} sits entirely behind a loader guard), so
 * its entries stay out of the golden and the NeoForge side asserts them in memory inside the
 * guard. 26.x and 1.21.1 differ in real ways: on 1.21.1 the six versioned builders do not
 * implement TaggableBuilder, painting has no author/title, and the potion effect overloads have
 * a different shape. Each version therefore freezes its own golden file, selected by the guards.
 * Ordinary tests only read the golden. To update it, change the input (the builder classes),
 * rerun this test for the actual output, compare by hand and update the golden, then record the
 * old and new diff, the reason, the impact and the review in the "golden differences" section
 * of the baseline REPORT. The root-tree golden has no regenerate switch; follow the same process
 * used for the managed surface (see managed-surface REGENERATE.md §1).
 */
class RegistryBuilderSurfaceGoldenTest {

    /** A loader-independent registry key, part of the frozen golden surface. Fluids are asserted in the NeoForge guard. */
    private static final String FLUID_KEY = "minecraft:fluid";
    private static final String GOLDEN;
    static {
//? if >=26 {
        GOLDEN = "/golden/registry/startup-builders.d.ts";
//?} else {
        /*// 1.21.1 builder members differ from 26.x (TaggableBuilder not implemented, painting has no
        // author/title, potion effect overload shape differs), so each version freezes its own
        // golden (see the five-node difference table in the REPORT)
        GOLDEN = "/golden/registry/startup-builders-1.21.1.d.ts";
         *///?}
    }

    /** The same registry list and order as production NekoRegistryPointsPlugin.registerRegistryTypes. */
    private static RegistryTypesPoint.RegistryTypes productionShapeTypes() {
        RegistryTypesPoint.RegistryTypesCollector collector = new RegistryTypesPoint.RegistryTypesCollector();
        collector.registerType(net.minecraft.core.registries.Registries.SOUND_EVENT, "basic", SoundEventBuilder.class, SoundEventBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.SOUND_EVENT, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.MOB_EFFECT, "basic", MobEffectBuilder.class, MobEffectBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.MOB_EFFECT, "basic");
        collector.registerType(net.minecraft.core.registries.Registries.POTION, "basic", PotionBuilder.class, PotionBuilder::new);
        collector.setDefault(net.minecraft.core.registries.Registries.POTION, "basic");
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

    /** The loader-independent entries, which the golden freezes. */
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

        // The byte-level comparison is the main gate; comparing normalized text alone would let
        // whitespace and comment drift through. Line endings are normalized to LF so a checkout
        // with autocrlf still passes. On failure the diagnostic falls back to a content-level
        // diff, which makes the difference easier to locate.
        String golden = readGolden().replace("\r\n", "\n");
        if (!golden.equals(first)) {
            java.nio.file.Path report = java.nio.file.Path.of("build", "nekojs-gates",
                    java.nio.file.Path.of(GOLDEN).getFileName().toString());
            java.nio.file.Files.createDirectories(report.getParent());
            java.nio.file.Files.writeString(report, first, StandardCharsets.UTF_8);
            org.junit.jupiter.api.Assertions.fail(byteLevelMismatch(golden, first)
                    + " Generated declaration for review: " + report.toAbsolutePath());
        }
    }

    /** Byte-level mismatch diagnostic: the first differing line and whether stripped content still matches. */
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
        // Python is not part of the golden; its determinism and parity are asserted in memory. Its
        // byte-level stability is still pinned here.
        assertEquals(py, RegistryBuilderPyRenderer.render(entries), "Python 渲染逐字节稳定");
    }

    @Test
    void startupSoundDeclarationsPreserveNullableBoxedFloatAndRuntimeWrites() {
        List<RegistryBuilderSurfaceEntry> entries = loaderIndependentEntries();
        String typescript = RegistryBuilderTsRenderer.render(entries);
        String python = RegistryBuilderPyRenderer.render(entries);
        assertTrue(typescript.contains("fixedRange: number | null;"), typescript);
        assertTrue(typescript.contains("setFixedRange(fixedRange: number | null): void;"), typescript);
        assertTrue(python.contains("fixedRange: float | None"), python);
        assertTrue(python.contains("def setFixedRange(self, fixedRange: float | None) -> None: ..."), python);
        assertEquals("number", RegistryBuilderContract.tsTypeOf(float.class));
        assertEquals("float", RegistryBuilderContract.pyTypeOf(float.class));
        SoundEventBuilder builder = new SoundEventBuilder(net.minecraft.resources.Identifier.parse("test:nullable_sound"));
        try (graal.graalvm.polyglot.Context context = graal.graalvm.polyglot.Context.newBuilder("js")
                .allowHostAccess(new com.tkisor.nekojs.core.NekoSharedHostAccess(List.of()).get())
                .allowHostClassLookup(name -> false).build()) {
            context.getBindings("js").putMember("build", BuilderSurface.of(builder));
            context.eval("js", "build.fixedRange = 16; build.fixedRange = null;");
            org.junit.jupiter.api.Assertions.assertNull(builder.getFixedRange());
            context.eval("js", "build.setFixedRange(32); build.setFixedRange(null);");
            org.junit.jupiter.api.Assertions.assertNull(builder.getFixedRange());
        }
    }

    @Test
    void potionDeclarationsExposeBothEffectOverloads() {
        List<RegistryBuilderSurfaceEntry> entries = loaderIndependentEntries();
        String typescript = RegistryBuilderTsRenderer.render(entries);
        String python = RegistryBuilderPyRenderer.render(entries);
        assertTrue(typescript.contains("effect(effect: any, durationTicks: number, amplifier: number): void;"),
                "Potion declarations must include the three-argument TypeScript effect overload");
        assertTrue(typescript.contains("effect(effect: any, durationTicks: number, amplifier: number, ambient: boolean, visible: boolean): void;"),
                "Potion declarations must include the five-argument TypeScript effect overload");
        assertTrue(python.contains("    @overload\n    def effect(self, effect: Any, durationTicks: int, amplifier: int) -> None: ..."),
                "Potion declarations must decorate the three-argument Python effect overload");
        assertTrue(python.contains("    @overload\n    def effect(self, effect: Any, durationTicks: int, amplifier: int, ambient: bool, visible: bool) -> None: ..."),
                "Potion declarations must decorate the five-argument Python effect overload");
    }

    @Test
    void derivedSurfaceCoversTheDocumentedBuilderSet() {
        List<String> names = loaderIndependentEntries().stream()
                .map(RegistryBuilderSurfaceEntry::builderName).toList();
        assertEquals(List.of(
                "BlockBuilder", "CreativeTabBuilder", "EnchantmentBuilder", "EntityTypeBuilder",
                "ItemBuilder", "MobEffectBuilder", "ParticleTypeBuilder",
                "PotionBuilder", "SoundEventBuilder", "VillagerTypeBuilder"), names,
                "派生顺序＝注册表键字典序（minecraft:block 最前）；面＝内置 11 loader 无关类型（fluid 另计）");
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

    /** The builder names in the declaration headers (TS {@code interface X} / Py {@code class X}). */
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

    /** Normalizes a 4-space member line to its member name: drop readonly/def, keep the first token. */
    private static List<String> memberNames(String rendered) {
        List<String> names = new ArrayList<>();
        for (String line : rendered.split("\n", -1)) {
            if (!line.startsWith("    ") || line.startsWith("     ")) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("/**") || trimmed.startsWith("*")
                    || trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("from ")
                    || trimmed.startsWith("@")) {
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
