package com.tkisor.nekojs.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.bindings.event.ModifyWorkspaceConfigEvent;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责生成工作区配置文件（README, jsconfig.json 等）
 */
public final class WorkspaceGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setupWorkspace() {
        createReadme();
        ClassFilter.loadEngineConfig();
    }

    public static void createReadme() {
        if (Files.notExists(NekoJSPaths.README)) {
            try {
                String content = """
                    === NekoJS Script Directory Guide ===
                    - startup_scripts: Loaded during game startup. Used for registering items and blocks. Changes require a full game restart.
                    - server_scripts: Executed when the world/server loads. Used for recipes and event handling. Can be reloaded with /reload.
                    - client_scripts: Runs on the client only. Used for GUI, key bindings, etc.
                    - Note: Automatically generated type declaration files (.d.ts) are located in the %s folder. Do not modify them manually.
                    """.formatted(NekoJSPaths.PROBE_DIR.getFileName()).trim();
                Files.writeString(NekoJSPaths.README, content);
            } catch (IOException ex) {
                NekoJSCommon.LOGGER.error("[NekoJS] Failed to create README.txt", ex);
            }
        }
    }

    public static void createWorkspaceConfigs() {
        createConfigForEnv("server", NekoJSPaths.SERVER_SCRIPTS);
        createConfigForEnv("client", NekoJSPaths.CLIENT_SCRIPTS);
        createConfigForEnv("startup", NekoJSPaths.STARTUP_SCRIPTS);
    }

    private static void createConfigForEnv(String envName, Path scriptDir) {
        JSConfigModel model = new JSConfigModel();

        String relativeProbePath = "../../" + NekoJSPaths.PROBE_DIR.getFileName() + "/" + envName + "/probe-types";

        model.compilerOptions.typeRoots = List.of(
                relativeProbePath,
                "../node_modules/@types"
        );
        model.compilerOptions.moduleResolution = "node";
        model.compilerOptions.baseUrl = relativeProbePath;

        ModifyWorkspaceConfigEvent event = new ModifyWorkspaceConfigEvent(model, envName);
        NeoForge.EVENT_BUS.post(event);

        Path configPath = scriptDir.resolve(event.getFileName());
        if (Files.notExists(configPath)) {
            try {
                Files.writeString(configPath, GSON.toJson(event.getModel()));
            } catch (IOException e) {
                NekoJSCommon.LOGGER.error("[NekoJS] Failed to create config file: {}", configPath, e);
            }
        }
    }

    private WorkspaceGenerator() {}
}