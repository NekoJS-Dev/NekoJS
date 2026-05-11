package com.tkisor.nekojs.core.node;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public final class NekoNodeBuffer {
    private final byte[] bytes;

    public NekoNodeBuffer(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    public static NekoNodeBuffer fromString(String value, String encoding) {
        return new NekoNodeBuffer(value == null ? new byte[0] : value.getBytes(charset(encoding)));
    }

    public static NekoNodeBuffer fromBytes(byte[] bytes) {
        return new NekoNodeBuffer(bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length));
    }

    public static NekoNodeBuffer alloc(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Buffer size must be non-negative");
        }
        return new NekoNodeBuffer(new byte[size]);
    }

    public static NekoNodeBuffer concat(NekoNodeBuffer[] buffers) {
        if (buffers == null || buffers.length == 0) {
            return new NekoNodeBuffer(new byte[0]);
        }
        int size = 0;
        for (NekoNodeBuffer buffer : buffers) {
            if (buffer != null) {
                size += buffer.bytes.length;
            }
        }
        byte[] joined = new byte[size];
        int offset = 0;
        for (NekoNodeBuffer buffer : buffers) {
            if (buffer == null) continue;
            System.arraycopy(buffer.bytes, 0, joined, offset, buffer.bytes.length);
            offset += buffer.bytes.length;
        }
        return new NekoNodeBuffer(joined);
    }

    public static int byteLength(String value, String encoding) {
        return value == null ? 0 : value.getBytes(charset(encoding)).length;
    }

    public int length() {
        return bytes.length;
    }

    public int get(int index) {
        return bytes[index] & 0xFF;
    }

    public void set(int index, int value) {
        bytes[index] = (byte) value;
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public String toString(String encoding) {
        String normalized = normalizeEncoding(encoding);
        return switch (normalized) {
            case "base64" -> Base64.getEncoder().encodeToString(bytes);
            case "hex" -> toHex();
            default -> new String(bytes, charset(normalized));
        };
    }

    public String toString() {
        return toString("utf8");
    }

    private String toHex() {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xFF));
        }
        return builder.toString();
    }

    static Charset charset(String encoding) {
        return switch (normalizeEncoding(encoding)) {
            case "utf8", "utf-8" -> StandardCharsets.UTF_8;
            case "utf16le", "utf-16le", "ucs2", "ucs-2" -> StandardCharsets.UTF_16LE;
            case "ascii" -> StandardCharsets.US_ASCII;
            case "latin1", "binary" -> StandardCharsets.ISO_8859_1;
            default -> Charset.forName(encoding == null || encoding.isBlank() ? "UTF-8" : encoding);
        };
    }

    private static String normalizeEncoding(String encoding) {
        return encoding == null || encoding.isBlank() ? "utf8" : encoding.toLowerCase().replace("_", "-");
    }
}
