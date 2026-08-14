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

    /**
     * 单个流累计输出行数上限：console.log 死循环等场景会产生无限行日志，撑爆 per-type
     * 日志文件与磁盘。超过上限后丢弃后续行并一次性告警；64KB 单行上限行为保持不变。
     */
    private static final int MAX_TOTAL_LINES = 100_000;

    private final Logger logger;
    private final boolean isErrorPipe;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int flushedLines;
    private boolean overflowWarned;
    /** close 幂等标记：保证「冲刷一次」语义，重复 close 为 no-op。 */
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

            // 行数上限：已输出行数达到上限后丢弃后续行（仅一次性告警），防止脚本日志死循环
            // 无限写盘。每行在 flush 时即落盘，故「丢弃最旧」退化为实现为「丢弃后续新行」。
            if (flushedLines < MAX_TOTAL_LINES) {
                emit(msg);
            } else if (!overflowWarned) {
                overflowWarned = true;
                logger.warn("脚本输出行数超过 {} 行上限，后续输出将被丢弃（防止日志无限增长）", MAX_TOTAL_LINES);
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
     * 关闭时冲刷缓冲中最后一行未换行内容（含 64KB 强制分段残留），避免丢日志。
     *
     * <p>背景：Graal 在 Context 关闭时只 detach 用户提供的 out/err 流、不会 flush/close
     * （engine 级 close 才会对其调用 {@code close()}），脚本末尾未以换行结束的输出
     * 会随行缓冲一起丢失。这里把 close 语义定义为「冲刷后关闭」，幂等（缓冲空时无操作）。
     *
     * <p>closed 标记保证多次 close 只冲刷一次：销毁路径（ScriptManager.closeRuntimeResources
     * 等）与 engine 级 close 都可能触达同一个实例，重复 close 必须是无害 no-op 而不是再次
     * 冲刷（防止 close 之后偶发写入的残余字节被误当作完整行落盘）。
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
