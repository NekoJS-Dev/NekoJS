package com.tkisor.nekojs.core.log;

import com.tkisor.nekojs.NekoJSCommon;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.logging.log4j.Level.INFO;

/**
 * NekoJS 日志管理器
 * 包含日志折叠机制，防止脚本死循环或高频触发导致的日志刷屏崩溃。
 */
public final class NekoJSLoggers {

    private static final ConcurrentHashMap<String, Logger> CACHE = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NekoJS-Log-Flusher");
        t.setDaemon(true);
        return t;
    });

    private static final List<CollapsingAppender> APPENDERS = new CopyOnWriteArrayList<>();

    static {
        // 每 1000 毫秒批量刷写
        FLUSHER.scheduleAtFixedRate(() -> {
            for (CollapsingAppender appender : APPENDERS) {
                appender.flush();
            }
        }, 1000, 1000, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (CollapsingAppender appender : APPENDERS) {
                appender.flush();
            }
            FLUSHER.shutdown();
        }));
    }

    public static Logger get(String name) {
        return CACHE.computeIfAbsent(name, NekoJSLoggers::createLogger);
    }

    public static Logger createLogger(String name) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();

        String fileAppenderName = "NekoJS-File-" + name;
        String asyncAppenderName = "NekoJS-Async-" + name;
        String collapsingAppenderName = "NekoJS-Collapse-" + name;
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
            NekoJSCommon.LOGGER.error("[NekoJS] Failed to create or backup log file for script: {}", file, e);
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

        CollapsingAppender collapsingAppender = new CollapsingAppender(collapsingAppenderName, asyncAppender);
        collapsingAppender.start();
        cfg.addAppender(collapsingAppender);
        APPENDERS.add(collapsingAppender);

        LoggerConfig loggerConfig = new LoggerConfig(loggerName, INFO, false);

        loggerConfig.addAppender(collapsingAppender, null, null);

        cfg.addLogger(loggerName, loggerConfig);
        ctx.updateLoggers();

        return LoggerFactory.getLogger(loggerName);
    }

    private NekoJSLoggers() {}

    /**
     * 自定义的 Log4j2 拦截追加器，专门负责识别相同特征的日志并将其聚合。
     */
    public static class CollapsingAppender extends AbstractAppender {
        private final Appender delegate;
        private LogEvent lastEvent = null;
        private String lastCheckStr = null;
        private int duplicateCount = 0;

        public CollapsingAppender(String name, Appender delegate) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
            this.delegate = delegate;
        }

        @Override
        public synchronized void append(LogEvent event) {
            String currentMsg = event.getMessage().getFormattedMessage();
            Throwable t = event.getThrown();

            String checkStr = currentMsg + (t == null ? "" : t.getClass().getName());

            if (lastCheckStr != null && lastCheckStr.equals(checkStr)) {
                duplicateCount++;
            } else {
                flush();
                lastEvent = event.toImmutable();
                lastCheckStr = checkStr;
                duplicateCount = 1;
            }
        }

        /**
         * 将拦截下来的日志下发给底层的 AsyncAppender 存文件，并手动完美同步到主控制台
         */
        public synchronized void flush() {
            if (lastEvent != null) {
                String newMsg = lastEvent.getMessage().getFormattedMessage();
                if (duplicateCount > 1) {
                    newMsg += " (x" + duplicateCount + ")";
                }

                LogEvent modified = new Log4jLogEvent.Builder(lastEvent)
                        .setMessage(new SimpleMessage(newMsg))
                        .build();
                delegate.append(modified);

                String envName = lastEvent.getLoggerName().replace("nekojs.", "");
                String consoleMsg = "[" + envName + "] " + newMsg;

                Level level = lastEvent.getLevel();
                Throwable t = lastEvent.getThrown();

                if (level == Level.ERROR || level == Level.FATAL) {
                    NekoJSCommon.LOGGER.error(consoleMsg, t);
                } else if (level == Level.WARN) {
                    NekoJSCommon.LOGGER.warn(consoleMsg, t);
                } else if (level == Level.DEBUG) {
                    NekoJSCommon.LOGGER.debug(consoleMsg, t);
                } else {
                    NekoJSCommon.LOGGER.info(consoleMsg, t);
                }

                lastEvent = null;
                lastCheckStr = null;
                duplicateCount = 0;
            }
        }
    }
}