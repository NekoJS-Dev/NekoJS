package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.catalog.NekoCatalogPlatformProvider;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.TypeOutputLayout;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.pack.sync.PackHasher;
import com.tkisor.nekojs.core.pack.sync.SyncedPack;
import com.tkisor.nekojs.script.WorkspaceGenerator;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end smoke test for the {@code modifyWorkspaceConfig} hook.
 *
 * <p>{@link WorkspaceGenerator#createWorkspaceConfigs()} calls this hook on every registered plugin
 * (once per env) before it writes each ScriptType's {@code jsconfig.json} model, and no built-in
 * plugin implemented it. This test runs the whole flow in a controlled {@code @TempDir} game
 * directory and checks that all four envs (server/client/startup/test) fire once each and that the
 * plugin's change to the model (an added include entry) lands in the written jsconfig.json.
 *
 * <p>Test setup:
 * <ul>
 *   <li>{@code NekoJSPaths.get()} is a lazily initialized singleton that may already point at a
 *       shared test directory, so reflection redirects it to this test's TempDir and restores it
 *       afterwards, keeping all reads and writes inside the controlled directory;</li>
 *   <li>The {@code NekoScriptCatalog} platform provider defaults to
 *       {@code EMPTY.outputLayout()==null} and {@code createSnippets()} would NPE there. Production
 *       injects it from the platform layer ({@code NeoForgeRuntimeBootstrap} /
 *       {@code ForgeRuntimeBootstrap}), so this test injects a minimal implementation that points
 *       at the TempDir;</li>
 *   <li>Global plugin manager state is backed up and restored with the test hook used by
 *       {@code NekoJSBasePluginManagerTest}.</li>
 * </ul>
 */
class WorkspaceGeneratorModifyConfigHookTest {

    @TempDir
    Path gameDir;

    private static final Field PATHS_INSTANCE_FIELD = field(NekoJSPaths.class, "INSTANCE");
    private static final Field ENTRIES_FIELD = field(NekoJSBasePluginManager.class, "ENTRIES");
    private static final Field SORTED_VIEW_FIELD = field(NekoJSBasePluginManager.class, "sortedView");
    private static final Field OWNED_VIEW_FIELD = field(NekoJSBasePluginManager.class, "ownedView");

    private Object previousPaths;
    private Object previousEntries;
    private Object previousSortedView;
    private Object previousOwnedView;

    @BeforeEach
    void setUp() throws Exception {
        TestPlatformInit.ensureInitialized(gameDir);

        // Redirect the NekoJSPaths singleton to this test's TempDir: WorkspaceGenerator reads and writes
        // only through NekoJSPaths.get()
        previousPaths = PATHS_INSTANCE_FIELD.get(null);
        NekoJSPaths paths = NekoJSPaths.fromGameDir(gameDir);
        PATHS_INSTANCE_FIELD.set(null, paths);
        paths.initFolders();

        // Back up and clear the global plugin manager state
        previousEntries = ENTRIES_FIELD.get(null);
        previousSortedView = SORTED_VIEW_FIELD.get(null);
        previousOwnedView = OWNED_VIEW_FIELD.get(null);
        ENTRIES_FIELD.set(null, new CopyOnWriteArrayList<>());
        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);

        // createSnippets needs outputLayout, and the EMPTY provider returns null, which NPEs; inject a
        // minimal implementation and restore it after the test
        NekoScriptCatalog.setPlatformProvider(new NekoCatalogPlatformProvider() {
            @Override
            public TypeOutputLayout outputLayout() {
                return new TypeOutputLayout(paths.probeDir(), paths.probeDir().resolve("snippets.json"));
            }
        });

        RecordingWorkspacePlugin.reset();
        NekoJSBasePluginManager.registerClass(RecordingWorkspacePlugin.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        NekoScriptCatalog.setPlatformProvider(null);

        SORTED_VIEW_FIELD.set(null, null);
        OWNED_VIEW_FIELD.set(null, null);
        ENTRIES_FIELD.set(null, previousEntries == null ? new CopyOnWriteArrayList<>() : previousEntries);

        PATHS_INSTANCE_FIELD.set(null, previousPaths);
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void createWorkspaceConfigsFiresHookPerEnvAndWritesPluginMutations() throws Exception {
        WorkspaceGenerator.createWorkspaceConfigs();

        assertEquals(new HashSet<>(List.of("server", "client", "startup", "test")),
                new HashSet<>(RecordingWorkspacePlugin.envs),
                "modifyWorkspaceConfig 必须对每个 ScriptType 的 env 各触发一次");

        // Plugin changes to the jsconfig model must land on disk when the model is written. No file
        // exists inside the TempDir yet, so every file is written fresh.
        NekoJSPaths paths = NekoJSPaths.get();
        for (Path scriptDir : List.of(paths.serverScripts(), paths.clientScripts(),
                paths.startupScripts(), paths.testScripts())) {
            Path config = scriptDir.resolve("jsconfig.json");
            assertTrue(Files.exists(config), "jsconfig.json 应写出: " + config);
            String json = Files.readString(config);
            assertTrue(json.contains(RecordingWorkspacePlugin.MARKER_INCLUDE),
                    "插件追加的 include 应写入 " + config + "，实际内容: " + json);
        }
    }

    @Test
    void workspaceGenerationPreservesSignedAndServerCacheContent() throws Exception {
        ScriptPackRegistry registry = ScriptPackRegistry.get();
        List<Field> fields = List.of(field(ScriptPackRegistry.class, "globalPacks"),
                field(ScriptPackRegistry.class, "worldPacks"),
                field(ScriptPackRegistry.class, "serverCachePacks"),
                field(ScriptPackRegistry.class, "globalScanned"));
        List<Object> previous = new ArrayList<>();
        for (Field entry : fields) previous.add(entry.get(registry));
        try {
            Path packs = NekoJSPaths.get().root().resolve("packs");
            Path signed = packs.resolve("signed");
            Path local = packs.resolve("local");
            Path cached = gameDir.resolve("owned-cache").resolve(SyncedPack.encodeSyncId("packs:cached"));
            for (Path pack : List.of(signed, local, cached)) {
                Files.createDirectories(pack.resolve("client_scripts"));
                Files.writeString(pack.resolve("client_scripts/proof.js"), "console.info('PACK-FIXTURE');\n");
                Files.writeString(pack.resolve("manifest.json"),
                        pack.equals(signed)
                                ? "{\"id\":\"signed\",\"signature\":{\"algorithm\":\"Ed25519\"}}"
                                : "{\"id\":\"local\"}");
            }
            Files.createDirectories(signed.resolve("server_scripts"));
            Path existing = signed.resolve("server_scripts/jsconfig.json");
            String edited = "{\"user\":\"preserve signed bytes\"}\n";
            Files.writeString(existing, edited);
            String signedHash = PackHasher.hashPackDir(signed);
            String cachedHash = PackHasher.hashPackDir(cached);
            registry.refreshGlobalPacks(packs);
            registry.activateServerCachePacks(cached.getParent());

            WorkspaceGenerator.createWorkspaceConfigs();
            WorkspaceGenerator.createWorkspaceConfigs();

            assertEquals(signedHash, PackHasher.hashPackDir(signed),
                    "workspace generation must not invalidate a signed content hash");
            assertEquals(cachedHash, PackHasher.hashPackDir(cached),
                    "workspace generation must preserve received server-cache bytes");
            assertFalse(Files.exists(signed.resolve("client_scripts/jsconfig.json")));
            assertFalse(Files.exists(cached.resolve("client_scripts/jsconfig.json")));
            assertEquals(edited, Files.readString(existing));
            assertTrue(Files.isRegularFile(local.resolve("client_scripts/jsconfig.json")),
                    "unsigned local packs still receive their workspace configuration");
            assertTrue(Files.isRegularFile(NekoJSPaths.get().clientScripts().resolve("jsconfig.json")),
                    "the root-owned workspace is still generated");
        } finally {
            for (int i = 0; i < fields.size(); i++) fields.get(i).set(registry, previous.get(i));
        }
    }

    /** Records the environment of every modifyWorkspaceConfig call and appends an assertable
     * include marker to the model. */
    @RegisterNekoJSPlugin(priority = 1000)
    public static class RecordingWorkspacePlugin implements NekoJSPlugin {
        static final String MARKER_INCLUDE = "plugin_smoke/**/*.d.ts";
        static final List<String> envs = new CopyOnWriteArrayList<>();

        static void reset() {
            envs.clear();
        }

        @Override
        public void modifyWorkspaceConfig(JSConfigModel model, String env) {
            envs.add(env);
            List<String> includes = new ArrayList<>(model.include);
            includes.add(MARKER_INCLUDE);
            model.include = includes;
        }
    }
}
