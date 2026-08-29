package com.tkisor.nekojs.script;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.ScriptId;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.log.NekoJSLoggers;
import com.tkisor.nekojs.platform.Platform;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link ScriptType} 的引擎侧环境（P1 契约抽薄，ADR-0007）：脚本目录、logger 与
 * 日志文件的解析原先内嵌在 {@code ScriptType}（枚举常量类初始化期捕获
 * {@code NekoJSPaths}），现全部迁到本引擎类——契约枚举只留名称与谓词语义，
 * 引擎包依赖归零后即可平移 common-api。
 */
public final class ScriptTypeEnv {

    private ScriptTypeEnv() {
    }

    /** 该脚本类型的脚本目录（{@code nekojs/<type>_scripts}）。 */
    public static Path scriptsDir(ScriptType type) {
        var paths = NekoJSPaths.get();
        return switch (type) {
            case STARTUP -> paths.startupScripts();
            case SERVER -> paths.serverScripts();
            case CLIENT -> paths.clientScripts();
            case TEST -> paths.testScripts();
        };
    }

    /** 该脚本类型的 logger。 */
    public static Logger logger(ScriptType type) {
        return NekoJSLoggers.createLogger(type.name);
    }

    /** 该脚本类型的日志文件（logs/nekojs/<type>.log，旧 .txt 自动迁移）。 */
    public static Path logFile(ScriptType type) {
        var dir = Platform.getGameDir().resolve("logs/nekojs");
        var file = dir.resolve(type.name + ".log");

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            if (!Files.exists(file)) {
                var oldFile = dir.resolve(type.name + ".txt");

                if (Files.exists(oldFile)) {
                    Files.move(oldFile, file);
                } else {
                    Files.createFile(file);
                }
            }
        } catch (Exception ex) {
            NekoJS.LOGGER.error("Failed to set up script log file", ex);
        }

        return file;
    }

    /** 按脚本文件绝对路径创建平台无关标识符（原 {@code ScriptType#makeId}）。 */
    public static ScriptId makeId(ScriptType type, Path file) {
        Path relativePath = scriptsDir(type).relativize(file);
        String cleanPath = relativePath.toString().replace('\\', '/');
        return ScriptId.of("nekojs", type.name + "/" + cleanPath);
    }
}
