package com.tkisor.nekojs.script;

import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.log.NekoJSLoggers;
import com.tkisor.nekojs.platform.Platform;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public enum ScriptType {
    COMMON("common", "NekoJS Common", null),
    STARTUP("startup", "NekoJS Startup", NekoJSPaths.STARTUP_SCRIPTS),
    SERVER("server", "NekoJS Server", NekoJSPaths.SERVER_SCRIPTS),
    CLIENT("client", "NekoJS Client", NekoJSPaths.CLIENT_SCRIPTS);

    private static class LoggerHolder {
        private static final ScriptTypedValue<Logger> LOGGERS =
                ScriptTypedValue.of(type -> NekoJSLoggers.createLogger(type.name));
    }

    public final String name;
    public final String cname;
    public final Path path;

    ScriptType(String name, String cname, Path path) {
        this.name = name;
        this.cname = cname;
        this.path = path;
    }

    public Logger logger() {
        return LoggerHolder.LOGGERS.at(this);
    }

    public Path getLogFile() {
        var dir = Platform.getGameDir().resolve("logs/nekojs");
        var file = dir.resolve(name + ".log");

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            if (!Files.exists(file)) {
                var oldFile = dir.resolve(name + ".txt");

                if (Files.exists(oldFile)) {
                    Files.move(oldFile, file);
                } else {
                    Files.createFile(file);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return file;
    }

    /**
     * 根据脚本文件路径创建平台无关的标识符
     * @param file 脚本文件绝对路径
     * @return 对应的脚本 ID
     */
    public ScriptId makeId(Path file) {
        Path relativePath = this.path.relativize(file);
        String cleanPath = relativePath.toString().replace('\\', '/');
        return ScriptId.of("nekojs", this.name + "/" + cleanPath);
    }

    public String defaultMainScript() {
        return """
                // %s example script
                console.info('Hello, World! (Loaded %s example script)');
                """.formatted(name, name);
    }

    public boolean isCommon() {
        return this == COMMON;
    }

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }

    public boolean isStartup() {
        return this == STARTUP;
    }

    private static final List<ScriptType> EXECUTABLE_TYPES = List.of(STARTUP, SERVER, CLIENT);

    /**
     * 获取所有需要被动态加载执行的脚本类型（已排除 COMMON）
     */
    public static List<ScriptType> all() {
        return EXECUTABLE_TYPES;
    }
}