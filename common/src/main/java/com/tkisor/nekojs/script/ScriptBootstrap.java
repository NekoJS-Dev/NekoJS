package com.tkisor.nekojs.script;

import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.NekoJS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ScriptBootstrap {

    private ScriptBootstrap() {}

    /**
     * 生成工程化的默认脚本结构
     * 路径：nekojs/[type]_scripts/src/main.js
     */
    public static void generateDefaultScripts() {
        for (ScriptType type : ScriptType.autoLoadTypes()) {
            Path rootDir = ScriptTypeEnv.scriptsDir(type);
            Path srcDir = rootDir.resolve("src");
            Path mainFile = srcDir.resolve("main.js");

            try {
                Files.createDirectories(srcDir);

                if (Files.notExists(mainFile)) {
                    Files.writeString(mainFile, type.defaultMainScript(), StandardOpenOption.CREATE_NEW);
                    com.tkisor.nekojs.script.ScriptTypeEnv.logger(type).info("已初始化环境入口: {} — workspace entry point created", mainFile);
                }
            } catch (IOException e) {
                NekoJS.LOGGER.error("[NEKO-1002] 无法初始化环境目录 [{}]: {} — failed to create workspace directory", type.name(), e.getMessage());
            }
        }
    }
}