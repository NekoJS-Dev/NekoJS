package com.tkisor.nekojs.core.log;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.logging.log4j.Level.INFO;

public final class NekoJSLoggers {

    private static final ConcurrentHashMap<String, Logger> CACHE = new ConcurrentHashMap<>();

    public static Logger get(String name) {
        return CACHE.computeIfAbsent(name, NekoJSLoggers::createLogger);
    }

    public static Logger createLogger(String name) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();

        String fileAppenderName = "NekoJS-File-" + name;
        String asyncAppenderName = "NekoJS-Async-" + name;
        String loggerName = "nekojs." + name;

        if (cfg.getAppenders().containsKey(asyncAppenderName)) {
            return LoggerFactory.getLogger(loggerName);
        }

        Path nekoLogDir = NekoJSPaths.GAME_DIR.resolve("logs").resolve("nekojs");
        Path file = nekoLogDir.resolve(name + ".log");
        Path oldLogDir = nekoLogDir.resolve("old");

        try {
            Files.createDirectories(nekoLogDir);

            if (Files.exists(file)) {
                Files.createDirectories(oldLogDir);

                Path backupFile = oldLogDir.resolve(name + ".log");

                Files.move(file, backupFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("[NekoJS] 无法为脚本创建日志文件或备份: " + file);
            NekoJS.LOGGER.error("[NekoJS] 无法为脚本创建日志文件或备份: {}", file, e);
        }

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss}] [%level] %msg%n")
                .withConfiguration(cfg)
                .build();

        FileAppender fileAppender = FileAppender.newBuilder()
                .withFileName(file.toString())
                .setName(fileAppenderName)
                .setLayout(layout)
                .withAppend(true)
                .setConfiguration(cfg)
                .build();

        fileAppender.start();
        cfg.addAppender(fileAppender);

        AppenderRef appenderRef = AppenderRef.createAppenderRef(fileAppenderName, null, null);
        AsyncAppender asyncAppender = AsyncAppender.newBuilder()
                .setName(asyncAppenderName)
                .setConfiguration(cfg)
                .setAppenderRefs(new AppenderRef[]{appenderRef})
                .setBufferSize(2048)
                .build();

        asyncAppender.start();
        cfg.addAppender(asyncAppender);

        LoggerConfig loggerConfig = new LoggerConfig(loggerName, INFO, true);
        loggerConfig.addAppender(asyncAppender, null, null);

        cfg.addLogger(loggerName, loggerConfig);
        ctx.updateLoggers();

        return LoggerFactory.getLogger(loggerName);
    }

    private NekoJSLoggers() {}
}