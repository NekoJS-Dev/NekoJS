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
    /** 单行缓冲上限：脚本输出巨量无换行内容时强制分段落盘，防止内存无限膨胀。 */
    private static final int MAX_LINE_BYTES = 64 * 1024;

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
            append(new byte[]{(byte) b}, 0, 1);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (b == null) throw new NullPointerException("b");
        if (off < 0 || len < 0 || off + len > b.length) throw new IndexOutOfBoundsException();
        int segmentStart = off;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == '\n') {
                append(b, segmentStart, i);
                flush();
                segmentStart = i + 1;
            } else if (c == '\r') {
                append(b, segmentStart, i);
                segmentStart = i + 1;
            }
        }
        append(b, segmentStart, off + len);
    }

    /** 分段追加，保证缓冲不超过 {@link #MAX_LINE_BYTES}。 */
    private void append(byte[] b, int from, int to) {
        while (from < to) {
            int chunk = Math.min(MAX_LINE_BYTES - buffer.size(), to - from);
            if (chunk <= 0) {
                flush();
                continue;
            }
            buffer.write(b, from, chunk);
            from += chunk;
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
