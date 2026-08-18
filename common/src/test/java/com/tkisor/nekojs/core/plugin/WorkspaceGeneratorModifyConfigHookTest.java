package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code modifyWorkspaceConfig} 钩子的端到端冒烟测试。
 *
 * <p>{@link WorkspaceGenerator#createWorkspaceConfigs()} 在每个 ScriptType 的
 * {@code jsconfig.json} 模型写盘前遍历已登记插件调用该钩子（每个 env 一次），此前没有任何
 * 内置插件实现它。本测试在受控的 {@code @TempDir} 游戏目录里跑完整流程，断言：
 * 四个 env（server/client/startup/test）各触发一次、且插件对模型的修改
 * （追加 include 条目）落到写出的 jsconfig.json 中。
 *
 * <p>测试设施说明：
 * <ul>
 *   <li>{@code NekoJSPaths.get()} 是懒初始化单例、可能已指向共享测试目录——反射重定向到
 *       本测试的 TempDir（测后恢复原值），使读写在受控目录内进行；</li>
 *   <li>{@code NekoScriptCatalog} 的 platform provider 默认 {@code EMPTY.outputLayout()==null}，
 *       {@code createSnippets()} 会 NPE——生产中由平台层
 *       （{@code NeoForgeRuntimeBootstrap} / {@code ForgeRuntimeBootstrap}）注入，
 *       这里注入指向 TempDir 的最小实现；</li>
 *   <li>插件管理器全局态的备份/恢复照 {@code NekoJSBasePluginManagerTest} 的 test seam 用法。</li>
 * </ul>
 */
class WorkspaceGeneratorModifyConfigHookTest {

    @TempDir
    Path gameDir;

    private static final Field PATHS_INSTANCE_FIELD = field(NekoJSPaths.class, "INSTANCE");
    private static final Field ENTRIES_FIELD = field(NekoJSBasePluginManager.class, "ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field(NekoJSBasePluginManager.class, "sortedView");
    private static final Field OWNED_VIEW_FIELD = field(NekoJSBasePluginManager.class, "ownedView");

    private Object previousPaths;
    private Object previousEntries;
    private Object previousSortedView;
    private Object previousOwnedView;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);

        // NekoJSPaths 单例重定向到本测试的 TempDir（WorkspaceGenerator 的全部读写经 NekoJSPaths.get()）
        previousPaths = PATHS_INSTANCE_FIELD.get(null);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        PATHS_INSTANCE_FIELD.set(null, paths);
        paths.initFolders();

        // 插件管理器全局态备份 + 清空
        previousEntries = ENTRIES_FIELD.get(null);
        previousSortedView = SORTED_VIEW_FIELD.get(null);
        previousOwnedView = OWNED_VIEW_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);

        // createSnippets 需要 outputLayout；EMPTY provider 返回 null 会 NPE，注入最小实现（测后恢复）
        NekoScriptCatalog.setPlatformProvider(new NekoCatalogPlatformProvider() {
            @Override
            public TypeOutputLayout outputLayout() {
                return new TypeOutputLayout(paths.probeDir(), paths.probeDir().resolve("snippets.json"));
            }
        });

        RecordingWorkspacePlugin.reset();
        NekoJSBasePluginManager.registerClass(RecordingWorkspacePlugin.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        NekoScriptCatalog.setPlatformProvider(null);

        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);

        PATHS_INSTANCE_FIELD.set(null, previousPaths);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void createWorkspaceConfigsFiresHookPerEnvAndWritesPluginMutations() throws Exception {
        WorkspaceGenerator.createWorkspaceConfigs();

        assertEquals(new HashSet<>(List.of("server", "client", "startup", "test")),
                new HashSet<>(RecordingWorkspacePlugin.envs),
                "modifyWorkspaceConfig 必须对每个 ScriptType 的 env 各触发一次");

        // 插件对 jsconfig 模型的修改必须随写盘落地（TempDir 内文件必然不存在、全部新写）
        NekoJSPaths paths = NekoJSPaths.get();
        for (Path scriptDir : List.of(paths.serverScripts(), paths.clientScripts(),
                paths.startupScripts(), paths.testScripts())) {
            Path config = scriptDir.resolve("jsconfig.json");
            assertTrue(Files.exists(config), "jsconfig.json 应写出: " + config);
            String json = Files.readString(config);
            assertTrue(json.contains(RecordingWorkspacePlugin.MARKER_INCLUDE),
                    "插件追加的 include 应写入 " + config + "，实际内容: " + json);
        }
    }

    /** 记录每次 modifyWorkspaceConfig 的 env，并向模型追加可断言的 include 标记。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class RecordingWorkspacePlugin implements NekoJSPlugin {
        static final String MARKER_INCLUDE = "plugin_smoke/**/*.d.ts";
        static final List<String> envs = new CopyOnWriteArrayList<>();

        static void reset() {
            envs.clear();
        }

        @Override
        public void modifyWorkspaceConfig(JSConfigModel model, String env) {
            envs.add(env);
            List<String> includes = new ArrayList<>(model.include);
            includes.add(MARKER_INCLUDE);
            model.include = includes;
        }
    }
}
