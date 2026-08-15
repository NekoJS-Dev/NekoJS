package com.tkisor.nekojs.platform.nbt;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.nbt.NbtBinaryException;
import com.tkisor.nekojs.api.nbt.NbtBinaryLimits;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CleanroomNbtBinaryCodec implements NbtBinaryCodec {
    public static final CleanroomNbtBinaryCodec INSTANCE = new CleanroomNbtBinaryCodec();

    private CleanroomNbtBinaryCodec() {
    }

    @Override
    public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
        requireCompressedInput(compressed, limits);

        NBTTagCompound root;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new java.util.zip.GZIPInputStream(new ByteArrayInputStream(compressed))))) {
            root = CompressedStreamTools.read(input, new NBTSizeTracker(limits.maxDecodedBytes()));
        } catch (IOException error) {
            throw invalid("Compressed NBT cannot be decoded", error);
        } catch (RuntimeException error) {
            if (isSizeTrackerFailure(error)) {
                throw limit("Compressed NBT exceeds the decoded byte limit", error);
            }
            throw invalid("Compressed NBT cannot be decoded", error);
        }

        try {
            NbtValue.CompoundValue portable = toPortableCompound(root, 0, new Budget());
            ensureDecodedSize(portable, limits);
            return portable;
        } catch (NbtBinaryException error) {
            throw error;
        } catch (RuntimeException error) {
            throw invalid("Compressed NBT contains an invalid native tag", error);
        }
    }

    @Override
    public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException {
        if (root == null) throw invalid("NBT root compound cannot be null", null);
        if (limits == null) throw invalid("NBT binary limits cannot be null", null);

        ensureDecodedSize(root, limits);
        NBTTagCompound nativeRoot = toNativeCompound(root, 0, new Budget());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CompressedStreamTools.writeCompressed(nativeRoot,
                    new LimitedOutputStream(output, limits.maxCompressedBytes()));
            return output.toByteArray();
        } catch (CompressedLimitException error) {
            throw fileSize("Compressed NBT exceeds " + limits.maxCompressedBytes() + " bytes", error);
        } catch (IOException | RuntimeException error) {
            throw invalid("NBT cannot be encoded", error);
        }
    }

    private static void requireCompressedInput(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
        if (compressed == null) throw invalid("Compressed NBT cannot be null", null);
        if (limits == null) throw invalid("NBT binary limits cannot be null", null);
        if (compressed.length > limits.maxCompressedBytes()) {
            throw fileSize("Compressed NBT exceeds " + limits.maxCompressedBytes() + " bytes", null);
        }
    }

    private static NbtValue.CompoundValue toPortableCompound(NBTTagCompound compound, int depth, Budget budget)
            throws NbtBinaryException {
        NbtValue value = toPortable(compound, depth, budget);
        return (NbtValue.CompoundValue) value;
    }

    private static NbtValue toPortable(NBTBase tag, int depth, Budget budget) throws NbtBinaryException {
        if (tag == null) throw invalid("NBT tag cannot be null", null);
        budget.visit(depth);

        if (tag instanceof NBTTagByte value) return NbtValue.byteValue(value.getByte());
        if (tag instanceof NBTTagShort value) return NbtValue.shortValue(value.getShort());
        if (tag instanceof NBTTagInt value) return NbtValue.intValue(value.getInt());
        if (tag instanceof NBTTagLong value) return NbtValue.longValue(value.getLong());
        if (tag instanceof NBTTagFloat value) {
            float number = value.getFloat();
            if (!Float.isFinite(number)) throw invalid("NBT float must be finite", null);
            return NbtValue.floatValue(number);
        }
        if (tag instanceof NBTTagDouble value) {
            double number = value.getDouble();
            if (!Double.isFinite(number)) throw invalid("NBT double must be finite", null);
            return NbtValue.doubleValue(number);
        }
        if (tag instanceof NBTTagString value) {
            String string = value.getString();
            checkString(string, "NBT string");
            return NbtValue.string(string);
        }
        if (tag instanceof NBTTagByteArray value) {
            byte[] values = value.getByteArray();
            checkArrayLength(values.length, "NBT byte array");
            return NbtValue.byteArray(values);
        }
        if (tag instanceof NBTTagIntArray value) {
            int[] values = value.getIntArray();
            checkArrayLength(values.length, "NBT int array");
            return NbtValue.intArray(values);
        }
        if (tag instanceof NBTTagList value) {
            List<NbtValue> values = new ArrayList<>(value.tagCount());
            for (int index = 0; index < value.tagCount(); index++) {
                values.add(toPortable(value.get(index), depth + 1, budget));
            }
            try {
                return NbtValue.list(values);
            } catch (IllegalArgumentException error) {
                throw invalid("NBT list must contain one tag type", error);
            }
        }
        if (tag instanceof NBTTagCompound value) {
            Map<String, NbtValue> values = new LinkedHashMap<>();
            for (String key : value.getKeySet()) {
                checkString(key, "NBT compound key");
                values.put(key, toPortable(value.getTag(key), depth + 1, budget));
            }
            try {
                return NbtValue.compound(values);
            } catch (IllegalArgumentException error) {
                throw invalid("NBT compound contains an invalid key or value", error);
            }
        }
        if (tag instanceof NBTTagLongArray) {
            throw invalid("NBT long arrays are not portable", null);
        }
        if (tag instanceof NBTTagEnd) {
            throw invalid("Standalone NBT end tags are not portable", null);
        }
        throw invalid("Unsupported native NBT tag " + tag.getClass().getName(), null);
    }

    private static NBTTagCompound toNativeCompound(NbtValue.CompoundValue compound, int depth, Budget budget)
            throws NbtBinaryException {
        return (NBTTagCompound) toNative(compound, depth, budget);
    }

    private static NBTBase toNative(NbtValue value, int depth, Budget budget) throws NbtBinaryException {
        if (value == null) throw invalid("NBT value cannot be null", null);
        budget.visit(depth);

        if (value instanceof NbtValue.ByteValue number) return new NBTTagByte(number.value());
        if (value instanceof NbtValue.ShortValue number) return new NBTTagShort(number.value());
        if (value instanceof NbtValue.IntValue number) return new NBTTagInt(number.value());
        if (value instanceof NbtValue.LongValue number) return new NBTTagLong(number.value());
        if (value instanceof NbtValue.FloatValue number) {
            if (!Float.isFinite(number.value())) throw invalid("NBT float must be finite", null);
            return new NBTTagFloat(number.value());
        }
        if (value instanceof NbtValue.DoubleValue number) {
            if (!Double.isFinite(number.value())) throw invalid("NBT double must be finite", null);
            return new NBTTagDouble(number.value());
        }
        if (value instanceof NbtValue.StringValue string) {
            checkString(string.value(), "NBT string");
            return new NBTTagString(string.value());
        }
        if (value instanceof NbtValue.ByteArrayValue array) {
            byte[] values = array.values();
            checkArrayLength(values.length, "NBT byte array");
            return new NBTTagByteArray(values);
        }
        if (value instanceof NbtValue.IntArrayValue array) {
            int[] values = array.values();
            checkArrayLength(values.length, "NBT int array");
            return new NBTTagIntArray(values);
        }
        if (value instanceof NbtValue.ListValue list) {
            if (list.values().isEmpty() != (list.elementKind() == NbtValue.Kind.END)) {
                throw invalid("NBT list has an invalid END element kind", null);
            }
            NBTTagList result = new NBTTagList();
            for (NbtValue child : list.values()) {
                if (child.kind() != list.elementKind()) {
                    throw invalid("NBT list must contain one value kind", null);
                }
                result.appendTag(toNative(child, depth + 1, budget));
            }
            return result;
        }
        if (value instanceof NbtValue.CompoundValue compound) {
            NBTTagCompound result = new NBTTagCompound();
            for (Map.Entry<String, NbtValue> entry : compound.values().entrySet()) {
                checkString(entry.getKey(), "NBT compound key");
                result.setTag(entry.getKey(), toNative(entry.getValue(), depth + 1, budget));
            }
            return result;
        }
        throw invalid("Unsupported portable NBT value " + value.getClass().getName(), null);
    }

    private static void checkString(String value, String label) throws NbtBinaryException {
        if (value == null) throw invalid(label + " cannot be null", null);
        if (value.length() > NbtValue.MAX_STRING_CHARS) {
            throw limit(label + " exceeds " + NbtValue.MAX_STRING_CHARS + " characters", null);
        }
    }

    private static void checkArrayLength(int length, String label) throws NbtBinaryException {
        if (length > NbtValue.MAX_NODES) {
            throw limit(label + " exceeds " + NbtValue.MAX_NODES + " values", null);
        }
    }

    private static void ensureDecodedSize(NbtValue.CompoundValue root, NbtBinaryLimits limits)
            throws NbtBinaryException {
        EncodedSizeCounter counter = new EncodedSizeCounter(limits.maxDecodedBytes());
        counter.add(1);
        counter.addModifiedUtf("");
        countNativePayload(root, counter);
    }

    private static void countNativePayload(NbtValue value, EncodedSizeCounter counter) throws NbtBinaryException {
        if (value instanceof NbtValue.ByteValue) {
            counter.add(1);
        } else if (value instanceof NbtValue.ShortValue) {
            counter.add(2);
        } else if (value instanceof NbtValue.IntValue || value instanceof NbtValue.FloatValue) {
            counter.add(4);
        } else if (value instanceof NbtValue.LongValue || value instanceof NbtValue.DoubleValue) {
            counter.add(8);
        } else if (value instanceof NbtValue.StringValue string) {
            counter.addModifiedUtf(string.value());
        } else if (value instanceof NbtValue.ByteArrayValue array) {
            counter.add(4L + array.values().length);
        } else if (value instanceof NbtValue.IntArrayValue array) {
            counter.add(4L + 4L * array.values().length);
        } else if (value instanceof NbtValue.ListValue list) {
            counter.add(5);
            for (NbtValue child : list.values()) countNativePayload(child, counter);
        } else if (value instanceof NbtValue.CompoundValue compound) {
            for (Map.Entry<String, NbtValue> entry : compound.values().entrySet()) {
                counter.add(1);
                counter.addModifiedUtf(entry.getKey());
                countNativePayload(entry.getValue(), counter);
            }
            counter.add(1);
        } else {
            throw invalid("Unsupported portable NBT value " + value.getClass().getName(), null);
        }
    }

    private static NbtBinaryException invalid(String message, Throwable cause) {
        return new NbtBinaryException(NbtBinaryException.Reason.INVALID, message, cause);
    }

    private static NbtBinaryException limit(String message, Throwable cause) {
        return new NbtBinaryException(NbtBinaryException.Reason.LIMIT, message, cause);
    }

    private static NbtBinaryException fileSize(String message, Throwable cause) {
        return new NbtBinaryException(NbtBinaryException.Reason.FILE_SIZE, message, cause);
    }

    private static boolean isSizeTrackerFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("NBT tag that was too big")) return true;
        }
        return false;
    }

    private static final class Budget {
        private int nodes;

        void visit(int depth) throws NbtBinaryException {
            if (depth > NbtValue.MAX_DEPTH) {
                throw limit("NBT nesting exceeds " + NbtValue.MAX_DEPTH, null);
            }
            if (++nodes > NbtValue.MAX_NODES) {
                throw limit("NBT contains more than " + NbtValue.MAX_NODES + " values", null);
            }
        }
    }

    private static final class EncodedSizeCounter {
        private final long maximum;
        private long size;

        private EncodedSizeCounter(long maximum) {
            this.maximum = maximum;
        }

        private void add(long bytes) throws NbtBinaryException {
            if (bytes < 0 || bytes > maximum - size) {
                throw limit("Decoded NBT exceeds " + maximum + " bytes", null);
            }
            size += bytes;
        }

        private void addModifiedUtf(String value) throws NbtBinaryException {
            int bytes = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                bytes += character >= 1 && character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
                if (bytes > 65_535) {
                    throw limit("NBT binary string exceeds 65535 modified UTF-8 bytes", null);
                }
            }
            add(2L + bytes);
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final int maximum;
        private int written;

        private LimitedOutputStream(OutputStream delegate, int maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            if (offset < 0 || length < 0 || length > values.length - offset) throw new IndexOutOfBoundsException();
            reserve(length);
            delegate.write(values, offset, length);
        }

        private void reserve(int bytes) throws CompressedLimitException {
            if (bytes > maximum - written) throw new CompressedLimitException();
            written += bytes;
        }
    }

    private static final class CompressedLimitException extends IOException {
        private static final long serialVersionUID = 1L;

        private CompressedLimitException() {
            super("Compressed NBT exceeds configured limit");
        }
    }
}
