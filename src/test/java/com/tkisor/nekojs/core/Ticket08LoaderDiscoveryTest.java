package com.tkisor.nekojs.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 08 node-scope discovery evidence: the external addon fixture jar is
 * discovered on this node through both loader-shaped inputs — the annotation /
 * mod-metadata scan (what FML performs over a jar in the mods directory) and the
 * {@code fabric.mod.json} {@code nekojs} entrypoint list (what FabricLoader
 * reads) — each resolved in a dedicated class loader and handed to the
 * production registration seam {@link NekoJSBasePluginManager#registerClass}.
 *
 * <p>The fixture classes are never on this node's test class path: discovery is
 * only faked if the engine can already see them without the jar.
 */
class Ticket08LoaderDiscoveryTest {

    private static final String MAIN_PLUGIN = "com.example.addon.ExampleAddonPlugin";
    private static final String SECONDARY_PLUGIN = "com.example.addon.ExampleAddonSecondaryPlugin";
    private static final String CLIENT_ONLY_PLUGIN = "com.example.addon.ExampleAddonClientOnlyPlugin";
    private static final String MISSING_MOD_PLUGIN = "com.example.addon.ExampleAddonMissingModPlugin";

    @BeforeAll
    static void initPlatformStub() {
        // defensive like the other shared-tree tests: Platform may already be initialized
        Platform.init(new IPlatform() {
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
                return "test";
            }

            @Override
            public Path getGameDir() {
                return com.tkisor.nekojs.TestGameDirs.unique("nekojs-t08-discovery");
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
            public String getLoaderId() {
                return "test";
            }

            @Override
            public String getLoaderVersion() {
                return "0";
            }
        });
    }

    @Test
    void annotationScanDiscoversExternalJarWithProductionRules() throws Exception {
        try (ManagerReset ignored = new ManagerReset();
             URLClassLoader loader = newAddonClassLoader()) {
            assertFixtureNotOnNodeClassPath();
            List<String> discovered = discoverByAnnotationScan(loader);
            assertEquals(List.of(CLIENT_ONLY_PLUGIN, MAIN_PLUGIN, MISSING_MOD_PLUGIN, SECONDARY_PLUGIN)
                            .stream().filter(discovered::contains).count(),
                    4L,
                    "annotation scan finds all four annotated plugin classes in the jar");
            assertEquals(List.of(MAIN_PLUGIN, SECONDARY_PLUGIN),
                    NekoJSBasePluginManager.getPlugins().stream()
                            .map(p -> p.getClass().getName()).toList(),
                    "production rules keep the eligible plugins in priority order"
                            + " (clientOnly and missing requiredMods are filtered)");
            String codeSource = NekoJSBasePluginManager.getOwnedPlugins().get(0)
                    .identity().codeSource().toString();
            assertTrue(codeSource.contains("nekojs-external-addon-fixture"),
                    "owner identity comes from the discovered jar: " + codeSource);
        }
    }

    @Test
    void fabricModJsonEntrypointDiscoveryRegistersTheAddon() throws Exception {
        try (ManagerReset ignored = new ManagerReset();
             URLClassLoader loader = newAddonClassLoader()) {
            for (String entrypoint : fabricNekojsEntrypoints()) {
                NekoJSBasePluginManager.registerClass(Class.forName(entrypoint, false, loader));
            }
            assertEquals(List.of(MAIN_PLUGIN),
                    NekoJSBasePluginManager.getPlugins().stream()
                            .map(p -> p.getClass().getName()).toList(),
                    "fabric.mod.json nekojs entrypoint registers the main addon plugin");
        }
    }

    @Test
    void repeatedCleanDiscoveryRunsGiveIdenticalResults() throws Exception {
        List<List<String>> runs = new ArrayList<>();
        List<List<String>> codeSources = new ArrayList<>();
        for (int round = 0; round < 2; round++) {
            try (ManagerReset ignored = new ManagerReset();
                 URLClassLoader loader = newAddonClassLoader()) {
                discoverByAnnotationScan(loader);
                runs.add(NekoJSBasePluginManager.getPlugins().stream()
                        .map(p -> p.getClass().getName()).toList());
                codeSources.add(NekoJSBasePluginManager.getOwnedPlugins().stream()
                        .map(p -> p.identity().codeSource().toString()).toList());
            }
        }
        assertEquals(runs.get(0), runs.get(1), "clean run dir + same jar input → identical discovery");
        assertEquals(codeSources.get(0), codeSources.get(1), "owner identities are identical across runs");
    }

    // ---- harness -----------------------------------------------------------------------

    private static Path jarPath() {
        String path = System.getProperty("nekojs.test.externalAddonJar");
        if (path == null || !Files.isRegularFile(Path.of(path))) {
            throw new IllegalStateException("fixture jar missing (nekojs.test.externalAddonJar): " + path);
        }
        return Path.of(path);
    }

    private static URLClassLoader newAddonClassLoader() throws Exception {
        return new URLClassLoader("ticket08-node-addon", new URL[] {jarPath().toUri().toURL()},
                Ticket08LoaderDiscoveryTest.class.getClassLoader());
    }

    private static void assertFixtureNotOnNodeClassPath() throws Exception {
        for (String name : List.of(MAIN_PLUGIN, "com.example.addon.ExampleAddonSurface")) {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName(name, false, Ticket08LoaderDiscoveryTest.class.getClassLoader()),
                    name + " must not be visible without the jar");
        }
    }

    /** Annotation/mod-metadata scan of the jar (the FML mods-directory input shape). */
    private static List<String> discoverByAnnotationScan(URLClassLoader loader) throws Exception {
        List<Class<?>> annotated = new ArrayList<>();
        try (JarFile jar = new JarFile(jarPath().toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String entry = entries.nextElement().getName();
                if (!entry.endsWith(".class") || entry.endsWith("module-info.class")) {
                    continue;
                }
                String binary = entry.substring(0, entry.length() - ".class".length()).replace('/', '.');
                Class<?> clazz = Class.forName(binary, false, loader);
                if (clazz.getAnnotation(RegisterNekoJSPlugin.class) != null) {
                    annotated.add(clazz);
                }
            }
        }
        for (Class<?> clazz : annotated) {
            NekoJSBasePluginManager.registerClass(clazz);
        }
        return annotated.stream().map(Class::getName).toList();
    }

    /** The {@code nekojs} entrypoint classes from the jar's fabric.mod.json. */
    private static List<String> fabricNekojsEntrypoints() throws Exception {
        try (JarFile jar = new JarFile(jarPath().toFile());
             InputStream in = jar.getInputStream(jar.getJarEntry("fabric.mod.json"))) {
            JsonObject metadata = JsonParser.parseString(
                    new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray nekojs = metadata.getAsJsonObject("entrypoints").getAsJsonArray("nekojs");
            List<String> classes = new ArrayList<>();
            nekojs.forEach(element -> classes.add(element.getAsString()));
            return classes;
        }
    }

    /** Swap of the manager's static registry (its documented private test seam). */
    private static final class ManagerReset implements AutoCloseable {
        private final Object entries;
        private final Object sorted;
        private final Object owned;

        ManagerReset() throws Exception {
            synchronized (NekoJSBasePluginManager.class) {
                entries = field("ENTRIES").get(null);
                sorted = field("sortedView").get(null);
                owned = field("ownedView").get(null);
                field("ENTRIES").set(null, new CopyOnWriteArrayList<>());
                field("sortedView").set(null, null);
                field("ownedView").set(null, null);
            }
        }

        private static Field field(String name) throws Exception {
            Field f = NekoJSBasePluginManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }

        @Override
        public void close() throws Exception {
            synchronized (NekoJSBasePluginManager.class) {
                field("ENTRIES").set(null, entries);
                field("sortedView").set(null, sorted);
                field("ownedView").set(null, owned);
            }
        }
    }
}
