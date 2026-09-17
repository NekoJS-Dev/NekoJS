package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import com.tkisor.nekojs.platform.PlatformCapability;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

public final class TestPlatformInit {

    private TestPlatformInit() {
    }

    private static volatile boolean initialized = false;

    public static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        ensureInitialized(Path.of(System.getProperty("java.io.tmpdir"), "nekojs-test-gamedir"));
        initialized = true;
    }

    /**
     * 按测试 JVM 唯一化的 gameDir（base + 当前进程 PID，与根测试树
     * {@code com.tkisor.nekojs.TestGameDirs.unique} 同口径）：并行 test JVM 不会互删
     * 脚本目录。common 测试树此前用固定 {@code nekojs-test-gamedir}，helper 下沉到
     * common 是随后的整理项（票 10 报告 N8）；新增测试先用本入口。
     *
     * <p>同 JVM 内 Platform 首次初始化即生效（先到先得，见根树 TestGameDirs 说明）——
     * 后到者传入的目录被视为无害的寄生请求，因此调用本入口不会让既有测试失效。
     */
    public static Path uniqueGameDir(String base) {
        return Path.of(System.getProperty("java.io.tmpdir"), base + '-' + ProcessHandle.current().pid());
    }

    public static void ensureInitialized(Path gameDir) {
        gameDir.toFile().mkdirs();
        try {
            Field instance = Platform.class.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            if (instance.get(null) == null) {
                Platform.init(new TestIPlatform(gameDir));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Platform for tests", e);
        }
    }

    public static final class TestIPlatform implements IPlatform {
        private final Path gameDir;

        public TestIPlatform(Path gameDir) {
            this.gameDir = gameDir;
        }

        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isDevelopment() {
            return true;
        }

        @Override
        public String getMcVersion() {
            return "0.0.0";
        }

        @Override
        public Path getGameDir() {
            return gameDir;
        }

        @Override
        public Map<String, IModInfo> getMods() {
            return Map.of();
        }

        @Override
        public IModInfo getInfo(String modID) {
            return null;
        }

        @Override
        public java.util.Set<PlatformCapability> capabilities() {
            return java.util.Set.of();
        }

        @Override
        public String getLoaderId() {
            return "test";
        }

        @Override
        public String getLoaderVersion() {
            return "0.0.0";
        }
    }
}
