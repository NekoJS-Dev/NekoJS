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
 * Engine-side environment for {@link ScriptType}. Script directories, loggers and log file
 * resolution used to live inside {@code ScriptType} (the enum constant class captured
 * {@code NekoJSPaths} during initialization) and now all live in this engine class. The contract
 * enum keeps only names and predicate semantics, so the engine package can move to common-api
 * once its dependencies are gone.
 */
public final class ScriptTypeEnv {

    private ScriptTypeEnv() {
    }

    /** Returns the script directory for a script type ({@code nekojs/<type>_scripts}). */
    public static Path scriptsDir(ScriptType type) {
        var paths = NekoJSPaths.get();
        return switch (type) {
            case STARTUP -> paths.startupScripts();
            case SERVER -> paths.serverScripts();
            case CLIENT -> paths.clientScripts();
            case TEST -> paths.testScripts();
        };
    }

    /** Returns the logger for a script type. */
    public static Logger logger(ScriptType type) {
        return NekoJSLoggers.createLogger(type.name);
    }

    /** The log file for a script type (logs/nekojs/<type>.log); an older .txt file is migrated. */
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

    /** Creates a platform-independent id for a script file from its absolute path ({@code ScriptType#makeId}). */
    public static ScriptId makeId(ScriptType type, Path file) {
        Path relativePath = scriptsDir(type).relativize(file);
        String cleanPath = relativePath.toString().replace('\\', '/');
        return ScriptId.of("nekojs", type.name + "/" + cleanPath);
    }
}
