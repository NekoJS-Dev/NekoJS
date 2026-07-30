package com.tkisor.nekojs.platform.nbt;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.nbt.NbtBinaryException;
import com.tkisor.nekojs.api.nbt.NbtBinaryLimits;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** NeoForge's compressed-NBT implementation of the portable binary codec contract. */
public final class NeoForgeNbtBinaryCodec implements NbtBinaryCodec {
    public static final NeoForgeNbtBinaryCodec INSTANCE = new NeoForgeNbtBinaryCodec();

    private NeoForgeNbtBinaryCodec() {
    }

    @Override
    public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
        requireLimits(limits);
        if (compressed == null) throw invalid("Compressed NBT data cannot be null");
        if (compressed.length > limits.maxCompressedBytes()) {
            throw fileSize("Compressed NBT exceeds " + limits.maxCompressedBytes() + " bytes");
        }

        final CompoundTag nativeRoot;
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressed)) {
            nativeRoot = NbtIo.readCompressed(input, new NbtAccounter(limits.maxDecodedBytes(), NbtValue.MAX_DEPTH));
        } catch (IOException | RuntimeException exception) {
            throw translateNativeFailure("read", exception);
        }

        NbtValue.CompoundValue root = fromNativeCompound(nativeRoot, new PortableBudget(), 0);
        ensureDecodedSize(root, limits);
        return root;
    }

    @Override
    public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException {
        requireLimits(limits);
        if (root == null) throw invalid("NBT root cannot be null");

        validatePortable(root, new PortableBudget(), 0);
        ensureDecodedSize(root, limits);

        CompoundTag nativeRoot = toNativeCompound(root);
        LimitedOutputStream output = new LimitedOutputStream(limits.maxCompressedBytes());
        try {
            NbtIo.writeCompressed(nativeRoot, output);
        } catch (IOException | RuntimeException exception) {
            throw translateNativeFailure("write", exception);
        }
        return output.toByteArray();
    }

    private static NbtValue.CompoundValue fromNativeCompound(CompoundTag compound, PortableBudget budget, int depth)
            throws NbtBinaryException {
        budget.visit(depth);
        Map<String, NbtValue> values = new LinkedHashMap<>();
        for (String key : compound.getAllKeys()) {
            checkPortableString(key, "NBT compound key");
            Tag child = compound.get(key);
            if (child == null) throw invalid("Native NBT compound contains a null tag");
            values.put(key, fromNative(child, budget, depth + 1));
        }
        return NbtValue.compound(values);
    }

    private static NbtValue fromNative(Tag tag, PortableBudget budget, int depth) throws NbtBinaryException {
        if (tag instanceof CompoundTag compound) return fromNativeCompound(compound, budget, depth);

        budget.visit(depth);
        if (tag instanceof ByteTag value) return NbtValue.byteValue(value.getAsByte());
        if (tag instanceof ShortTag value) return NbtValue.shortValue(value.getAsShort());
        if (tag instanceof IntTag value) return NbtValue.intValue(value.getAsInt());
        if (tag instanceof LongTag value) return NbtValue.longValue(value.getAsLong());
        if (tag instanceof FloatTag value) {
            float number = value.getAsFloat();
            if (!Float.isFinite(number)) throw invalid("Native NBT float must be finite");
            return NbtValue.floatValue(number);
        }
        if (tag instanceof DoubleTag value) {
            double number = value.getAsDouble();
            if (!Double.isFinite(number)) throw invalid("Native NBT double must be finite");
            return NbtValue.doubleValue(number);
        }
        if (tag instanceof StringTag value) {
            String string = value.getAsString();
            checkPortableString(string, "NBT string");
            return NbtValue.string(string);
        }
        if (tag instanceof ByteArrayTag value) {
            byte[] array = value.getAsByteArray();
            checkPortableArray(array.length, "byte");
            return NbtValue.byteArray(array);
        }
        if (tag instanceof IntArrayTag value) {
            int[] array = value.getAsIntArray();
            checkPortableArray(array.length, "int");
            return NbtValue.intArray(array);
        }
        if (tag instanceof ListTag value) return fromNativeList(value, budget, depth);
        if (tag instanceof LongArrayTag) throw invalid("Native NBT long arrays are not portable");
        if (tag instanceof EndTag) throw invalid("Native NBT END tag is not a portable value");
        throw invalid("Native NBT tag type " + tag.getClass().getName() + " is not portable");
    }

    private static NbtValue.ListValue fromNativeList(ListTag list, PortableBudget budget, int depth)
            throws NbtBinaryException {
        if (list.isEmpty()) {
            if (list.getElementType() != Tag.TAG_END) {
                throw invalid("Empty native NBT list must use END element type");
            }
            return new NbtValue.ListValue(NbtValue.Kind.END, List.of());
        }

        NbtValue.Kind expectedKind = portableKind(list.getElementType());
        List<NbtValue> values = new ArrayList<>(list.size());
        for (Tag child : list) {
            NbtValue value = fromNative(child, budget, depth + 1);
            if (value.kind() != expectedKind) {
                throw invalid("Native NBT list contains an element with a different tag type");
            }
            values.add(value);
        }
        return new NbtValue.ListValue(expectedKind, values);
    }

    private static NbtValue.Kind portableKind(byte nativeType) throws NbtBinaryException {
        return switch (nativeType) {
            case Tag.TAG_BYTE -> NbtValue.Kind.BYTE;
            case Tag.TAG_SHORT -> NbtValue.Kind.SHORT;
            case Tag.TAG_INT -> NbtValue.Kind.INT;
            case Tag.TAG_LONG -> NbtValue.Kind.LONG;
            case Tag.TAG_FLOAT -> NbtValue.Kind.FLOAT;
            case Tag.TAG_DOUBLE -> NbtValue.Kind.DOUBLE;
            case Tag.TAG_BYTE_ARRAY -> NbtValue.Kind.BYTE_ARRAY;
            case Tag.TAG_STRING -> NbtValue.Kind.STRING;
            case Tag.TAG_LIST -> NbtValue.Kind.LIST;
            case Tag.TAG_COMPOUND -> NbtValue.Kind.COMPOUND;
            case Tag.TAG_INT_ARRAY -> NbtValue.Kind.INT_ARRAY;
            case Tag.TAG_LONG_ARRAY -> throw invalid("Native NBT long-array lists are not portable");
            case Tag.TAG_END -> throw invalid("Non-empty native NBT list cannot use END element type");
            default -> throw invalid("Native NBT list has an unsupported element type " + nativeType);
        };
    }

    private static CompoundTag toNativeCompound(NbtValue.CompoundValue compound) throws NbtBinaryException {
        CompoundTag result = new CompoundTag();
        for (Map.Entry<String, NbtValue> entry : compound.values().entrySet()) {
            result.put(entry.getKey(), toNative(entry.getValue()));
        }
        return result;
    }

    private static Tag toNative(NbtValue value) throws NbtBinaryException {
        if (value instanceof NbtValue.ByteValue number) return ByteTag.valueOf(number.value());
        if (value instanceof NbtValue.ShortValue number) return ShortTag.valueOf(number.value());
        if (value instanceof NbtValue.IntValue number) return IntTag.valueOf(number.value());
        if (value instanceof NbtValue.LongValue number) return LongTag.valueOf(number.value());
        if (value instanceof NbtValue.FloatValue number) return FloatTag.valueOf(number.value());
        if (value instanceof NbtValue.DoubleValue number) return DoubleTag.valueOf(number.value());
        if (value instanceof NbtValue.StringValue string) return StringTag.valueOf(string.value());
        if (value instanceof NbtValue.ByteArrayValue array) return new ByteArrayTag(array.values());
        if (value instanceof NbtValue.IntArrayValue array) return new IntArrayTag(array.values());
        if (value instanceof NbtValue.ListValue list) {
            ListTag result = new ListTag();
            for (NbtValue element : list.values()) result.add(toNative(element));
            return result;
        }
        if (value instanceof NbtValue.CompoundValue compound) return toNativeCompound(compound);
        throw invalid("Portable NBT value has an unsupported type");
    }

    private static void validatePortable(NbtValue value, PortableBudget budget, int depth) throws NbtBinaryException {
        budget.visit(depth);
        if (value instanceof NbtValue.StringValue string) {
            checkPortableString(string.value(), "NBT string");
        } else if (value instanceof NbtValue.ByteArrayValue array) {
            checkPortableArray(array.values().length, "byte");
        } else if (value instanceof NbtValue.IntArrayValue array) {
            checkPortableArray(array.values().length, "int");
        } else if (value instanceof NbtValue.ListValue list) {
            if (list.values().isEmpty()) {
                if (list.elementKind() != NbtValue.Kind.END) throw invalid("Empty portable NBT list must use END element type");
            } else {
                if (list.elementKind() == NbtValue.Kind.END) throw invalid("Non-empty portable NBT list cannot use END element type");
                for (NbtValue element : list.values()) {
                    if (element.kind() != list.elementKind()) {
                        throw invalid("Portable NBT list contains an element with a different tag type");
                    }
                    validatePortable(element, budget, depth + 1);
                }
            }
        } else if (value instanceof NbtValue.CompoundValue compound) {
            for (Map.Entry<String, NbtValue> entry : compound.values().entrySet()) {
                checkPortableString(entry.getKey(), "NBT compound key");
                validatePortable(entry.getValue(), budget, depth + 1);
            }
        }
    }

    private static void ensureDecodedSize(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException {
        EncodedSizeCounter counter = new EncodedSizeCounter(limits.maxDecodedBytes());
        counter.add(1);
        counter.addModifiedUtf("");
        countNativePayload(root, counter);
    }

    private static void countNamedNativeTag(String name, NbtValue value, EncodedSizeCounter counter)
            throws NbtBinaryException {
        counter.add(1);
        counter.addModifiedUtf(name);
        countNativePayload(value, counter);
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
            for (NbtValue element : list.values()) countNativePayload(element, counter);
        } else if (value instanceof NbtValue.CompoundValue compound) {
            for (Map.Entry<String, NbtValue> entry : compound.values().entrySet()) {
                countNamedNativeTag(entry.getKey(), entry.getValue(), counter);
            }
            counter.add(1);
        } else {
            throw invalid("Portable NBT value has an unsupported type");
        }
    }

    private static void requireLimits(NbtBinaryLimits limits) throws NbtBinaryException {
        if (limits == null) throw invalid("NBT binary limits cannot be null");
    }

    private static void checkPortableString(String value, String label) throws NbtBinaryException {
        if (value.length() > NbtValue.MAX_STRING_CHARS) {
            throw limit(label + " exceeds " + NbtValue.MAX_STRING_CHARS + " characters");
        }
    }

    private static void checkPortableArray(int length, String type) throws NbtBinaryException {
        if (length > NbtValue.MAX_NODES) {
            throw limit("NBT " + type + " array exceeds " + NbtValue.MAX_NODES + " values");
        }
    }

    private static NbtBinaryException translateNativeFailure(String operation, Throwable failure) {
        if (failure instanceof LimitedOutputException) {
            return fileSize("Native NBT " + operation + " exceeded the compressed size limit", failure);
        }
        if (failure instanceof UTFDataFormatException || isAccountingFailure(failure)) {
            return limit("Native NBT " + operation + " exceeded a size limit", failure);
        }
        return invalid("Native NBT " + operation + " failed", failure);
    }

    private static boolean isAccountingFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getClass().getName().equals("net.minecraft.nbt.NbtAccounterException")) return true;
        }
        return false;
    }

    private static NbtBinaryException invalid(String message) {
        return new NbtBinaryException(NbtBinaryException.Reason.INVALID, message);
    }

    private static NbtBinaryException invalid(String message, Throwable cause) {
        return new NbtBinaryException(NbtBinaryException.Reason.INVALID, message, cause);
    }

    private static NbtBinaryException limit(String message) {
        return new NbtBinaryException(NbtBinaryException.Reason.LIMIT, message);
    }

    private static NbtBinaryException limit(String message, Throwable cause) {
        return new NbtBinaryException(NbtBinaryException.Reason.LIMIT, message, cause);
    }

    private static NbtBinaryException fileSize(String message) {
        return new NbtBinaryException(NbtBinaryException.Reason.FILE_SIZE, message);
    }

    private static NbtBinaryException fileSize(String message, Throwable cause) {
        return new NbtBinaryException(NbtBinaryException.Reason.FILE_SIZE, message, cause);
    }

    private static final class PortableBudget {
        private int nodes;

        void visit(int depth) throws NbtBinaryException {
            if (depth > NbtValue.MAX_DEPTH) {
                throw limit("NBT nesting exceeds " + NbtValue.MAX_DEPTH);
            }
            if (++nodes > NbtValue.MAX_NODES) {
                throw limit("NBT contains more than " + NbtValue.MAX_NODES + " values");
            }
        }
    }

    private static final class EncodedSizeCounter {
        private final long maximum;
        private long size;

        private EncodedSizeCounter(long maximum) {
            this.maximum = maximum;
        }

        void add(long bytes) throws NbtBinaryException {
            if (bytes < 0 || bytes > maximum - size) {
                throw limit("Decoded NBT exceeds " + maximum + " bytes");
            }
            size += bytes;
        }

        void addModifiedUtf(String value) throws NbtBinaryException {
            int utfBytes = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                utfBytes += character >= 1 && character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
                if (utfBytes > 65_535) {
                    throw limit("NBT binary string exceeds 65535 modified UTF-8 bytes");
                }
            }
            add(2L + utfBytes);
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        private final int maximum;

        private LimitedOutputStream(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            if (offset < 0 || length < 0 || offset > values.length - length) throw new IndexOutOfBoundsException();
            reserve(length);
            delegate.write(values, offset, length);
        }

        byte[] toByteArray() {
            return delegate.toByteArray();
        }

        private void reserve(int bytes) throws LimitedOutputException {
            if (bytes > maximum - delegate.size()) {
                throw new LimitedOutputException();
            }
        }
    }

    private static final class LimitedOutputException extends IOException {
        private LimitedOutputException() {
            super("Compressed NBT exceeds configured limit");
        }
    }
}
