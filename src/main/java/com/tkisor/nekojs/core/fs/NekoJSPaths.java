package com.tkisor.nekojs.core.fs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.ModifyWorkspaceConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NekoJSPaths {
    // ✨ 创建一个带缩进的 Gson 实例，用来把实体类变成漂亮的 JSON
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /* ================= Base ================= */
    public static final Path GAME_DIR = FMLPaths.GAMEDIR.get().normalize().toAbsolutePath();
    public static final Path ROOT = GAME_DIR.resolve("nekojs");

    /* ================= Script Folders ================= */
    public static final Path COMMON_SCRIPTS = ROOT.resolve("common_scripts");
    public static final Path STARTUP_SCRIPTS = ROOT.resolve("startup_scripts");
    public static final Path SERVER_SCRIPTS = ROOT.resolve("server_scripts");
    public static final Path CLIENT_SCRIPTS = ROOT.resolve("client_scripts");

    // ✨ 新增 probe 文件夹，用于未来存放类型提示文件 (.d.ts)
    public static final Path PROBE_DIR = ROOT.resolve("probe");
    public static final Path NODE_MODULES = ROOT.resolve("node_modules");

    /* ================= Config ================= */
    public static final Path CONFIG = ROOT.resolve("config");
    public static final Path COMMON_CONFIG = CONFIG.resolve("common.json");
    public static final Path CLIENT_CONFIG = CONFIG.resolve("client.json");
    public static final Path DEV_CONFIG = CONFIG.resolve("dev.json");

    /* ================= Docs & DX ================= */
    public static final Path README = ROOT.resolve("README.txt");
    public static final Path JS_CONFIG = ROOT.resolve("jsconfig.json"); // ✨ 新增 jsconfig 路径

    /* ================= Initialization ================= */
    public static void initFoldersOnly() {
        ensureDir(ROOT);
        ensureDir(COMMON_SCRIPTS);
        ensureDir(STARTUP_SCRIPTS);
        ensureDir(SERVER_SCRIPTS);
        ensureDir(CLIENT_SCRIPTS);
        ensureDir(CONFIG);
        ensureDir(PROBE_DIR);
        ensureDir(NODE_MODULES);

        createReadme();
    }

    /* ================= Utilities ================= */

    private static void ensureDir(Path dir) {
        try {
            if (Files.notExists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            NekoJS.LOGGER.error("[NekoJS] 无法创建目录: {}", dir, e);
        }
    }

    public static Path verifyInsideGameDir(Path path) throws IOException {
        Path normalized = path.normalize().toAbsolutePath();

        if (!normalized.startsWith(GAME_DIR)) {
            throw new IOException("Access outside game directory is forbidden");
        }

        if (Files.exists(normalized)) {
            Path realPath = normalized.toRealPath();
            if (!realPath.startsWith(GAME_DIR)) {
                throw new IOException("Symlink escape detected!");
            }
        }

        return normalized;
    }

    private static void createReadme() {
        if (Files.notExists(README)) {
            try {
                Files.writeString(README, """
                      === NekoJS 脚本目录说明 ===
                      - startup_scripts: 游戏启动时加载，用于注册物品、方块。修改后需要重启游戏。
                      - server_scripts: 存档/服务器加载时运行，用于配方、事件监听。可使用 /reload 重载。
                      - client_scripts: 仅在客户端运行，用于 GUI、按键绑定等。
                      - probe: 存放 NekoJS 自动生成的类型声明文件 (.d.ts)，请勿手动修改。
                      """.trim());
            } catch (Exception ex) {
                NekoJS.LOGGER.error("[NekoJS] 无法创建 README.txt 文件", ex);
            }
        }
    }

    public static void createWorkspaceConfig() {
        JSConfigModel model = new JSConfigModel();

        ModifyWorkspaceConfigEvent event = new ModifyWorkspaceConfigEvent(model);
        NeoForge.EVENT_BUS.post(event);

        Path configPath = ROOT.resolve(event.getFileName());

        if (Files.notExists(configPath)) {
            try {
                String jsonContent = GSON.toJson(event.getModel());
                Files.writeString(configPath, jsonContent);
                NekoJS.LOGGER.info("[NekoJS] 成功生成工作区配置文件: {}", event.getFileName());
            } catch (Exception e) {
                NekoJS.LOGGER.error("[NekoJS] 无法创建配置文件: {}", event.getFileName(), e);
            }
        }
    }

    private NekoJSPaths() {}
}