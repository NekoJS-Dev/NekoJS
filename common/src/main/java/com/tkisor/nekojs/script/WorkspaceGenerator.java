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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成工作区配置文件（README, jsconfig.json 等）。平台无关——jsconfig.json 模型写盘前，
 * 通过 {@link NekoJSPlugin#modifyWorkspaceConfig} 钩子让插件修改（每个 ScriptType 触发一次）。
 */
public final class WorkspaceGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setupWorkspace() {
        createReadme();
        ClassFilter.loadEngineConfig();
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
        createProbeDirConfig();
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

        // paths 映射：java:、@side-only/{type}、@special
        Map<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("java:*", List.of(relativeProbePath + "/@package/*"));

        String sideOnlyBase = relativeProbePath + "/@side-only/" + scriptType.name;
        paths.put("@side-only/" + scriptType.name, List.of(sideOnlyBase));
        paths.put("@side-only/" + scriptType.name + "/*", List.of(sideOnlyBase + "/*"));

        paths.put("@special", List.of(relativeProbePath + "/@special"));
        paths.put("@special/*", List.of(relativeProbePath + "/@special/*"));

        model.compilerOptions.paths = paths;

        // include 中追加 probe 生成的 .d.ts，让 VS Code 索引类型声明
        List<String> includes = new ArrayList<>(model.include);
        includes.add(relativeProbePath + "/@package/**/*.d.ts");
        includes.add(relativeProbePath + "/@manual/**/*.d.ts");
        includes.add(sideOnlyBase + "/**/*.d.ts");
        model.include = includes;

        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                plugin.modifyWorkspaceConfig(model, scriptType.name);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("Plugin {} failed modifyWorkspaceConfig for {}",
                        plugin.getClass(), scriptType.name, t);
            }
        }

        Path configPath = scriptDir.resolve("jsconfig.json");
        if (Files.notExists(configPath)) {
            try {
                Files.writeString(configPath, GSON.toJson(model));
            } catch (IOException e) {
                NekoJS.LOGGER.error("Failed to create config file: {}", configPath, e);
            }
        }
    }

    /**
     * 在 {@code .neko_probe/} 根目录生成 jsconfig.json。脚本目录的 jsconfig 覆盖不到
     * {@code .neko_probe/} 内部（两者同级），而 probe 生成的 {@code .d.ts} 内部大量用
     * {@code import { $X } from "java:<pkg>"}（GraalJS 互操作模块说明符），TypeScript/IDE 不认
     * {@code java:} scheme（报 TS2307）。这里用相对自身的 paths 把 {@code java:*} 映射到
     * {@code @package/*}（{@code $X} alias 实际定义处），让 IDE 能解析 {@code .neko_probe} 内部 import。
     * 单条 {@code java:*} 规则即可：TS 的 {@code *} 通配匹配含 {@code /} 的整段路径。
     */
    private static void createProbeDirConfig() {
        Path probeDir = NekoJSPaths.get().probeDir();
        JSConfigModel model = new JSConfigModel();
        model.compilerOptions.moduleResolution = "node";
        model.compilerOptions.baseUrl = ".";

        Map<String, List<String>> paths = new LinkedHashMap<>();
        paths.put("java:*", List.of("@package/*"));
        // .neko_probe 内部 @side-only/<type> 与 @special 互引（相对自身）
        for (ScriptType st : ScriptType.values()) {
            paths.put("@side-only/" + st.name, List.of("@side-only/" + st.name));
            paths.put("@side-only/" + st.name + "/*", List.of("@side-only/" + st.name + "/*"));
        }
        paths.put("@special", List.of("@special"));
        paths.put("@special/*", List.of("@special/*"));
        model.compilerOptions.paths = paths;

        // 索引 .neko_probe 下所有 .d.ts
        List<String> includes = new ArrayList<>();
        includes.add("./**/*.d.ts");
        model.include = includes;

        Path configPath = probeDir.resolve("jsconfig.json");
        if (Files.notExists(configPath)) {
            try {
                Files.writeString(configPath, GSON.toJson(model));
            } catch (IOException e) {
                NekoJS.LOGGER.error("Failed to create probe dir config: {}", configPath, e);
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
