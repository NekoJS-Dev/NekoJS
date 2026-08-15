package com.tkisor.nekojs.core.log;

import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link NekoJSLoggers} 的并发注册安全。
 *
 * <p>修复前 {@code createLogger} 是 check-then-act，并且直接修改全局 Log4j
 * Configuration；同一 logger name 的并发首次调用可能注册重复的
 * FileAppender / AsyncAppender / CollapsingAppender 以及重复的 {@code APPENDERS} 条目。
 *
 * <p>Log4j / SLF4J 在 common 模块中只有 {@code compileOnly} / {@code testRuntimeOnly} 依赖，
 * 测试编译类路径看不到它们，因此这里通过反射调用 {@code NekoJSLoggers}、Log4j
 * {@code LoggerContext} 与 {@code Configuration}，避免测试代码直接 import。
 */
class NekoJSLoggersConcurrencyTest {

    private static final Path GAME_DIR = Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir");
    private static final Field CACHE_FIELD = field(NekoJSLoggers.class, "CACHE");
    private static final Field APPENDERS_FIELD = field(NekoJSLoggers.class, "APPENDERS");

    private final List<String> createdLoggerNames = new ArrayList<>();

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
        // 固定的非临时目录：FileAppender 会持有日志文件句柄，若放在 JUnit @TempDir 中，
        // 目录清理会在 Windows 上因文件被占用而失败。
        TestPlatformInit.ensureInitialized(GAME_DIR);
    }

    @AfterEach
    void cleanUpCreatedLoggers() {
        for (String name : new ArrayList<>(createdLoggerNames)) {
            cleanup(name);
        }
        createdLoggerNames.clear();
    }

    @Test
    void concurrentGetReturnsSameLoggerAndRegistersSingleAppenderSet() throws Exception {
        String name = newLoggerName("get");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return NekoJSLoggers.class.getMethod("get", String.class).invoke(null, name);
                }));
            }
            start.countDown();

            Object first = null;
            for (Future<Object> future : futures) {
                Object logger = future.get(30, TimeUnit.SECONDS);
                assertNotNull(logger);
                if (first == null) {
                    first = logger;
                }
                assertSame(first, logger, "all threads must observe the same Logger instance");
            }

            assertSingleAppenderSet(name);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCreateLoggerRegistersSingleAppenderSet() throws Exception {
        String name = newLoggerName("direct");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return NekoJSLoggers.class.getMethod("createLogger", String.class).invoke(null, name);
                }));
            }
            start.countDown();

            Object first = null;
            for (Future<Object> future : futures) {
                Object logger = future.get(30, TimeUnit.SECONDS);
                assertNotNull(logger);
                if (first == null) {
                    first = logger;
                }
                assertSame(first, logger, "all threads must observe the same Logger instance");
            }

            assertSingleAppenderSet(name);
        } finally {
            pool.shutdownNow();
        }
    }

    private String newLoggerName(String suffix) {
        String name = "test-concurrent-" + suffix + "-" + UUID.randomUUID();
        createdLoggerNames.add(name);
        return name;
    }

    private static void assertSingleAppenderSet(String name) throws Exception {
        Object cfg = log4jConfiguration();

        String collapseName = "NekoJS-Collapse-" + name;
        String fileAppenderName = "NekoJS-File-" + name;

        Map<?, ?> appenders = (Map<?, ?>) cfg.getClass().getMethod("getAppenders").invoke(cfg);
        long collapseAppenderCount = appenders.keySet().stream()
                .filter(collapseName::equals)
                .count();
        long fileAppenderCount = appenders.keySet().stream()
                .filter(fileAppenderName::equals)
                .count();

        assertEquals(1, collapseAppenderCount,
                "Log4j Configuration must contain exactly one CollapsingAppender for " + name);
        assertEquals(1, fileAppenderCount,
                "Log4j Configuration must contain exactly one FileAppender for " + name);
        assertEquals(1, appenderListCount(collapseName),
                "APPENDERS must contain exactly one CollapsingAppender for " + name);
    }

    private static long appenderListCount(String appenderName) throws Exception {
        Object value = APPENDERS_FIELD.get(null);
        if (!(value instanceof List<?> list)) {
            return 0;
        }
        long count = 0;
        for (Object item : list) {
            if (appenderName.equals(appenderNameOf(item))) {
                count++;
            }
        }
        return count;
    }

    private static String appenderNameOf(Object appender) throws Exception {
        return (String) appender.getClass().getMethod("getName").invoke(appender);
    }

    private static Object log4jConfiguration() throws Exception {
        Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
        Object ctx = logManagerClass.getMethod("getContext", boolean.class).invoke(null, false);
        return ctx.getClass().getMethod("getConfiguration").invoke(ctx);
    }

    private static Object log4jContext() throws Exception {
        Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager");
        return logManagerClass.getMethod("getContext", boolean.class).invoke(null, false);
    }

    private static void cleanup(String name) {
        try {
            Object ctx = log4jContext();
            Object cfg = ctx.getClass().getMethod("getConfiguration").invoke(ctx);

            String collapseName = "NekoJS-Collapse-" + name;
            String asyncName = "NekoJS-Async-" + name;
            String fileAppenderName = "NekoJS-File-" + name;
            String loggerName = "nekojs." + name;

            // 先清掉 APPENDERS 中的 CollapsingAppender（修复前的并发重复项也在这里）。
            Object value = APPENDERS_FIELD.get(null);
            if (value instanceof List<?> list) {
                for (Object item : new ArrayList<>(list)) {
                    if (collapseName.equals(appenderNameOf(item))) {
                        invokeNoArgs(item, "stop");
                        list.remove(item);
                    }
                }
            }

            // 再从 Log4j Configuration 中停止并移除 appender / logger config。
            for (String appenderName : List.of(collapseName, asyncName, fileAppenderName)) {
                if (cfgAppender(cfg, appenderName) != null) {
                    cfg.getClass().getMethod("removeAppender", String.class).invoke(cfg, appenderName);
                }
            }
            cfg.getClass().getMethod("removeLogger", String.class).invoke(cfg, loggerName);
            ctx.getClass().getMethod("updateLoggers").invoke(ctx);

            // 反射清理 NekoJSLoggers.CACHE，避免影响同一 JVM 内后续用例。
            Object cache = CACHE_FIELD.get(null);
            if (cache instanceof ConcurrentHashMap) {
                ((ConcurrentHashMap) cache).remove(name);
            }

            Path logFile = NekoJSPaths.get().gameDir()
                    .resolve("logs").resolve("nekojs").resolve(name + ".log");
            Files.deleteIfExists(logFile);
        } catch (Exception e) {
            // 清理失败不应掩盖测试失败；修复后正常情况下不会走到这里。
            System.err.println("[NekoJSLoggersConcurrencyTest] cleanup failed for " + name + ": " + e);
        }
    }

    private static Object cfgAppender(Object cfg, String appenderName) throws Exception {
        Method getAppender = cfg.getClass().getMethod("getAppender", String.class);
        return getAppender.invoke(cfg, appenderName);
    }

    private static void invokeNoArgs(Object target, String methodName) throws Exception {
        target.getClass().getMethod(methodName).invoke(target);
    }
}
