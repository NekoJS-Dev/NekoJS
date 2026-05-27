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

    public NekoNodeBuffer slice(int start, int end) {
        int len = bytes.length;
        int s = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
        int e = end < 0 ? Math.max(len + end, 0) : Math.min(end, len);
        int newLen = Math.max(e - s, 0);
        byte[] sliced = new byte[newLen];
        if (newLen > 0) System.arraycopy(bytes, s, sliced, 0, newLen);
        return new NekoNodeBuffer(sliced);
    }

    public NekoNodeBuffer fill(int value, int start, int end) {
        int len = bytes.length;
        int s = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
        int e = end < 0 ? Math.max(len + end, 0) : Math.min(end, len);
        Arrays.fill(bytes, s, e, (byte) value);
        return this;
    }

    public int indexOf(NekoNodeBuffer needle, int fromIndex) {
        if (needle == null || needle.bytes.length == 0) return -1;
        byte[] search = needle.bytes;
        int start = Math.max(fromIndex, 0);
        outer:
        for (int i = start; i <= bytes.length - search.length; i++) {
            for (int j = 0; j < search.length; j++) {
                if (bytes[i + j] != search[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    public boolean includes(NekoNodeBuffer needle) {
        return indexOf(needle, 0) >= 0;
    }

    public int copy(NekoNodeBuffer target, int targetStart, int sourceStart, int sourceEnd) {
        int len = bytes.length;
        int srcS = Math.max(sourceStart, 0);
        int srcE = sourceEnd < 0 ? len : Math.min(sourceEnd, len);
        int count = Math.max(srcE - srcS, 0);
        if (count > 0 && target != null) {
            System.arraycopy(bytes, srcS, target.bytes, targetStart, count);
        }
        return count;
    }

    public boolean equals(NekoNodeBuffer other) {
        if (other == null) return false;
        return Arrays.equals(bytes, other.bytes);
    }

    public int compare(NekoNodeBuffer other) {
        if (other == null) return 1;
        return Arrays.compare(bytes, other.bytes);
    }

    // Multi-byte read helpers
    public int readUInt8(int offset) { return bytes[offset] & 0xFF; }
    public int readInt8(int offset) { return bytes[offset]; }
    public int readUInt16LE(int offset) { return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8); }
    public int readUInt16BE(int offset) { return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF); }
    public int readInt16LE(int offset) { return (short) readUInt16LE(offset); }
    public int readInt16BE(int offset) { return (short) readUInt16BE(offset); }
    public long readUInt32LE(int offset) { return (bytes[offset] & 0xFFL) | ((bytes[offset + 1] & 0xFFL) << 8) | ((bytes[offset + 2] & 0xFFL) << 16) | ((bytes[offset + 3] & 0xFFL) << 24); }
    public long readUInt32BE(int offset) { return ((bytes[offset] & 0xFFL) << 24) | ((bytes[offset + 1] & 0xFFL) << 16) | ((bytes[offset + 2] & 0xFFL) << 8) | (bytes[offset + 3] & 0xFFL); }
    public int readInt32LE(int offset) { return (int) readUInt32LE(offset); }
    public int readInt32BE(int offset) { return (int) readUInt32BE(offset); }
    public double readDoubleLE(int offset) { return Double.longBitsToDouble(Long.reverseBytes(readInt64LE(offset))); }
    public double readDoubleBE(int offset) { return Double.longBitsToDouble(readInt64BE(offset)); }
    public float readFloatLE(int offset) { return Float.intBitsToFloat(Integer.reverseBytes(readInt32LE(offset))); }
    public float readFloatBE(int offset) { return Float.intBitsToFloat(readInt32BE(offset)); }

    private long readInt64LE(int offset) {
        return (bytes[offset] & 0xFFL) | ((bytes[offset + 1] & 0xFFL) << 8) | ((bytes[offset + 2] & 0xFFL) << 16) | ((bytes[offset + 3] & 0xFFL) << 24) | ((bytes[offset + 4] & 0xFFL) << 32) | ((bytes[offset + 5] & 0xFFL) << 40) | ((bytes[offset + 6] & 0xFFL) << 48) | ((bytes[offset + 7] & 0xFFL) << 56);
    }

    private long readInt64BE(int offset) {
        return ((bytes[offset] & 0xFFL) << 56) | ((bytes[offset + 1] & 0xFFL) << 48) | ((bytes[offset + 2] & 0xFFL) << 40) | ((bytes[offset + 3] & 0xFFL) << 32) | ((bytes[offset + 4] & 0xFFL) << 24) | ((bytes[offset + 5] & 0xFFL) << 16) | ((bytes[offset + 6] & 0xFFL) << 8) | (bytes[offset + 7] & 0xFFL);
    }

    // Multi-byte write helpers
    public void writeUInt8(int offset, int value) { bytes[offset] = (byte) value; }
    public void writeInt8(int offset, int value) { bytes[offset] = (byte) value; }
    public void writeUInt16LE(int offset, int value) { bytes[offset] = (byte) value; bytes[offset + 1] = (byte) (value >>> 8); }
    public void writeUInt16BE(int offset, int value) { bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value; }
    public void writeInt16LE(int offset, int value) { writeUInt16LE(offset, value); }
    public void writeInt16BE(int offset, int value) { writeUInt16BE(offset, value); }
    public void writeUInt32LE(int offset, long value) { for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (i * 8)); }
    public void writeUInt32BE(int offset, long value) { for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> ((3 - i) * 8)); }
    public void writeInt32LE(int offset, int value) { writeUInt32LE(offset, value); }
    public void writeInt32BE(int offset, int value) { writeUInt32BE(offset, value); }
    public void writeDoubleLE(int offset, double value) { writeInt64LE(offset, Double.doubleToRawLongBits(value)); }
    public void writeDoubleBE(int offset, double value) { writeInt64BE(offset, Double.doubleToRawLongBits(value)); }
    public void writeFloatLE(int offset, float value) { writeInt32LE(offset, Float.floatToRawIntBits(value)); }
    public void writeFloatBE(int offset, float value) { writeInt32BE(offset, Float.floatToRawIntBits(value)); }

    private void writeInt64LE(int offset, long value) { for (int i = 0; i < 8; i++) bytes[offset + i] = (byte) (value >>> (i * 8)); }
    private void writeInt64BE(int offset, long value) { for (int i = 0; i < 8; i++) bytes[offset + i] = (byte) (value >>> ((7 - i) * 8)); }

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
