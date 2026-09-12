package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.BindingDeclarationGenerator;
import com.tkisor.nekojs.util.selector.EntitySelectorsJS;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 25 EntitySelectors query binding fixture（跨节点：neoforge 1.21.1/26.x + fabric）：
 *
 * <ul>
 *   <li><b>非法输入</b>：null level / null config 得到带域+调用入口的普通错误，
 *       不含修复提示（源位置由统一错误管线从脚本栈提取）。</li>
 *   <li><b>declaration parity</b>：经生产 probe 的 {@link BindingDeclarationGenerator}
 *       生成的 TS declaration 与真实注册形态一致（golden 只读；重复生成逐字节稳定）。</li>
 *   <li><b>裸 JVM 约束</b>：builder 校验（limit/distance/gamemode/type/level）、
 *       selector 语法与 {@code find} 执行都触及 MC 类初始化链（{@code EntitySelector}
 *       的 {@code <clinit>} 要求 FML Loader）或真实 ServerLevel——由 runServer 的
 *       {@code /nekojs test} fixture 承载（REPORT §5），不在本类冒充。</li>
 * </ul>
 */
class EntitySelectorsQueryBindingTest {

    private static final String GOLDEN = "/golden/query/entityselectors-binding.d.txt";

    @Test
    void declarationGenerationIsByteStableAndMatchesGolden() throws IOException {
        // 与生产 catalog 相同的 entry 形态：实例 binding（EntitySelectorsPlugin 注册 EntitySelectorsJS 实例）
        BindingCatalogEntry entry =
                BindingCatalogEntry.of("EntitySelectors", ScriptType.SERVER, EntitySelectorsJS.class, false);
        BindingDeclarationGenerator generator = new BindingDeclarationGenerator();

        String first = generator.generate(List.of(entry), ScriptType.SERVER);
        String second = generator.generate(List.of(entry), ScriptType.SERVER);
        assertEquals(first, second, "repeated declaration generation must be byte-stable");

        assertEquals(normalize(readGolden()), normalize(first),
                "EntitySelectors binding declaration changed. 确认是刻意变更后，更新 " + GOLDEN
                        + " 并在 REPORT golden 差异小节留记录");
    }

    /** 非法输入：null level → 带 EntitySelectors.find 域+入口的普通错误。 */
    @Test
    void findNullLevelErrorCarriesDomainAndEntry() {
        EntitySelectorsJS selectors = new EntitySelectorsJS();

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> selectors.find(null, null));
        assertEquals("EntitySelectors.find: level must not be null", error.getMessage());
    }

    /** 非法输入：null config → 带 EntitySelectors.create 域+入口的普通错误。 */
    @Test
    void createNullConfigErrorCarriesDomainAndEntry() {
        EntitySelectorsJS selectors = new EntitySelectorsJS();

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> selectors.create(null));
        assertEquals("EntitySelectors.create: config must not be null", error.getMessage());
    }

    /** 工厂预设方法全部存在（server/test side 生成入口；execution 由 runServer fixture 验证）。 */
    @Test
    void factoryPresetsAreDeclared() throws Exception {
        EntitySelectorsJS selectors = new EntitySelectorsJS();
        for (String preset : List.of("allPlayers", "allEntities", "nearestPlayer",
                "nearestEntity", "randomPlayer", "randomEntity")) {
            assertEquals(EntitySelectorsJS.class, selectors.getClass(),
                    "presets are instance methods on the shared facade");
            assertTrue(EntitySelectorsJS.class.getMethod(preset).getReturnType().getName()
                            .equals("com.tkisor.nekojs.util.selector.EntitySelectorBuilderJS"),
                    "preset '" + preset + "' must return the builder");
        }
    }

    // ---- helpers ----

    private static String normalize(String value) {
        return value.lines().map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("//"))
                .collect(Collectors.joining("\n"));
    }

    private static String readGolden() throws IOException {
        try (InputStream stream = EntitySelectorsQueryBindingTest.class.getResourceAsStream(GOLDEN)) {
            if (stream == null) {
                throw new IllegalStateException("缺少 golden 资源 " + GOLDEN);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
