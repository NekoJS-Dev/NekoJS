package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code attachServerData} / {@code attachLevelData} / {@code attachPlayerData} 三个插件钩子的冒烟测试。
 *
 * <p>这三个钩子由平台层 mixin 在首次访问 {@code host.data} 时经
 * {@link AttachedDataHooks#fireAttachServer} 等入口触发（common 集中实现、三个平台模块复用），
 * 但此前没有任何内置插件实现它们。本测试向 {@link NekoJSBasePluginManager} 登记录制插件后
 * 触发 fire 入口，证明链路确实会调用插件：同一 {@link AttachedData} 实例送达、宿主引用
 * （{@code getParent()}）原样保留、插件在钩子内写入的条目留在容器上、三个钩子间不串扰、
 * 且单个插件异常被隔离不中断后续插件。
 */
class AttachedDataHooksTest {

    private static final Field ENTRIES_FIELD = field("ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field("sortedView");
    private static final Field OWNED_VIEW_FIELD = field("ownedView");

    private Object previousEntries;
    private Object previousSortedView;
    private Object previousOwnedView;

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetPluginManagerAndRecordings() throws Exception {
        previousEntries = ENTRIES_FIELD.get(null);
        previousSortedView = SORTED_VIEW_FIELD.get(null);
        previousOwnedView = OWNED_VIEW_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);

        RecordingAttachPlugin.reset();
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
    void fireAttachServerInvokesPluginWithSameDataInstance() {
        NekoJSBasePluginManager.registerClass(RecordingAttachPlugin.class);
        Object serverHost = new Object();
        AttachedData<Object> data = new AttachedData<>(serverHost);

        AttachedDataHooks.fireAttachServer(data);

        assertEquals(List.of(data), RecordingAttachPlugin.serverCalls,
                "fireAttachServer 必须把同一 AttachedData 实例传给已登记插件的 attachServerData");
        assertTrue(RecordingAttachPlugin.levelCalls.isEmpty(), "不应误触 attachLevelData");
        assertTrue(RecordingAttachPlugin.playerCalls.isEmpty(), "不应误触 attachPlayerData");
        assertSame(serverHost, data.getParent(), "宿主引用应原样保留");
        assertEquals("server", data.get("nekojs.smoke.attach"), "插件在钩子内写入的条目应留在容器上");
    }

    @Test
    void fireAttachLevelInvokesPluginWithSameDataInstance() {
        NekoJSBasePluginManager.registerClass(RecordingAttachPlugin.class);
        Object levelHost = new Object();
        AttachedData<Object> data = new AttachedData<>(levelHost);

        AttachedDataHooks.fireAttachLevel(data);

        assertEquals(List.of(data), RecordingAttachPlugin.levelCalls,
                "fireAttachLevel 必须把同一 AttachedData 实例传给已登记插件的 attachLevelData");
        assertTrue(RecordingAttachPlugin.serverCalls.isEmpty(), "不应误触 attachServerData");
        assertTrue(RecordingAttachPlugin.playerCalls.isEmpty(), "不应误触 attachPlayerData");
        assertSame(levelHost, data.getParent(), "宿主引用应原样保留");
        assertEquals("level", data.get("nekojs.smoke.attach"), "插件在钩子内写入的条目应留在容器上");
    }

    @Test
    void fireAttachPlayerInvokesPluginWithSameDataInstance() {
        NekoJSBasePluginManager.registerClass(RecordingAttachPlugin.class);
        Object playerHost = new Object();
        AttachedData<Object> data = new AttachedData<>(playerHost);

        AttachedDataHooks.fireAttachPlayer(data);

        assertEquals(List.of(data), RecordingAttachPlugin.playerCalls,
                "fireAttachPlayer 必须把同一 AttachedData 实例传给已登记插件的 attachPlayerData");
        assertTrue(RecordingAttachPlugin.serverCalls.isEmpty(), "不应误触 attachServerData");
        assertTrue(RecordingAttachPlugin.levelCalls.isEmpty(), "不应误触 attachLevelData");
        assertSame(playerHost, data.getParent(), "宿主引用应原样保留");
        assertEquals("player", data.get("nekojs.smoke.attach"), "插件在钩子内写入的条目应留在容器上");
    }

    @Test
    void throwingPluginIsIsolatedAndDoesNotBlockLaterPlugins() {
        // priority 1001 > 1000：抛异常的插件必然先于录制插件触发（同优先级顺序不稳定，见注解契约）
        NekoJSBasePluginManager.registerClass(ThrowingAttachPlugin.class);
        NekoJSBasePluginManager.registerClass(RecordingAttachPlugin.class);
        AttachedData<Object> server = new AttachedData<>(new Object());
        AttachedData<Object> level = new AttachedData<>(new Object());
        AttachedData<Object> player = new AttachedData<>(new Object());

        assertDoesNotThrow(() -> AttachedDataHooks.fireAttachServer(server));
        assertDoesNotThrow(() -> AttachedDataHooks.fireAttachLevel(level));
        assertDoesNotThrow(() -> AttachedDataHooks.fireAttachPlayer(player));

        assertEquals(List.of(server), RecordingAttachPlugin.serverCalls,
                "前序插件抛异常不应中断后续插件的 attachServerData");
        assertEquals(List.of(level), RecordingAttachPlugin.levelCalls,
                "前序插件抛异常不应中断后续插件的 attachLevelData");
        assertEquals(List.of(player), RecordingAttachPlugin.playerCalls,
                "前序插件抛异常不应中断后续插件的 attachPlayerData");
    }

    /** 记录三个 attach 钩子调用并向容器写入标记条目（实例由插件管理器创建，录制走静态状态）。 */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class RecordingAttachPlugin implements NekoJSPlugin {
        static final List<AttachedData<?>> serverCalls = new CopyOnWriteArrayList<>();
        static final List<AttachedData<?>> levelCalls = new CopyOnWriteArrayList<>();
        static final List<AttachedData<?>> playerCalls = new CopyOnWriteArrayList<>();

        static void reset() {
            serverCalls.clear();
            levelCalls.clear();
            playerCalls.clear();
        }

        @Override
        public void attachServerData(AttachedData<?> data) {
            serverCalls.add(data);
            data.add("nekojs.smoke.attach", "server");
        }

        @Override
        public void attachLevelData(AttachedData<?> data) {
            levelCalls.add(data);
            data.add("nekojs.smoke.attach", "level");
        }

        @Override
        public void attachPlayerData(AttachedData<?> data) {
            playerCalls.add(data);
            data.add("nekojs.smoke.attach", "player");
        }
    }

    /** 三个 attach 钩子全部抛异常，验证 fire 入口的异常隔离语义。 */
    @RegisterNekoJSPlugin(priority = 1001)
    public static class ThrowingAttachPlugin implements NekoJSPlugin {
        @Override
        public void attachServerData(AttachedData<?> data) {
            throw new IllegalStateException("boom-attachServerData");
        }

        @Override
        public void attachLevelData(AttachedData<?> data) {
            throw new IllegalStateException("boom-attachLevelData");
        }

        @Override
        public void attachPlayerData(AttachedData<?> data) {
            throw new IllegalStateException("boom-attachPlayerData");
        }
    }
}
