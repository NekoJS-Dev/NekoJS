package com.tkisor.nekojs.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoSnippetJson;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.probe.ProbeCoordinator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the workspace config files (README, jsconfig.json and others). This is platform
 * independent. Before a jsconfig.json model is written, the {@link NekoJSPlugin#modifyWorkspaceConfig}
 * hook lets plugins modify it (triggered once per ScriptType). The JSX compiler options follow the
 * engine config ({@link ClassFilter#INSTANCE} jsxAutomaticRuntime), so IDE type checking uses the
 * same JSX transform as the runtime.
 */
public final class WorkspaceGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setupWorkspace() {
        createReadme();
        ClassFilter.loadEngineConfig();
        // Like engine.toml, probe.toml is written with default values at startup when it is missing.
        // Otherwise a user opening nekojs/config/ for the first time would find only engine.toml,
        // and probe.toml would appear only after the probe command has run.
        ProbeCoordinator.ensureConfigFile();
    }

    public static void createReadme() {
        if (Files.notExists(NekoJSPaths.get().readme())) {
            try {
                String content = """
                    === NekoJS Script Directory Guide ===
                    - startup_scripts: Loaded during game startup. Used for registering items and blocks. Changes require a full game restart.
                    - server_scripts: Executed when the world/server loads. Used for recipes and event handling. Can be reloaded with /reload.
                    - client_scripts: Runs on the client only. Used for GUI, key bindings, etc.
                    - test_scripts: Explicit smoke/regression scripts. Run with /nekojs test; they are not loaded by normal startup or reload.
                    - packs: Optional script packs. Each pack is a folder with a manifest.json plus its own startup/server/client/test_scripts subfolders; load order is packs first, then the flat folders above.
                    - Signed and server-cache packs are not modified by workspace generation. Include any pack-local IDE configuration before signing the pack.
                    - Note: Automatically generated type declaration files (.d.ts) are located in the %s folder. Do not modify them manually.
                    - Tip: Write .ts (or add // @ts-check at the top of a .js file) to enable editor type-checking; run /nekojs view_all_errors in-game to inspect script errors.
                    """.formatted(NekoJSPaths.get().probeDir().getFileName()).trim();
                Files.writeString(NekoJSPaths.get().readme(), content);
            } catch (IOException ex) {
                NekoJS.LOGGER.error("Failed to create README.txt", ex);
            }
        }
    }

    public static void createWorkspaceConfigs() {
        createConfigForEnv(ScriptType.SERVER, NekoJSPaths.get().serverScripts());
        createConfigForEnv(ScriptType.CLIENT, NekoJSPaths.get().clientScripts());
        createConfigForEnv(ScriptType.STARTUP, NekoJSPaths.get().startupScripts());
        createConfigForEnv(ScriptType.TEST, NekoJSPaths.get().testScripts());
        createPackConfigs();
        createProbeDirConfig();
        createSnippets();
    }

    /**
     * Generates missing pack-local IDE configs for unsigned local packs. Signed content and
     * received server-cache bytes remain unchanged; pack-local configs must be included before signing.
     */
    private static void createPackConfigs() {
        for (com.tkisor.nekojs.core.pack.ScriptPack pack :
                com.tkisor.nekojs.core.pack.ScriptPackRegistry.get().enabledPacks()) {
            // Pack-local configs are hashed content: preserve signed inputs and received cache bytes.
            if (pack.manifest().signature() != null
                    || pack.scope() == com.tkisor.nekojs.core.pack.ScriptPackScope.SERVER_CACHE
                    || pack.scope() == com.tkisor.nekojs.core.pack.ScriptPackScope.MOD) {
                continue;
            }
            for (ScriptType type : ScriptType.all()) {
                Path dir = pack.scriptsDirFor(type);
                if (java.nio.file.Files.isDirectory(dir)) {
                    createConfigForEnv(type, dir);
                }
            }
        }
    }

    private static void createConfigForEnv(ScriptType scriptType, Path scriptDir) {
        JSConfigModel model = buildConfigForEnv(scriptType, scriptDir, NekoJSPaths.get().probeDir());

        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.modifyWorkspaceConfig(model, scriptType.name);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("Plugin {} failed modifyWorkspaceConfig for {}",
                        plugin.getClass(), scriptType.name, t);
            }
        }

        Path configPath = scriptDir.resolve("jsconfig.json");
        writeConfigIfMissing(configPath, model);
    }

    /**
     * Writes a workspace config only when it does not exist yet: reloads, validation and migration
     * must never overwrite or delete a config the user edited in their external IDE. Returns true
     * when the file was written.
     */
    static boolean writeConfigIfMissing(Path configPath, JSConfigModel model) {
        if (Files.notExists(configPath)) {
            try {
                Files.writeString(configPath, GSON.toJson(model));
                NekoJS.LOGGER.info("Generated default IDE workspace config at {}; later edits by the user are kept as-is",
                        configPath);
                return true;
            } catch (IOException e) {
                NekoJS.LOGGER.error("Failed to create config file: {}", configPath, e);
            }
        }
        return false;
    }

    /**
     * Build the jsconfig model for a given script type without writing to disk or invoking plugins.
     * Extracted for testability.
     */
    static JSConfigModel buildConfigForEnv(ScriptType scriptType, Path scriptDir, Path probeDir) {
        JSConfigModel model = new JSConfigModel();
        applyEngineJsxRuntime(model);

        String relativeProbePath = scriptDir.relativize(probeDir).toString().replace('\\', '/');
        // paths mapping values must be relative paths (./ or ../ prefix); TS 4.1+ resolves them against the jsconfig location
        if (!relativeProbePath.startsWith(".")) {
            relativeProbePath = "./" + relativeProbePath;
        }

        model.compilerOptions.typeRoots = List.of(
                relativeProbePath + "/@package",
                "../node_modules/@types"
        );
        model.compilerOptions.moduleResolution = "bundler";

        Map<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("java:*", List.of(relativeProbePath + "/@package/*"));

        String sideOnlyBase = relativeProbePath + "/@side-only/" + scriptType.name;
        paths.put("@side-only/" + scriptType.name, List.of(sideOnlyBase));
        paths.put("@side-only/" + scriptType.name + "/*", List.of(sideOnlyBase + "/*"));

        paths.put("@special", List.of(relativeProbePath + "/@special"));
        paths.put("@special/*", List.of(relativeProbePath + "/@special/*"));

        model.compilerOptions.paths = paths;

        List<String> includes = new ArrayList<>(model.include);
        includes.add(relativeProbePath + "/@package/**/*.d.ts");
        includes.add(relativeProbePath + "/@manual/**/*.d.ts");
        includes.add(sideOnlyBase + "/**/*.d.ts");
        includes.add(relativeProbePath + "/@nekojs/managed/" + scriptType.name + "/**/*.d.ts");
        model.include = includes;

        return model;
    }

    /**
     * Generates a jsconfig.json in the {@code .neko_probe/} root. The script-directory jsconfig does
     * not cover anything inside {@code .neko_probe/} (both are siblings), and the {@code .d.ts} files
     * probe generates are full of {@code import { $X } from "java:<pkg>"} (GraalJS interop module
     * specifiers), which TypeScript and IDEs do not understand for the {@code java:} scheme (TS2307).
     * A paths entry relative to this file maps {@code java:*} to {@code @package/*} (where the
     * {@code $X} aliases are actually declared), so the IDE can resolve imports inside
     * {@code .neko_probe}. One {@code java:*} rule is enough: the TS {@code *} wildcard matches a
     * whole path, including {@code /}.
     */
    private static void createProbeDirConfig() {
        Path probeDir = NekoJSPaths.get().probeDir();
        JSConfigModel model = new JSConfigModel();
        applyEngineJsxRuntime(model);
        model.compilerOptions.moduleResolution = "bundler";

        Map<String, List<String>> paths = new LinkedHashMap<>();
        // Without a baseUrl, paths mapping values must be relative paths (./ prefix) resolved against the jsconfig location
        paths.put("java:*", List.of("./@package/*"));
        // Inside .neko_probe, @side-only/<type> and @special reference each other, relative to the probe root
        for (ScriptType st : ScriptType.values()) {
            paths.put("@side-only/" + st.name, List.of("./@side-only/" + st.name));
            paths.put("@side-only/" + st.name + "/*", List.of("./@side-only/" + st.name + "/*"));
        }
        paths.put("@special", List.of("./@special"));
        paths.put("@special/*", List.of("./@special/*"));
        model.compilerOptions.paths = paths;

        // Index every .d.ts under .neko_probe
        List<String> includes = new ArrayList<>();
        includes.add("./**/*.d.ts");
        model.include = includes;

        Path configPath = probeDir.resolve("jsconfig.json");
        writeConfigIfMissing(configPath, model);
    }

    /**
     * Picks the jsconfig JSX runtime mode from the engine config, so IDE type checking matches the
     * runtime compile. When {@code jsxAutomaticRuntime=true} (in {@code nekojs/config/engine.toml}),
     * the config switches to the TS automatic runtime ({@code jsx: "react-jsx"} plus
     * {@code jsxImportSource: "nekojs"}, which TS resolves to
     * {@code import { jsx } from "nekojs/jsx-runtime"}, matching what {@code NekoJsxCompiler}
     * emits). The default is false, which keeps the classic {@code __nekoJsxFactory} global factory.
     */
    private static void applyEngineJsxRuntime(JSConfigModel model) {
        if (ClassFilter.INSTANCE.config().jsxAutomaticRuntime()) {
            model.useAutomaticJsxRuntime();
        }
    }

    private static void createSnippets() {
        com.tkisor.nekojs.api.catalog.TypeOutputLayout layout = NekoScriptCatalog.outputLayout();
        if (layout == null) {
            // No catalog provider is installed (plain common environment or tests): the EMPTY
            // provider returns null from outputLayout(), so there is no snippets output path;
            // skip instead of throwing an NPE.
            NekoJS.LOGGER.debug("No catalog output layout available, skipping snippets file creation");
            return;
        }
        Path snippetsPath = layout.snippetsPath();
        try {
            Files.createDirectories(snippetsPath.getParent());
            Files.writeString(snippetsPath, GSON.toJson(NekoSnippetJson.vscodeSnippets()));
        } catch (IOException e) {
            NekoJS.LOGGER.error("Failed to create snippets file: {}", snippetsPath, e);
        }
    }

    private WorkspaceGenerator() {}
}
