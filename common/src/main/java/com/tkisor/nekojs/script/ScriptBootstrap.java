package com.tkisor.nekojs.script;

import com.tkisor.nekojs.NekoJSCommon;

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
        for (ScriptType type : ScriptType.all()) {
            Path rootDir = type.path;
            Path srcDir = rootDir.resolve("src");
            Path mainFile = srcDir.resolve("main.js");

            try {
                Files.createDirectories(srcDir);

                if (Files.notExists(mainFile)) {
                    Files.writeString(mainFile, type.defaultMainScript(), StandardOpenOption.CREATE_NEW);
                    type.logger().info("已初始化环境入口: {}", mainFile);
                }
            } catch (IOException e) {
                NekoJSCommon.LOGGER.error("无法初始化环境目录 [{}]: {}", type.name(), e.getMessage());
            }
        }
    }
}