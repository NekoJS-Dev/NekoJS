package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Ticket 08 test harness: drives the real external addon fixture jar through the
 * production discovery seam ({@link NekoJSBasePluginManager#registerClass}).
 *
 * <p>The fixture jar is NOT on any test classpath — the harness opens it through a
 * dedicated {@link URLClassLoader}, mimicking how a loader hands discovered mod
 * classes to the engine. Two input shapes are provided:
 *
 * <ul>
 *   <li><b>jar / mods-dir shape</b> (the real target release form): scan the jar's
 *       class entries, resolve each annotated class in the addon class loader, and
 *       hand it to the production registration seam — the annotation filter,
 *       {@code clientOnly}/{@code requiredMods} rules and priority ordering all run
 *       in production code;</li>
 *   <li><b>fabric.mod.json shape</b>: read the {@code nekojs} entrypoint list from
 *       the jar's Fabric metadata and register those classes through the same seam
 *       (mirrors {@code FabricPluginLoader.loadPlugins}).</li>
 * </ul>
 */
final class ExternalAddonFixture {

    static final String ADDON_PACKAGE = "com.example.addon";
    static final String MAIN_PLUGIN = ADDON_PACKAGE + ".ExampleAddonPlugin";
    static final String SECONDARY_PLUGIN = ADDON_PACKAGE + ".ExampleAddonSecondaryPlugin";
    static final String CLIENT_ONLY_PLUGIN = ADDON_PACKAGE + ".ExampleAddonClientOnlyPlugin";
    static final String MISSING_MOD_PLUGIN = ADDON_PACKAGE + ".ExampleAddonMissingModPlugin";
    static final String SURFACE = ADDON_PACKAGE + ".ExampleAddonSurface";

    private ExternalAddonFixture() {
    }

    /** Path of the built fixture jar, injected by the Gradle test wiring. */
    static Path jarPath() {
        String path = System.getProperty("nekojs.test.externalAddonJar");
        if (path == null || !Files.isRegularFile(Path.of(path))) {
            throw new IllegalStateException(
                    "fixture jar not available (expected system property nekojs.test.externalAddonJar): " + path);
        }
        return Path.of(path);
    }

    /** Fresh class loader over the fixture jar — one isolated addon world per call. */
    static URLClassLoader openJarLoader() throws Exception {
        return new URLClassLoader("ticket08-addon-fixture", new URL[] {jarPath().toUri().toURL()},
                ExternalAddonFixture.class.getClassLoader());
    }

    /** The fixture classes must not be reachable from the engine/test class path. */
    static void assertNotOnEngineClassPath() throws Exception {
        ClassLoader engine = ExternalAddonFixture.class.getClassLoader();
        for (String name : List.of(MAIN_PLUGIN, SURFACE)) {
            try {
                Class.forName(name, false, engine);
                throw new AssertionError(name + " must not be visible on the engine class path"
                        + " (discovery may not be faked by classpath visibility)");
            } catch (ClassNotFoundException expected) {
                // pass
            }
        }
    }

    /** Jar/mods-dir shaped discovery: annotated classes resolved through the addon loader. */
    static List<Class<?>> scanAnnotatedPluginClasses(URLClassLoader loader) throws Exception {
        List<Class<?>> found = new ArrayList<>();
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
                    found.add(clazz);
                }
            }
        }
        return found;
    }

    /** fabric.mod.json shaped discovery: the {@code nekojs} entrypoint classes. */
    static List<String> fabricNekojsEntrypoints() throws Exception {
        try (JarFile jar = new JarFile(jarPath().toFile());
             InputStream in = jar.getInputStream(jar.getJarEntry("fabric.mod.json"))) {
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            JsonObject metadata = JsonParser.parseString(json).getAsJsonObject();
            JsonObject entrypoints = metadata.getAsJsonObject("entrypoints");
            if (entrypoints == null) {
                return List.of();
            }
            JsonArray nekojs = entrypoints.getAsJsonArray("nekojs");
            List<String> classes = new ArrayList<>();
            if (nekojs != null) {
                nekojs.forEach(element -> classes.add(element.getAsString()));
            }
            return classes;
        }
    }

    /** NeoForge production metadata presence in the fixture jar (mods-dir input sanity). */
    static String neoforgeModsToml() throws Exception {
        try (JarFile jar = new JarFile(jarPath().toFile());
             InputStream in = jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /** Registers every discovered fixture class through the production seam. */
    static void registerDiscovered(List<Class<?>> classes) {
        for (Class<?> clazz : classes) {
            NekoJSBasePluginManager.registerClass(clazz);
        }
    }

    // ---- reflection helpers over the addon class world ------------------------------

    /**
     * Minimal contract set accepted by {@code bootstrapOwned}: the frozen registry set
     * requires exactly one {@code nekojs-core} PORTABLE contract (same shape as the
     * empty preview used by {@code ApiSurfaceBootstrapTest}).
     */
    static VerifiedContractSet corePreviewContracts() throws Exception {
        java.net.URI codeSource = NekoJS.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0"));
        NormativeApiContract contract = new NormativeApiContract(
                2,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0")),
                null, List.of(), List.of(), List.of());
        return VerifiedContractSet.of(VerifiedApiContract.create(identity, contract, codeSource,
                "nekojs/api-contract/preview", "sha256:preview", "sha256:preview"));
    }

    static Object surfaceOf(URLClassLoader loader) throws Exception {
        return loader.loadClass(SURFACE).getMethod("get").invoke(null);
    }

    static Object call(Object target, String method, Object... args) throws Exception {
        Method found = null;
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                found = m;
                break;
            }
        }
        if (found == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + method);
        }
        try {
            return found.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw e;
        }
    }

    /** Snapshot + swap of the manager's static registry (private test seam of the manager). */
    static final class ManagerEntriesSnapshot implements AutoCloseable {
        private final Object previousEntries;
        private final Object previousSorted;
        private final Object previousOwned;

        ManagerEntriesSnapshot() throws Exception {
            synchronized (NekoJSBasePluginManager.class) {
                this.previousEntries = entriesField().get(null);
                this.previousSorted = field("sortedView").get(null);
                this.previousOwned = field("ownedView").get(null);
                entriesField().set(null, new java.util.concurrent.CopyOnWriteArrayList<>());
                field("sortedView").set(null, null);
                field("ownedView").set(null, null);
            }
        }

        private static Field entriesField() throws Exception {
            return field("ENTRIES");
        }

        private static Field field(String name) throws Exception {
            Field f = NekoJSBasePluginManager.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        }

        @Override
        public void close() throws Exception {
            synchronized (NekoJSBasePluginManager.class) {
                entriesField().set(null, previousEntries);
                field("sortedView").set(null, previousSorted);
                field("ownedView").set(null, previousOwned);
            }
        }
    }
}
