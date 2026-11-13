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
    /** Per-line buffer limit: output without newlines is forced into segments so memory cannot grow without bound. */
    private static final int MAX_LINE_BYTES = 64 * 1024;

    /**
     * Per-stream limit on total output lines. A console.log loop would otherwise produce
     * endless lines and fill the per-type log file and the disk. Past the limit further lines are
     * dropped with a single warning; the 64KB per-line limit still applies.
     */
    private static final int MAX_TOTAL_LINES = 100_000;

    private final Logger logger;
    private final boolean isErrorPipe;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int flushedLines;
    private boolean overflowWarned;
    /** Close idempotency flag: guarantees the buffer is flushed once, so a repeated close is a no-op. */
    private boolean closed;

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

    /** Appends in segments so the buffer never exceeds {@link #MAX_LINE_BYTES}. */
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

            // Line limit: once the emitted line count reaches the limit, later lines are dropped (with a
            // single warning) so a runaway script cannot fill the disk. Each line is on disk as soon as it is
            // flushed, so dropping new lines is what "drop the oldest" comes down to here.
            if (flushedLines < MAX_TOTAL_LINES) {
                emit(msg);
            } else if (!overflowWarned) {
                overflowWarned = true;
                logger.warn("[NEKO-2004] 脚本输出行数超过 {} 行上限，后续输出将被丢弃（防止日志无限增长） — script output line limit reached", MAX_TOTAL_LINES);
            }
            flushedLines++;
            buffer.reset();
        }
    }

    private void emit(String msg) {
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
    }

    /**
     * On close, flushes the last buffered line that has no trailing newline (including what a forced
     * 64KB split left behind) so no log line is lost.
     *
     * <p>Graal only detaches the out/err streams the user supplied when the Context closes; it does not
     * flush or close them, and only an engine-level close calls {@code close()}. Output at the end of a
     * script that does not end with a newline would otherwise be lost with the line buffer. Close
     * therefore means "flush, then close" and is idempotent (no-op on an empty buffer).
     *
     * <p>The closed flag makes repeated close flush only once. The destruction path (such as
     * ScriptManager.closeRuntimeResources) and the engine-level close can both reach the same instance,
     * and a repeated close must be a harmless no-op instead of flushing again: leftover bytes written
     * after close must not be flushed as if they were a complete line.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        flush();
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
