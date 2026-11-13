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
 * Smoke tests for the {@code attachServerData}, {@code attachLevelData} and
 * {@code attachPlayerData} plugin hooks.
 *
 * <p>The platform mixin triggers these hooks on the first access to {@code host.data} through
 * entry points such as {@link AttachedDataHooks#fireAttachServer}. The implementation lives in
 * common and all three platform modules reuse it. This test registers recording plugins with
 * {@link NekoJSBasePluginManager} and then fires those entry points to show that the call
 * reaches the plugins: the same {@link AttachedData} instance arrives, the host reference
 * ({@code getParent()}) is kept as it was, entries a plugin adds inside the hook stay on the
 * container, the three hooks do not leak into each other, and an exception from one plugin is
 * isolated and does not stop the plugins after it.
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
        // priority 1001 > 1000, so the throwing plugin always fires before the recording plugin; the
        // order within one priority is not stable.
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

    /** Records calls to the three attach hooks and adds a marker entry to the container. The plugin
     * manager creates the instance, so recording uses static state. */
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

    /** Throws from all three attach hooks to test how the fire entry points isolate exceptions. */
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
