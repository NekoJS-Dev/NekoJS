package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 票 29 AC2/AC4：plugin-only 语言声明的确定性语言集合。
 *
 * <p>覆盖 {@link PluginGenerationHooks#resolveGeneratedLangs} 四条语义：
 * 默认 {@code en_us} 使无脚本 listener 时插件回调不被静默跳过、插件声明与脚本 keyed
 * listener 语言的字典序并集、非法语言在写入任何文件之前整批拒绝、单个插件声明失败只
 * 作废它自己而不污染其他插件。
 *
 * <p>插件清单的注入方式照 {@link PluginGenerationHooksTest} 的既有 test seam：反射备份并
 * 清空 {@link NekoJSBasePluginManager} 的私有静态 {@code ENTRIES}（生产代码里已注明
 * "non-final ONLY as a private test seam"），不新增也不改动生产 API。
 */
class Ticket29GeneratedLangsTest {

    private static final Field ENTRIES_FIELD = field("ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field("sortedView");
    private static final Field OWNED_VIEW_FIELD = field("ownedView");

    private Object previousEntries;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void isolatePluginManager() throws Exception {
        previousEntries = ENTRIES_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
    }

    @AfterEach
    void restorePluginManager() throws Exception {
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);
    }

    private static Field field(String name) {
        try {
            Field f = NekoJSBasePluginManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void defaultsToEnUsSoPluginCallbacksAreNotGatedByScriptListeners() {
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        // 脚本侧没有任何 ClientEvents.lang listener：旧实现下 registeredKeys() 为空，
        // generateLang 插件回调因此从不触发。默认声明必须让 en_us 仍然在集合里。
        List<String> langs = PluginGenerationHooks.resolveGeneratedLangs(Set.of());

        assertTrue(langs.contains("en_us"),
                "the default generatedLangs() must keep en_us present so the generateLang plugin"
                        + " callback is not silently skipped when no script lang listener exists;"
                        + " got " + langs);
    }

    @Test
    void unionOfPluginDeclarationsAndScriptKeysIsDeduplicatedAndSorted() {
        NekoJSBasePluginManager.registerClass(TwoLangPlugin.class);
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        List<String> first = PluginGenerationHooks.resolveGeneratedLangs(
                Set.of("sv_se", "en_us", "de_de"));
        // 同样三个脚本键，换个传入顺序：结果必须逐元素相等（TreeSet 归一，不随输入顺序漂移）。
        List<String> second = PluginGenerationHooks.resolveGeneratedLangs(
                Set.of("de_de", "en_us", "sv_se"));

        assertEquals(first, second,
                "the language list must not depend on the iteration order of the script keys");
        assertEquals(List.of("de_de", "en_us", "fr_fr", "ja_jp", "sv_se"), first,
                "plugin declarations union script keys, deduplicated by code and sorted lexicographically;"
                        + " got " + first);
    }

    @Test
    void invalidPluginLangRejectsTheWholeBatchWithOwnerAndCodeInTheMessage() {
        NekoJSBasePluginManager.registerClass(InvalidLangPlugin.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("en_us")));

        assertTrue(error.getMessage().contains(InvalidLangPlugin.class.getName()),
                "the rejection must name the owning plugin: " + error.getMessage());
        assertTrue(error.getMessage().contains("zh cn"),
                "the rejection must name the offending language code: " + error.getMessage());
    }

    @Test
    void invalidScriptLangKeyIsRejectedBeforeAnyWrite() {
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("en_us", "../evil")));

        assertTrue(error.getMessage().contains("../evil"),
                "a bad script listener key must be rejected too: " + error.getMessage());
    }

    @Test
    void overlongAndEmptyLangCodesAreRejected() {
        NekoJSBasePluginManager.registerClass(DefaultLangsPlugin.class);

        assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("x".repeat(65))),
                "codes longer than 64 characters must be rejected");
        assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of("")),
                "an empty code must be rejected");
    }

    @Test
    void oneFailingPluginDoesNotPoisonOtherPluginsDeclarations() {
        NekoJSBasePluginManager.registerClass(ThrowingLangsPlugin.class);
        NekoJSBasePluginManager.registerClass(TwoLangPlugin.class);

        // 返回而非抛出本身就是第一条区分：整批失败会抛 IllegalStateException。
        List<String> langs = PluginGenerationHooks.resolveGeneratedLangs(Set.of());

        assertEquals(List.of("fr_fr", "ja_jp"), langs,
                "a plugin whose generatedLangs() throws loses only its own declarations: the batch must"
                        + " still succeed and carry exactly the other plugin's valid declarations;"
                        + " got " + langs);
    }

    @Test
    void nullDeclarationIsTreatedAsAViolation() {
        NekoJSBasePluginManager.registerClass(NullLangsPlugin.class);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PluginGenerationHooks.resolveGeneratedLangs(Set.of()));

        assertTrue(error.getMessage().contains(NullLangsPlugin.class.getName()),
                "a null declaration must be attributed to its plugin: " + error.getMessage());
        assertTrue(error.getMessage().contains("null"),
                "a null declaration must be reported as null: " + error.getMessage());
    }

    /* ================= 桩插件（经 NekoJSBasePluginManager.registerClass 注入） ================= */

    /** 不覆写 generatedLangs()：走 default 的 Set.of("en_us")。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class DefaultLangsPlugin implements NekoJSPlugin {
    }

    /** 显式声明两种非英语言，用于证明声明面生效。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class TwoLangPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            return Set.of("ja_jp", "fr_fr");
        }
    }

    /** 声明含非法 code（含空格）。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class InvalidLangPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            return Set.of("en_us", "zh cn");
        }
    }

    /** 声明阶段抛异常：只有本插件的声明被作废。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class ThrowingLangsPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            throw new IllegalStateException("boom-generatedLangs");
        }
    }

    /** 返回 null：按违规归因。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class NullLangsPlugin implements NekoJSPlugin {
        @Override
        public Set<String> generatedLangs() {
            return null;
        }
    }
}
