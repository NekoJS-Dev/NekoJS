package com.tkisor.nekojs.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.catalog.NekoScriptCatalog;
import com.tkisor.nekojs.api.catalog.NekoSnippetJson;
import com.tkisor.nekojs.bindings.event.ModifyWorkspaceConfigEvent;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.error.DefaultErrorTracker;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责生成工作区配置文件（README, jsconfig.json 等）
 */
public final class WorkspaceGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setupWorkspace() {
        createReadme();
        SandboxConfig config = ClassFilter.loadEngineConfig();
    }

    private static void createReadme() {
        if (Files.notExists(NekoJSPaths.get().readme())) {
            try {
                String content = """
                    === NekoJS Script Directory Guide ===
                    - startup_scripts: Loaded during game startup. Used for registering items and blocks. Changes require a full game restart.
                    - server_scripts: Executed when the world/server loads. Used for recipes and event handling. Can be reloaded with /reload.
                    - client_scripts: Runs on the client only. Used for GUI, key bindings, etc.
                    - test_scripts: Explicit smoke/regression scripts. Run with /nekojs test; they are not loaded by normal startup or reload.
                    - Note: Automatically generated type declaration files (.d.ts) are located in the %s folder. Do not modify them manually.
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
        createSnippets();
    }

    private static void createConfigForEnv(ScriptType scriptType, Path scriptDir) {
        JSConfigModel model = new JSConfigModel();

        // 计算从脚本目录到 .neko_probe 的相对路径
        Path probeDir = NekoJSPaths.get().probeDir();
        String relativeProbePath = scriptDir.relativize(probeDir).toString().replace('\\', '/');

        model.compilerOptions.typeRoots = List.of(
                relativeProbePath + "/@package",
                "../node_modules/@types"
        );
        model.compilerOptions.moduleResolution = "node";
        model.compilerOptions.baseUrl = ".";

        // paths 映射：@package、@side-only/{type}、@special
        Map<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("@package", List.of(relativeProbePath + "/@package"));
        paths.put("@package/*", List.of(relativeProbePath + "/@package/*"));

        String sideOnlyBase = relativeProbePath + "/@side-only/" + scriptType.name;
        paths.put("@side-only/" + scriptType.name, List.of(sideOnlyBase));
        paths.put("@side-only/" + scriptType.name + "/*", List.of(sideOnlyBase + "/*"));

        paths.put("@special", List.of(relativeProbePath + "/@special"));
        paths.put("@special/*", List.of(relativeProbePath + "/@special/*"));

        model.compilerOptions.paths = paths;

        // include 中追加 probe 生成的 .d.ts，让 VS Code 索引类型声明
        List<String> includes = new ArrayList<>(model.include);
        includes.add(relativeProbePath + "/@package/**/*.d.ts");
        includes.add(sideOnlyBase + "/**/*.d.ts");
        model.include = includes;

        ModifyWorkspaceConfigEvent event = new ModifyWorkspaceConfigEvent(model, scriptType.name);
        NeoForge.EVENT_BUS.post(event);

        Path configPath = scriptDir.resolve(event.getFileName());
        if (Files.notExists(configPath)) {
            try {
                Files.writeString(configPath, GSON.toJson(event.getModel()));
            } catch (IOException e) {
                NekoJS.LOGGER.error("Failed to create config file: {}", configPath, e);
            }
        }
    }

    private static void createSnippets() {
        Path snippetsPath = NekoScriptCatalog.outputLayout().snippetsPath();
        try {
            Files.createDirectories(snippetsPath.getParent());
            Files.writeString(snippetsPath, GSON.toJson(NekoSnippetJson.vscodeSnippets()));
        } catch (IOException e) {
            NekoJS.LOGGER.error("Failed to create snippets file: {}", snippetsPath, e);
        }
    }

    private WorkspaceGenerator() {}
}
