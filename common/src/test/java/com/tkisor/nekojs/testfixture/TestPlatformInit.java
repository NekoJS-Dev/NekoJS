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
    }
}
