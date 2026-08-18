package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NekoJSBasePluginManager} 的注册 / 排序视图发布原子性。
 *
 * <p>修复契约：{@code registerClass} 两个重载与 {@code getPlugins}/{@code getOwnedPlugins}
 * 都同步在类锁上，杜绝「注册已发生但排序视图仍是旧快照」的竞态（保留 CopyOnWriteArrayList）。
 */
class NekoJSBasePluginManagerTest {

    private static final List<Class<?>> PLUGIN_CLASSES = List.of(
            PluginP0.class, PluginP1.class, PluginP2.class, PluginP3.class, PluginP4.class,
            PluginP5.class, PluginP6.class, PluginP7.class, PluginP8.class, PluginP9.class);

    private static final Field ENTRIES_FIELD = field("ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field("sortedView");
    private static final Field OWNED_VIEW_FIELD = field("ownedView");

    private Object previousEntries;
    private Object previousSortedView;
    private Object previousOwnedView;

    private static Field field(String name) {
        try {
            Field f = NekoJSBasePluginManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @BeforeEach
    void resetStaticState() throws Exception {
        previousEntries = ENTRIES_FIELD.get(null);
        previousSortedView = SORTED_VIEW_FIELD.get(null);
        previousOwnedView = OWNED_VIEW_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
    }

    @AfterEach
    void restoreStaticState() throws Exception {
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);
    }

    /**
     * 确定性竞态测试：reader 线程已进入 getPlugins 的快照计算（持有旧快照、尚未写回
     * sortedView）时，另一个线程完成 registerClass。修复前 registerClass 不受类锁约束，
     * reader 之后会把旧快照写回 sortedView，导致新注册插件在最终视图中永久缺失。
     */
    @Test
    void getPluginsDoesNotPublishStaleViewWhenRegisterInterleaves() throws Exception {
        // 先登记一个初始插件，为 reader 提供可排序的旧快照。
        NekoJSBasePluginManager.registerClass(PluginP0.class);

        BlockingEntries blockingEntries = new BlockingEntries();
        blockingEntries.addAll((List<?>) ENTRIES_FIELD.get(null));
        ENTRIES_FIELD.set(null, blockingEntries);
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<NekoJSPlugin>> reader = pool.submit(NekoJSBasePluginManager::getPlugins);
            assertTrue(blockingEntries.entered.await(10, TimeUnit.SECONDS),
                    "reader must enter ENTRIES.stream() before registrar starts");

            Future<?> registrar = pool.submit(() -> {
                NekoJSBasePluginManager.registerClass(PluginP1.class);
                return null;
            });

            // 修复前：registerClass 无锁，会立即完成；修复后：它阻塞在类锁上，直到 reader 放行。
            try {
                registrar.get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                // 修复后的预期：registrar 等待 reader 释放类锁。
            }

            blockingEntries.release.countDown();
            assertDoesNotThrow(() -> reader.get(10, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> registrar.get(10, TimeUnit.SECONDS));

            List<NekoJSPlugin> finalView = NekoJSBasePluginManager.getPlugins();
            assertEquals(2, finalView.size(),
                    "final view must contain initial plugin plus concurrently registered plugin");
            assertTrue(finalView.stream().anyMatch(PluginP1.class::isInstance),
                    "final view must not be a stale snapshot missing the concurrently registered plugin");
        } finally {
            blockingEntries.release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentRegisterClassAndGetPluginsNeverThrowsAndFinalViewIsComplete() throws Exception {
        int registrarThreads = 4;
        int getterThreads = 4;
        int rounds = 20;
        // 重复发现按 (identity, class) 去重：并发的重复 registerClass 调用下，
        // 最终视图 = 10 个唯一类各恰好一次（回归：cleanroom dev classpath 重复列出
        // common/common-api jar，同插件类被扫描两次导致 ScriptProperty 重复注册崩溃）
        int expectedSize = PLUGIN_CLASSES.size();

        ExecutorService pool = Executors.newFixedThreadPool(registrarThreads + getterThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch registrarsDone = new CountDownLatch(registrarThreads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < registrarThreads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int r = 0; r < rounds; r++) {
                        for (Class<?> clazz : PLUGIN_CLASSES) {
                            NekoJSBasePluginManager.registerClass(clazz);
                        }
                    }
                    registrarsDone.countDown();
                    return null;
                }));
            }
            for (int t = 0; t < getterThreads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    while (registrarsDone.getCount() > 0) {
                        NekoJSBasePluginManager.getPlugins();
                        NekoJSBasePluginManager.getOwnedPlugins();
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                assertDoesNotThrow(() -> future.get(60, TimeUnit.SECONDS));
            }

            assertEquals(expectedSize, NekoJSBasePluginManager.getPlugins().size(),
                    "final sorted view must contain every unique plugin class exactly once");
            assertEquals(expectedSize, NekoJSBasePluginManager.getOwnedPlugins().size(),
                    "final owned view must contain every unique plugin class exactly once");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void duplicateDiscoveryOfSameClassRegistersOnce() {
        NekoJSBasePluginManager.registerClass(PluginP0.class);
        NekoJSBasePluginManager.registerClass(PluginP0.class);

        assertEquals(1, NekoJSBasePluginManager.getPlugins().size(),
                "same class discovered twice (duplicate classpath entry) must register exactly once");
        assertEquals(1, NekoJSBasePluginManager.getOwnedPlugins().size());
    }

    /** 在 stream() 中阻塞的 CopyOnWriteArrayList：旧快照在阻塞前捕获，用于确定性复现 stale-view 竞态。 */
    private static final class BlockingEntries extends CopyOnWriteArrayList<Object> {
        private static final long serialVersionUID = 1L;
        final transient CountDownLatch entered = new CountDownLatch(1);
        final transient CountDownLatch release = new CountDownLatch(1);
        private transient volatile List<Object> snapshot;

        @Override
        public Stream<Object> stream() {
            snapshot = List.copyOf(this);
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return snapshot.stream();
        }
    }

    @RegisterNekoJSPlugin(priority = 1000)
    public static class PluginP0 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1001)
    public static class PluginP1 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1002)
    public static class PluginP2 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1003)
    public static class PluginP3 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1004)
    public static class PluginP4 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1005)
    public static class PluginP5 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1006)
    public static class PluginP6 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1007)
    public static class PluginP7 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1008)
    public static class PluginP8 implements NekoJSPlugin {}

    @RegisterNekoJSPlugin(priority = 1009)
    public static class PluginP9 implements NekoJSPlugin {}
}
