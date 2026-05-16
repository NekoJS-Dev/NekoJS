package com.tkisor.nekojs.core.log;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
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
            String msg = decode(buffer.toByteArray());

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

    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, Charset.defaultCharset());
        }
    }
}
