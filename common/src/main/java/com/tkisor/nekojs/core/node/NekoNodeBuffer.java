package com.tkisor.nekojs.core.node;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public final class NekoNodeBuffer {
    /** Maximum number of bytes a single buffer may allocate, guarding against script-triggered OOM. */
    public static final int MAX_ALLOC_BYTES = 256 * 1024 * 1024;

    private final byte[] bytes;
    private final int offset;
    private final int length;

    public NekoNodeBuffer(byte[] bytes) {
        byte[] data = bytes == null ? new byte[0] : bytes;
        checkAllocSize(data.length);
        this.bytes = data;
        this.offset = 0;
        this.length = data.length;
    }

    private NekoNodeBuffer(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || length > bytes.length - offset) {
            throw new IndexOutOfBoundsException(
                    "offset " + offset + ", length " + length + " outside backing array length " + bytes.length);
        }
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
    }

    static void checkAllocSize(long size) {
        if (size > MAX_ALLOC_BYTES) {
            throw new IllegalArgumentException(
                    "Buffer size " + size + " exceeds maximum of " + MAX_ALLOC_BYTES + " bytes");
        }
    }

    public static NekoNodeBuffer fromString(String value, String encoding) {
        if (value == null) {
            return new NekoNodeBuffer(new byte[0]);
        }
        Charset charset = charset(encoding);
        checkAllocSize((long) Math.ceil(value.length() * (double) charset.newEncoder().maxBytesPerChar()));
        return new NekoNodeBuffer(value.getBytes(charset));
    }

    public static NekoNodeBuffer fromBytes(byte[] bytes) {
        if (bytes == null) {
            return new NekoNodeBuffer(new byte[0]);
        }
        checkAllocSize(bytes.length);
        return new NekoNodeBuffer(Arrays.copyOf(bytes, bytes.length));
    }

    public static NekoNodeBuffer alloc(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Buffer size must be non-negative");
        }
        checkAllocSize(size);
        return new NekoNodeBuffer(new byte[size]);
    }

    public static NekoNodeBuffer concat(NekoNodeBuffer[] buffers) {
        if (buffers == null || buffers.length == 0) {
            return new NekoNodeBuffer(new byte[0]);
        }
        long size = 0;
        for (NekoNodeBuffer buffer : buffers) {
            if (buffer != null) {
                size += buffer.length;
            }
        }
        checkAllocSize(size);
        byte[] joined = new byte[(int) size];
        int offset = 0;
        for (NekoNodeBuffer buffer : buffers) {
            if (buffer == null) continue;
            System.arraycopy(buffer.bytes, buffer.offset, joined, offset, buffer.length);
            offset += buffer.length;
        }
        return new NekoNodeBuffer(joined);
    }

    public static int byteLength(String value, String encoding) {
        if (value == null) {
            return 0;
        }
        Charset charset = charset(encoding);
        checkAllocSize((long) Math.ceil(value.length() * (double) charset.newEncoder().maxBytesPerChar()));
        return value.getBytes(charset).length;
    }

    public int length() {
        return length;
    }

    public int get(int index) {
        return bytes[viewIndex(index)] & 0xFF;
    }

    public void set(int index, int value) {
        bytes[viewIndex(index)] = (byte) value;
    }

    public byte[] bytes() {
        return Arrays.copyOfRange(bytes, offset, offset + length);
    }

    public String toString(String encoding) {
        String normalized = normalizeEncoding(encoding);
        return switch (normalized) {
            case "base64" -> Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, offset, offset + length));
            case "hex" -> toHex();
            default -> new String(bytes, offset, length, charset(normalized));
        };
    }

    public String toString() {
        return toString("utf8");
    }

    public NekoNodeBuffer slice(int start, int end) {
        int len = length;
        int s = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
        int e = end < 0 ? Math.max(len + end, 0) : Math.min(end, len);
        int newLen = Math.max(e - s, 0);
        return new NekoNodeBuffer(bytes, offset + s, newLen);
    }

    public NekoNodeBuffer fill(int value, int start, int end) {
        int len = length;
        if (start < 0) {
            throw new IllegalArgumentException("fill start must be >= 0: " + start);
        }
        if (end < 0) {
            throw new IllegalArgumentException("fill end must be >= 0: " + end);
        }
        if (end > len) {
            throw new IllegalArgumentException("fill end must be <= length " + len + ": " + end);
        }
        if (start >= end) {
            return this;
        }
        Arrays.fill(bytes, offset + start, offset + end, (byte) value);
        return this;
    }

    public int indexOf(NekoNodeBuffer needle, int fromIndex) {
        if (needle == null) {
            return -1;
        }
        if (needle.length == 0) {
            return Math.min(Math.max(fromIndex, 0), length);
        }
        byte[] search = needle.bytes;
        int searchOffset = needle.offset;
        int searchLen = needle.length;
        int start = Math.max(fromIndex, 0);
        outer:
        for (int i = start; i <= length - searchLen; i++) {
            for (int j = 0; j < searchLen; j++) {
                if (bytes[offset + i + j] != search[searchOffset + j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    public boolean includes(NekoNodeBuffer needle) {
        return indexOf(needle, 0) >= 0;
    }

    public int copy(NekoNodeBuffer target, int targetStart, int sourceStart, int sourceEnd) {
        if (targetStart < 0) {
            throw new IllegalArgumentException("targetStart must be >= 0: " + targetStart);
        }
        if (sourceStart < 0) {
            throw new IllegalArgumentException("sourceStart must be >= 0: " + sourceStart);
        }
        int len = length;
        if (sourceStart > len) {
            throw new IllegalArgumentException(
                    "sourceStart must be <= source length " + len + ": " + sourceStart);
        }
        if (sourceEnd < 0) {
            throw new IllegalArgumentException("sourceEnd must be >= 0: " + sourceEnd);
        }
        int srcS = sourceStart;
        int srcE = Math.min(sourceEnd, len);
        int count = Math.max(srcE - srcS, 0);
        if (target == null) {
            return count;
        }
        int targetLen = target.length;
        if (targetStart >= targetLen || sourceStart >= sourceEnd) {
            return 0;
        }
        count = Math.min(count, targetLen - targetStart);
        if (count > 0) {
            System.arraycopy(bytes, offset + srcS, target.bytes, target.offset + targetStart, count);
        }
        return count;
    }

    public boolean equals(NekoNodeBuffer other) {
        if (other == null) return false;
        return Arrays.equals(bytes, offset, offset + length, other.bytes, other.offset, other.offset + other.length);
    }

    public int compare(NekoNodeBuffer other) {
        if (other == null) return 1;
        return Arrays.compare(bytes, offset, offset + length, other.bytes, other.offset, other.offset + other.length);
    }

    // Multi-byte read helpers
    public int readUInt8(int offset) { return bytes[viewIndex(offset)] & 0xFF; }
    public int readInt8(int offset) { return bytes[viewIndex(offset)]; }

    public int readUInt16LE(int offset) {
        int p = viewIndex(offset, 2);
        return (bytes[p] & 0xFF) | ((bytes[p + 1] & 0xFF) << 8);
    }

    public int readUInt16BE(int offset) {
        int p = viewIndex(offset, 2);
        return ((bytes[p] & 0xFF) << 8) | (bytes[p + 1] & 0xFF);
    }

    public int readInt16LE(int offset) { return (short) readUInt16LE(offset); }
    public int readInt16BE(int offset) { return (short) readUInt16BE(offset); }

    public long readUInt32LE(int offset) {
        int p = viewIndex(offset, 4);
        return (bytes[p] & 0xFFL) | ((bytes[p + 1] & 0xFFL) << 8) | ((bytes[p + 2] & 0xFFL) << 16) | ((bytes[p + 3] & 0xFFL) << 24);
    }

    public long readUInt32BE(int offset) {
        int p = viewIndex(offset, 4);
        return ((bytes[p] & 0xFFL) << 24) | ((bytes[p + 1] & 0xFFL) << 16) | ((bytes[p + 2] & 0xFFL) << 8) | (bytes[p + 3] & 0xFFL);
    }

    public int readInt32LE(int offset) { return (int) readUInt32LE(offset); }
    public int readInt32BE(int offset) { return (int) readUInt32BE(offset); }

    public double readDoubleLE(int offset) { return Double.longBitsToDouble(readInt64LE(offset)); }
    public double readDoubleBE(int offset) { return Double.longBitsToDouble(readInt64BE(offset)); }
    public float readFloatLE(int offset) { return Float.intBitsToFloat(readInt32LE(offset)); }
    public float readFloatBE(int offset) { return Float.intBitsToFloat(readInt32BE(offset)); }

    private long readInt64LE(int offset) {
        int p = viewIndex(offset, 8);
        return (bytes[p] & 0xFFL) | ((bytes[p + 1] & 0xFFL) << 8) | ((bytes[p + 2] & 0xFFL) << 16) | ((bytes[p + 3] & 0xFFL) << 24)
                | ((bytes[p + 4] & 0xFFL) << 32) | ((bytes[p + 5] & 0xFFL) << 40) | ((bytes[p + 6] & 0xFFL) << 48) | ((bytes[p + 7] & 0xFFL) << 56);
    }

    private long readInt64BE(int offset) {
        int p = viewIndex(offset, 8);
        return ((bytes[p] & 0xFFL) << 56) | ((bytes[p + 1] & 0xFFL) << 48) | ((bytes[p + 2] & 0xFFL) << 40) | ((bytes[p + 3] & 0xFFL) << 32)
                | ((bytes[p + 4] & 0xFFL) << 24) | ((bytes[p + 5] & 0xFFL) << 16) | ((bytes[p + 6] & 0xFFL) << 8) | (bytes[p + 7] & 0xFFL);
    }

    // Multi-byte write helpers
    public void writeUInt8(int offset, int value) { bytes[viewIndex(offset)] = (byte) value; }
    public void writeInt8(int offset, int value) { bytes[viewIndex(offset)] = (byte) value; }

    public void writeUInt16LE(int offset, int value) {
        int p = viewIndex(offset, 2);
        bytes[p] = (byte) value;
        bytes[p + 1] = (byte) (value >>> 8);
    }

    public void writeUInt16BE(int offset, int value) {
        int p = viewIndex(offset, 2);
        bytes[p] = (byte) (value >>> 8);
        bytes[p + 1] = (byte) value;
    }

    public void writeInt16LE(int offset, int value) { writeUInt16LE(offset, value); }
    public void writeInt16BE(int offset, int value) { writeUInt16BE(offset, value); }

    public void writeUInt32LE(int offset, long value) {
        int p = viewIndex(offset, 4);
        for (int i = 0; i < 4; i++) bytes[p + i] = (byte) (value >>> (i * 8));
    }

    public void writeUInt32BE(int offset, long value) {
        int p = viewIndex(offset, 4);
        for (int i = 0; i < 4; i++) bytes[p + i] = (byte) (value >>> ((3 - i) * 8));
    }

    public void writeInt32LE(int offset, int value) { writeUInt32LE(offset, value); }
    public void writeInt32BE(int offset, int value) { writeUInt32BE(offset, value); }
    public void writeDoubleLE(int offset, double value) { writeInt64LE(offset, Double.doubleToRawLongBits(value)); }
    public void writeDoubleBE(int offset, double value) { writeInt64BE(offset, Double.doubleToRawLongBits(value)); }
    public void writeFloatLE(int offset, float value) { writeInt32LE(offset, Float.floatToRawIntBits(value)); }
    public void writeFloatBE(int offset, float value) { writeInt32BE(offset, Float.floatToRawIntBits(value)); }

    private void writeInt64LE(int offset, long value) {
        int p = viewIndex(offset, 8);
        for (int i = 0; i < 8; i++) bytes[p + i] = (byte) (value >>> (i * 8));
    }

    private void writeInt64BE(int offset, long value) {
        int p = viewIndex(offset, 8);
        for (int i = 0; i < 8; i++) bytes[p + i] = (byte) (value >>> ((7 - i) * 8));
    }

    private int viewIndex(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index " + index + " outside view length " + length);
        }
        return offset + index;
    }

    private int viewIndex(int index, int byteCount) {
        if (index < 0 || byteCount < 0 || index > length - byteCount) {
            throw new IndexOutOfBoundsException("index " + index + " outside view length " + length);
        }
        return offset + index;
    }

    private String toHex() {
        StringBuilder builder = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            builder.append(String.format("%02x", bytes[offset + i] & 0xFF));
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
