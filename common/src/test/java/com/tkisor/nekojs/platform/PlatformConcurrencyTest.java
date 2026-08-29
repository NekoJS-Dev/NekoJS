package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台级懒加载单例的并发安全。
 *
 * <p>覆盖两个修复：
 * <ul>
 *   <li>{@link NekoJSPaths#get()} 的 synchronized double-checked locking：并发首次调用只允许一次
 *       {@code fromGameDir(Platform.getGameDir())} 初始化。</li>
 *   <li>{@link Platform#init(IPlatform)} 的 synchronized 双初始化保护：并发 init 只允许一个赢家，
 *       另一个线程必须得到 {@link IllegalStateException}。</li>
 * </ul>
 */
class PlatformConcurrencyTest {

    private static final Path GAME_DIR = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
    private static final Field PLATFORM_INSTANCE = field(Platform.class, "INSTANCE");
    private static final Field NEKOJS_PATHS_INSTANCE = field(NekoJSPaths.class, "INSTANCE");

    private Object previousPlatform;
    private Object previousPaths;

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
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
    void saveState() throws Exception {
        previousPlatform = PLATFORM_INSTANCE.get(null);
        previousPaths = NEKOJS_PATHS_INSTANCE.get(null);
    }

    @AfterEach
    void restoreState() throws Exception {
        NEKOJS_PATHS_INSTANCE.set(null, previousPaths);
        PLATFORM_INSTANCE.set(null, previousPlatform);
    }

    @Test
    void nekoJSPathsGetInitializesSingletonExactlyOnceAcrossThreads() throws Exception {
        CountingPlatform countingPlatform = new CountingPlatform(new TestPlatformInit.TestIPlatform(GAME_DIR));
        PLATFORM_INSTANCE.set(null, countingPlatform);
        NEKOJS_PATHS_INSTANCE.set(null, null);

        int rounds = 5;
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                NEKOJS_PATHS_INSTANCE.set(null, null);
                countingPlatform.gameDirCalls.set(0);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<NekoJSPaths>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        return NekoJSPaths.get();
                    }));
                }
                start.countDown();

                Set<NekoJSPaths> results = java.util.concurrent.ConcurrentHashMap.newKeySet();
                for (Future<NekoJSPaths> future : futures) {
                    results.add(future.get(30, TimeUnit.SECONDS));
                }

                assertEquals(1, results.size(),
                        "round " + round + ": all threads must observe the identical NekoJSPaths instance");
                assertNotNull(results.iterator().next());
                assertEquals(1, countingPlatform.gameDirCalls.get(),
                        "round " + round + ": singleton initializer must run exactly once");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentPlatformInitAllowsExactlyOneWinner() throws Exception {
        int rounds = 200;
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                PLATFORM_INSTANCE.set(null, null);
                CyclicBarrier barrier = new CyclicBarrier(threads);
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        barrier.await();
                        try {
                            Platform.init(new TestPlatformInit.TestIPlatform(GAME_DIR));
                            return true;
                        } catch (IllegalStateException expected) {
                            return false;
                        }
                    }));
                }
                int winners = 0;
                for (Future<Boolean> future : futures) {
                    winners += future.get(30, TimeUnit.SECONDS) ? 1 : 0;
                }
                assertEquals(1, winners,
                        "round " + round + ": exactly one concurrent Platform.init call may win");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void platformInitCalledTwiceThrowsIllegalStateException() throws Exception {
        PLATFORM_INSTANCE.set(null, null);
        Platform.init(new TestPlatformInit.TestIPlatform(GAME_DIR));
        assertThrows(IllegalStateException.class,
                () -> Platform.init(new TestPlatformInit.TestIPlatform(GAME_DIR)));
    }

    /** 统计 getGameDir 调用次数的 IPlatform 包装器，用于观察 NekoJSPaths 初始化是否只发生一次。 */
    private static final class CountingPlatform implements IPlatform {
        private final IPlatform delegate;
        private final AtomicInteger gameDirCalls = new AtomicInteger();

        CountingPlatform(IPlatform delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isClient() {
            return delegate.isClient();
        }

        @Override
        public boolean isDevelopment() {
            return delegate.isDevelopment();
        }

        @Override
        public String getMcVersion() {
            return delegate.getMcVersion();
        }

        @Override
        public Path getGameDir() {
            gameDirCalls.incrementAndGet();
            return delegate.getGameDir();
        }

        @Override
        public Map<String, IModInfo> getMods() {
            return delegate.getMods();
        }

        @Override
        public IModInfo getInfo(String modID) {
            return delegate.getInfo(modID);
        }

        @Override
        public Set<PlatformCapability> capabilities() {
            return delegate.capabilities();
        }

        @Override
        public String getLoaderId() {
            return delegate.getLoaderId();
        }

        @Override
        public String getLoaderVersion() {
            return delegate.getLoaderVersion();
        }
    }
}
