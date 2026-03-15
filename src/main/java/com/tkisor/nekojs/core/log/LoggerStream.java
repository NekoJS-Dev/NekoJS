package com.tkisor.nekojs.core.log;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LoggerStream extends OutputStream {
    private final Logger logger;
    private final boolean isErrorPipe;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private static final String WARN_TAG = "[NekoJS_WARN] ";
    private static final String DEBUG_TAG = "[NekoJS_DEBUG] ";

    public LoggerStream(Logger logger, boolean isErrorPipe) {
        this.logger = logger;
        this.isErrorPipe = isErrorPipe;
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            flush();
        } else if (b != '\r') {
            buffer.write(b);
        }
    }

    public void flush() {
        if (buffer.size() > 0) {
            String msg = buffer.toString(StandardCharsets.UTF_8);

            if (isErrorPipe) {
                if (msg.startsWith(WARN_TAG)) {
                    logger.warn(msg.substring(WARN_TAG.length()));
                } else {
                    logger.error(msg);
                }
            } else {
                if (msg.startsWith(DEBUG_TAG)) {
                    logger.debug(msg.substring(DEBUG_TAG.length()));
                } else {
                    logger.info(msg);
                }
            }

            buffer.reset();
        }
    }
}