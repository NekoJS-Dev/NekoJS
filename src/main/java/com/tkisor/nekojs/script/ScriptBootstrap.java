package com.tkisor.nekojs.script;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ScriptBootstrap {
    private ScriptBootstrap() {}

    /**
     * 生成默认的脚本文件
     */
    public static void generateDefaultScripts() {
        for (ScriptType type : ScriptType.values()) {
            Path dir = type.path;
            Path main = dir.resolve("main.js");

            try {
                if (Files.notExists(dir)) {
                    Files.createDirectories(dir);
                }

                if (Files.notExists(main)) {
                    Files.writeString(main, type.defaultMainScript());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
