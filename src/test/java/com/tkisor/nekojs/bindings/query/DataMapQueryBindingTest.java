//? if neoforge {
package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.bindings.static_access.DataMapJS;
import com.tkisor.nekojs.probe.backend.typescript.BindingDeclarationGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 25 DataMap query binding fixture（NeoForge）：
 *
 * <ul>
 *   <li><b>只读快照面</b>：binding 只暴露 furnaceFuel/compostable 两个查询方法，
 *       返回不可变值类型（Integer/Float），不暴露可变 registry view；portable 替代面
 *       {@code RegistryView.dataMapValue} 返回 JSON 字符串。</li>
 *   <li><b>declaration parity</b>：经生产 probe 的 {@link BindingDeclarationGenerator}
 *       （catalog entry 派生自真实 binding 注册形态）生成的 TS declaration 与 runtime
 *       member 一致（golden 只读；重复生成逐字节稳定）。</li>
 *   <li><b>非法输入</b>：null 栈得到带域+调用入口的普通错误，不含修复提示。</li>
 *   <li><b>命中/缺失/类型转换</b>：依赖真实注册表与数据包加载（裸 JVM 不可行，
 *       见 {@code VanillaRegistryProbe}），由 runServer 的 {@code /nekojs test} fixture
 *       承载（REPORT §5）。</li>
 * </ul>
 */
class DataMapQueryBindingTest {

    private static final String GOLDEN = "/golden/query/datamap-binding.d.txt";

    @Test
    void declarationGenerationIsByteStableAndMatchesGolden() throws IOException {
        // 与生产 catalog 相同的 entry 形态：class binding（value instanceof Class -> staticClass=true）
        BindingCatalogEntry entry =
                BindingCatalogEntry.of("DataMap", ScriptType.SERVER, DataMapJS.class, true);
        BindingDeclarationGenerator generator = new BindingDeclarationGenerator();

        String first = generator.generate(List.of(entry), ScriptType.SERVER);
        String second = generator.generate(List.of(entry), ScriptType.SERVER);
        assertEquals(first, second, "repeated declaration generation must be byte-stable");

        assertEquals(normalize(readGolden()), normalize(first),
                "DataMap binding declaration changed. 确认是刻意变更后，更新 " + GOLDEN
                        + " 并在 REPORT golden 差异小节留记录");
    }

    /** 只读面：两个查询方法、不可变值返回类型；替代面 dataMapValue 返回 portable String。 */
    @Test
    void querySurfaceIsReadOnlyAndPortable() {
        List<String> publicMethods = List.of(DataMapJS.class.getDeclaredMethods()).stream()
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .sorted()
                .collect(Collectors.toList());
        assertEquals(List.of("compostable", "furnaceFuel"), publicMethods,
                "query binding must expose read-only query methods only (no mutation, no registry view)");

        assertEquals(Integer.class, returnTypeOf("furnaceFuel"),
                "furnaceFuel returns an immutable snapshot value (burn ticks)");
        assertEquals(Float.class, returnTypeOf("compostable"),
                "compostable returns an immutable snapshot value (chance)");

        try {
            // RegistryView（common，无 MC 依赖）的替代查询面返回 JSON 字符串而非活对象
            Class<?> registryView = Class.forName("com.tkisor.nekojs.api.facade.RegistryView");
            assertEquals(String.class,
                    registryView.getMethod("dataMapValue", String.class, String.class).getReturnType(),
                    "portable data map query must return a JSON string, not a live registry object");
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            throw new AssertionError("RegistryView dataMap query surface missing", error);
        }
    }

    /** 非法输入：null 栈 → 带 DataMap.furnaceFuel/compostable 域+入口的普通错误，无修复提示。 */
    @Test
    void nullStackErrorCarriesDomainAndEntryWithoutFixHints() {
        DataMapJS dataMap = new DataMapJS();

        IllegalArgumentException fuelError =
                assertThrows(IllegalArgumentException.class, () -> dataMap.furnaceFuel(null));
        assertTrue(fuelError.getMessage().startsWith("DataMap.furnaceFuel:"),
                "error must carry domain + entry: " + fuelError.getMessage());
        assertTrue(fuelError.getMessage().contains("null"));

        IllegalArgumentException compostError =
                assertThrows(IllegalArgumentException.class, () -> dataMap.compostable(null));
        assertTrue(compostError.getMessage().startsWith("DataMap.compostable:"),
                "error must carry domain + entry: " + compostError.getMessage());
    }

    // ---- helpers ----

    private static Class<?> returnTypeOf(String method) {
        try {
            return DataMapJS.class.getMethod(method, net.minecraft.world.item.ItemStack.class)
                    .getReturnType();
        } catch (NoSuchMethodException error) {
            throw new AssertionError("missing query method " + method, error);
        }
    }

    private static String normalize(String value) {
        return value.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                .collect(Collectors.joining("\n"));
    }

    private static String readGolden() throws IOException {
        try (InputStream stream = DataMapQueryBindingTest.class.getResourceAsStream(GOLDEN)) {
            if (stream == null) {
                throw new IllegalStateException("缺少 golden 资源 " + GOLDEN);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
//?}
