package com.tkisor.nekojs.bindings.query;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.BindingCatalogEntry;
import com.tkisor.nekojs.probe.backend.typescript.BindingDeclarationGenerator;
import com.tkisor.nekojs.util.selector.EntitySelectorBuilderJS;
import com.tkisor.nekojs.util.selector.EntitySelectorsJS;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 *   <li><b>characterization（双轴审查回退后钉住现状）</b>：作用域位只由基座预设决定、
 *       未知实体类型 tag 静默接受。这两条是「本票只记录、不改公开语义」的书面证据。</li>
 *   <li><b>裸 JVM 约束</b>：builder 校验（limit/distance/gamemode/type/level）、
 *       selector 语法与 {@code find} 执行都触及 MC 类初始化链（{@code BuiltInRegistries}
 *       的 {@code <clinit>} 要求 FML Loader，同 {@code VanillaRegistryProbe} 事实）或真实
 *       ServerLevel——由 runServer 的 {@code /nekojs test} fixture 承载（REPORT §5），
 *       不在本类冒充。</li>
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

    /**
     * 工厂预设方法全部存在（server/test side 生成入口；execution 由 runServer fixture 验证）。
     *
     * <p>断言口径（工单 25 审查修正）：原实现循环里断言
     * {@code selectors.getClass() == EntitySelectorsJS.class} 恒真（新建实例的类就是它自己），
     * 且返回类型用名字字符串比较。改为：方法在 facade 上**声明**（非继承）+ 返回共享 builder
     * + 无参 + public——四项都对「脚本侧 preset 是零参实例方法」这一事实有鉴别力。
     */
    @Test
    void factoryPresetsAreDeclared() throws Exception {
        for (String preset : List.of("allPlayers", "allEntities", "nearestPlayer",
                "nearestEntity", "randomPlayer", "randomEntity")) {
            Method method = EntitySelectorsJS.class.getMethod(preset);
            assertEquals(EntitySelectorsJS.class, method.getDeclaringClass(),
                    "preset '" + preset + "' must be declared on the EntitySelectors facade itself");
            assertEquals(EntitySelectorBuilderJS.class, method.getReturnType(),
                    "preset '" + preset + "' must return the shared builder");
            assertEquals(0, method.getParameterCount(),
                    "preset '" + preset + "' is a zero-arg instance method (script-side preset)");
            assertTrue(Modifier.isPublic(method.getModifiers()),
                    "preset '" + preset + "' must be public to be visible to scripts");
        }
    }

    /**
     * <b>characterization（工单 25 双轴审查回退后钉住现状）</b>：作用域位
     * {@code includesEntities} 只由<b>基座预设</b>决定；{@code type(...)} / {@code typeTag(...)}
     * 只在玩家类型上调整该位，因此对非玩家类型<b>不切作用域</b>。
     *
     * <p>为什么裸 JUnit 只钉「基座」这一半：{@code type(...)} / {@code allPlayers()} 要走
     * {@code BuiltInRegistries.ENTITY_TYPE}，无 FML Loader 的裸 JVM 不可用
     * （{@code VanillaRegistryProbe} 事实）。「非玩家类型不切作用域」这一半由 runServer
     * fixture 钉（{@code bench/query/fixtures/test_scripts/entityselectors-query.js} 的
     * {@code scope: …} 断言组），两者合起来才是完整的 characterization。
     */
    @Test
    void scopeBitIsOwnedByBasePresetsOnly() throws Exception {
        assertFalse(includesEntitiesOf(new EntitySelectorBuilderJS()),
                "builder()/create(cfg) base is the player scope (includesEntities=false, @a semantics)");
        assertTrue(includesEntitiesOf(EntitySelectorBuilderJS.allEntities()),
                "allEntities() base is the entity scope (includesEntities=true, @e semantics)");
        assertTrue(includesEntitiesOf(EntitySelectorBuilderJS.nearestEntity()),
                "nearestEntity() base is the entity scope");
        assertTrue(includesEntitiesOf(EntitySelectorBuilderJS.randomEntity()),
                "randomEntity() base is the entity scope");
    }

    /**
     * <b>characterization（工单 25 双轴审查回退后钉住现状）</b>：未知实体类型 tag 不报错——
     * {@code resolveEntityTypeTag} 静默构造一个「没有成员」的 {@code TagKey}，于是使用它的
     * 过滤条件恒假、查询恒空（silent no-op）。
     *
     * <p>这是 spec 04 禁止的形态，属<b>已记录缺口</b>（REPORT §7，owner = 维护者裁决 /
     * domain 票）：本票只记录现状，不改公开语义。对照面：{@code type(...)} 对未知实体类型
     * <b>直接报错</b>——两者不一致本身就是缺口证据（游戏内对照见 REPORT §5.7）。
     */
    @Test
    void unknownEntityTypeTagIsSilentlyAccepted() throws Exception {
        Method resolve = EntitySelectorsJS.class.getDeclaredMethod("resolveEntityTypeTag", String.class);
        resolve.setAccessible(true);
        Object unknown = resolve.invoke(null, "nekojs:no_such_tag");

        assertNotNull(unknown, "unknown tag must NOT throw (characterized silent no-op)");
        assertEquals("nekojs:no_such_tag", tagIdOf(unknown),
                "unknown tag is still materialized as a TagKey under its own id (no declaration check)");
    }

    /**
     * 非法输入（AC3，审查后**保留**的唯一入参校验）：{@code typeTag(null)} 抛带域+入口的
     * 普通错误，而不是让 {@code Identifier.parse(null)} 抛裸 NPE。
     *
     * <p>它不改变既有可观察语义——null 本来就是错误路径，这里只把错误类型与可读性对齐到
     * AC3「错误含域+调用入口」；与 {@code distance}/limit 等消息前缀同批保留（见
     * {@code EntitySelectorsJS#resolveEntityTypeTag} javadoc）。
     */
    @Test
    void typeTagNullErrorCarriesDomainAndEntry() throws Exception {
        Method resolve = EntitySelectorsJS.class.getDeclaredMethod("resolveEntityTypeTag", String.class);
        resolve.setAccessible(true);
        java.lang.reflect.InvocationTargetException thrown = assertThrows(
                java.lang.reflect.InvocationTargetException.class, () -> resolve.invoke(null, (Object) null));

        assertTrue(thrown.getCause() instanceof IllegalArgumentException,
                "null tag must be a labeled IllegalArgumentException, not a bare NPE; got "
                        + thrown.getCause());
        assertEquals("EntitySelectors.typeTag: tag must not be null", thrown.getCause().getMessage());
    }

    // ---- helpers ----

    /** 反射读 builder 的私有作用域位（本票 characterization 的观察点）。 */
    private static boolean includesEntitiesOf(EntitySelectorBuilderJS builder) throws Exception {
        Field field = EntitySelectorBuilderJS.class.getDeclaredField("includesEntities");
        field.setAccessible(true);
        return (boolean) field.get(builder);
    }

    /** 反射读 TagKey 的 location（{@code TagKey#location()} → Identifier）。 */
    private static String tagIdOf(Object tagKey) throws Exception {
        Object location = tagKey.getClass().getMethod("location").invoke(tagKey);
        return String.valueOf(location.getClass().getMethod("toString").invoke(location));
    }

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
