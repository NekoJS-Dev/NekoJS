//? if neoforge {
package com.tkisor.nekojs.testfixture;

import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

/** Owns only a missing headless platform; native registry bootstrap remains a separate fixture. */
public final class RecipeFilterPlatformFixture implements AutoCloseable {
    private final Field singleton;
    private final IPlatform installed;

    private RecipeFilterPlatformFixture() throws ReflectiveOperationException {
        singleton = Platform.class.getDeclaredField("INSTANCE");
        singleton.setAccessible(true);
        synchronized (Platform.class) {
            if (singleton.get(null) == null) {
                installed = new HeadlessPlatform();
                Platform.init(installed);
            } else {
                installed = null;
            }
        }
    }

    public static RecipeFilterPlatformFixture open() {
        try {
            return new RecipeFilterPlatformFixture();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot enter the recipe filter platform fixture", failure);
        }
    }

    @Override
    public void close() {
        if (installed == null) return;
        synchronized (Platform.class) {
            try {
                if (singleton.get(null) != installed) {
                    throw new IllegalStateException("The owned recipe filter test platform was replaced");
                }
                singleton.set(null, null);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot release the owned recipe filter test platform", failure);
            }
        }
    }

    private static final class HeadlessPlatform implements IPlatform {
        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "unknown"; }
        @Override public Path getGameDir() {
            return Path.of(System.getProperty("java.io.tmpdir"), "nekojs-recipe-filter-" + ProcessHandle.current().pid());
        }
        @Override public Map<String, IModInfo> getMods() { return Map.of(); }
        @Override public IModInfo getInfo(String modId) { return null; }
        @Override public String getLoaderId() { return "test"; }
        @Override public String getLoaderVersion() { return "0"; }
    }
}
//?}
