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
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagTypes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NeoForgeNbtBinaryCodec implements NbtBinaryCodec {
    public static final NeoForgeNbtBinaryCodec INSTANCE = new NeoForgeNbtBinaryCodec();

    @Override
    public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
        validateCompressedInput(compressed, limits);

        try {
            CompoundTag root = NbtIo.readCompressed(
                    new ByteArrayInputStream(compressed),
                    new NbtAccounter(limits.maxDecodedBytes(), NbtValue.MAX_DEPTH)
            );
            if (root == null) {
                throw invalid("Compressed NBT does not contain a root compound");
            }
            requireEmptyListsUseEndElementType(compressed, limits);

            NbtValue.CompoundValue value = (NbtValue.CompoundValue) fromNative(root, new LimitTracker(), 0);
            ensureDecodedSize(value, limits);
            return value;
        } catch (NbtBinaryException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw nativeFailure("Failed to decode compressed NBT", exception);
        }
    }

    @Override
    public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits) throws NbtBinaryException {
        if (root == null) {
            throw invalid("NBT root compound must not be null");
        }
        if (limits == null) {
            throw invalid("NBT binary limits must not be null");
        }

        ensureDecodedSize(root, limits);
        try {
            CompoundTag nativeRoot = (CompoundTag) toNative(root, new LimitTracker(), 0);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nativeRoot, new LimitedOutputStream(output, limits.maxCompressedBytes()));
            return output.toByteArray();
        } catch (NbtBinaryException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw nativeFailure("Failed to encode compressed NBT", exception);
        }
    }

    private static void validateCompressedInput(byte[] compressed, NbtBinaryLimits limits) throws NbtBinaryException {
        if (compressed == null) {
            throw invalid("Compressed NBT must not be null");
        }
        if (limits == null) {
            throw invalid("NBT binary limits must not be null");
        }
        if (compressed.length > limits.maxCompressedBytes()) {
            throw fileSize("Compressed NBT exceeds " + limits.maxCompressedBytes() + " bytes");
        }
    }

    private static void requireEmptyListsUseEndElementType(byte[] compressed, NbtBinaryLimits limits)
            throws NbtBinaryException {
        // 26.x ListTag no longer remembers the element type of an empty list, so the
        // strictness the 1.21.1/cleanroom codecs enforce via ListTag.getElementType()
        // has to be checked here with a second pass over the raw compressed bytes.
        EmptyListElementTypeValidator validator = new EmptyListElementTypeValidator();
        try {
            NbtIo.parseCompressed(
                    new ByteArrayInputStream(compressed),
                    validator,
                    new NbtAccounter(limits.maxDecodedBytes(), NbtValue.MAX_DEPTH)
            );
        } catch (IOException | RuntimeException exception) {
            throw nativeFailure("Failed to decode compressed NBT", exception);
        }
        if (validator.foundInvalidEmptyList) {
            throw invalid("Empty native NBT list must use END element type");
        }
    }

    private static NbtValue fromNative(Tag tag, LimitTracker tracker, int depth) throws NbtBinaryException {
        if (tag == null) {
            throw invalid("NBT compound or list contains a null tag");
        }
        if (tag instanceof EndTag) {
            throw invalid("Standalone END tags are not portable NBT values");
        }
        if (tag instanceof LongArrayTag) {
            throw invalid("Long arrays are not supported by portable NBT");
        }
        if (tag instanceof ByteTag byteTag) {
            tracker.value(depth, Byte.BYTES);
            return NbtValue.byteValue(byteTag.value());
        }
        if (tag instanceof ShortTag shortTag) {
            tracker.value(depth, Short.BYTES);
            return NbtValue.shortValue(shortTag.value());
        }
        if (tag instanceof IntTag intTag) {
            tracker.value(depth, Integer.BYTES);
            return NbtValue.intValue(intTag.value());
        }
        if (tag instanceof LongTag longTag) {
            tracker.value(depth, Long.BYTES);
            return NbtValue.longValue(longTag.value());
        }
        if (tag instanceof FloatTag floatTag) {
            float value = floatTag.value();
            if (!Float.isFinite(value)) {
                throw invalid("NBT float must be finite");
            }
            tracker.value(depth, Float.BYTES);
            return NbtValue.floatValue(value);
        }
        if (tag instanceof DoubleTag doubleTag) {
            double value = doubleTag.value();
            if (!Double.isFinite(value)) {
                throw invalid("NBT double must be finite");
            }
            tracker.value(depth, Double.BYTES);
            return NbtValue.doubleValue(value);
        }
        if (tag instanceof StringTag stringTag) {
            tracker.value(depth, 0L);
            String value = stringTag.value();
            tracker.string(value, "NBT string");
            return NbtValue.string(value);
        }
        if (tag instanceof ByteArrayTag byteArrayTag) {
            byte[] values = byteArrayTag.getAsByteArray();
            tracker.value(depth, Integer.BYTES);
            tracker.array(values.length, Byte.BYTES, "NBT byte array");
            return NbtValue.byteArray(values);
        }
        if (tag instanceof IntArrayTag intArrayTag) {
            int[] values = intArrayTag.getAsIntArray();
            tracker.value(depth, Integer.BYTES);
            tracker.array(values.length, Integer.BYTES, "NBT int array");
            return NbtValue.intArray(values);
        }
        if (tag instanceof ListTag listTag) {
            tracker.value(depth, 0L);
            int size = listTag.size();
            tracker.listEntries(size);
            if (size == 0) {
                return new NbtValue.ListValue(NbtValue.Kind.END, List.of());
            }

            List<NbtValue> values = new ArrayList<>(size);
            NbtValue.Kind elementKind = null;
            for (int index = 0; index < size; index++) {
                NbtValue value = fromNative(listTag.get(index), tracker, depth + 1);
                if (elementKind == null) {
                    elementKind = value.kind();
                } else if (value.kind() != elementKind) {
                    throw invalid("NBT list contains mixed element types");
                }
                values.add(value);
            }
            return new NbtValue.ListValue(elementKind, values);
        }
        if (tag instanceof CompoundTag compoundTag) {
            tracker.value(depth, 0L);
            Map<String, NbtValue> values = new LinkedHashMap<>();
            for (String key : compoundTag.keySet()) {
                tracker.compoundEntry(key);
                values.put(key, fromNative(compoundTag.get(key), tracker, depth + 1));
            }
            return NbtValue.compound(values);
        }

        throw invalid("Unsupported native NBT tag: " + tag.getClass().getSimpleName());
    }

    private static Tag toNative(NbtValue value, LimitTracker tracker, int depth) throws NbtBinaryException {
        if (value == null) {
            throw invalid("Portable NBT contains a null value");
        }
        if (value instanceof NbtValue.ByteValue byteValue) {
            tracker.value(depth, Byte.BYTES);
            return ByteTag.valueOf(byteValue.value());
        }
        if (value instanceof NbtValue.ShortValue shortValue) {
            tracker.value(depth, Short.BYTES);
            return ShortTag.valueOf(shortValue.value());
        }
        if (value instanceof NbtValue.IntValue intValue) {
            tracker.value(depth, Integer.BYTES);
            return IntTag.valueOf(intValue.value());
        }
        if (value instanceof NbtValue.LongValue longValue) {
            tracker.value(depth, Long.BYTES);
            return LongTag.valueOf(longValue.value());
        }
        if (value instanceof NbtValue.FloatValue floatValue) {
            if (!Float.isFinite(floatValue.value())) {
                throw invalid("NBT float must be finite");
            }
            tracker.value(depth, Float.BYTES);
            return FloatTag.valueOf(floatValue.value());
        }
        if (value instanceof NbtValue.DoubleValue doubleValue) {
            if (!Double.isFinite(doubleValue.value())) {
                throw invalid("NBT double must be finite");
            }
            tracker.value(depth, Double.BYTES);
            return DoubleTag.valueOf(doubleValue.value());
        }
        if (value instanceof NbtValue.StringValue stringValue) {
            tracker.value(depth, 0L);
            tracker.string(stringValue.value(), "NBT string");
            return StringTag.valueOf(stringValue.value());
        }
        if (value instanceof NbtValue.ByteArrayValue byteArrayValue) {
            byte[] values = byteArrayValue.values();
            tracker.value(depth, Integer.BYTES);
            tracker.array(values.length, Byte.BYTES, "NBT byte array");
            return new ByteArrayTag(values);
        }
        if (value instanceof NbtValue.IntArrayValue intArrayValue) {
            int[] values = intArrayValue.values();
            tracker.value(depth, Integer.BYTES);
            tracker.array(values.length, Integer.BYTES, "NBT int array");
            return new IntArrayTag(values);
        }
        if (value instanceof NbtValue.ListValue listValue) {
            tracker.value(depth, 0L);
            List<NbtValue> values = listValue.values();
            tracker.listEntries(values.size());

            if (values.isEmpty()) {
                if (listValue.elementKind() != NbtValue.Kind.END) {
                    throw invalid("Empty portable NBT lists must use END element kind");
                }
                return new ListTag();
            }
            if (listValue.elementKind() == NbtValue.Kind.END) {
                throw invalid("Non-empty portable NBT lists cannot use END element kind");
            }

            ListTag nativeList = new ListTag();
            for (NbtValue element : values) {
                if (element == null || element.kind() != listValue.elementKind()) {
                    throw invalid("Portable NBT lists must contain one element kind");
                }
                if (!nativeList.addTag(nativeList.size(), toNative(element, tracker, depth + 1))) {
                    throw invalid("Native NBT rejected a portable list element");
                }
            }
            return nativeList;
        }
        if (value instanceof NbtValue.CompoundValue compoundValue) {
            tracker.value(depth, 0L);
            CompoundTag nativeCompound = new CompoundTag();
            for (Map.Entry<String, NbtValue> entry : compoundValue.values().entrySet()) {
                tracker.compoundEntry(entry.getKey());
                nativeCompound.put(entry.getKey(), toNative(entry.getValue(), tracker, depth + 1));
            }
            return nativeCompound;
        }

        throw invalid("Unsupported portable NBT value: " + value.getClass().getSimpleName());
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
            throw invalid("Unsupported portable NBT value: " + value.getClass().getSimpleName());
        }
    }

    private static NbtBinaryException nativeFailure(String operation, Throwable cause) {
        NbtBinaryException.Reason reason = hasCause(cause, CompressedLimitException.class)
                ? NbtBinaryException.Reason.FILE_SIZE
                : hasCauseNamed(cause, "NbtAccounter")
                ? NbtBinaryException.Reason.LIMIT
                : NbtBinaryException.Reason.INVALID;
        return new NbtBinaryException(reason, operation + ": " + cause.getMessage(), cause);
    }

    private static boolean hasCauseNamed(Throwable cause, String nameFragment) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current.getClass().getSimpleName().contains(nameFragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCause(Throwable cause, Class<? extends Throwable> type) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static NbtBinaryException invalid(String message) {
        return new NbtBinaryException(NbtBinaryException.Reason.INVALID, message);
    }

    private static NbtBinaryException limit(String message) {
        return new NbtBinaryException(NbtBinaryException.Reason.LIMIT, message);
    }

    private static NbtBinaryException fileSize(String message) {
        return new NbtBinaryException(NbtBinaryException.Reason.FILE_SIZE, message);
    }

    private static final class EmptyListElementTypeValidator implements StreamTagVisitor {
        private boolean foundInvalidEmptyList;

        @Override
        public StreamTagVisitor.ValueResult visitList(TagType<?> elementType, int size) {
            if (size == 0 && elementType != TagTypes.getType(Tag.TAG_END)) {
                foundInvalidEmptyList = true;
                return StreamTagVisitor.ValueResult.HALT;
            }
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visitEnd() {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(String value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(byte value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(short value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(int value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(long value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(float value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(double value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(byte[] value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(int[] value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visit(long[] value) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
            return StreamTagVisitor.EntryResult.ENTER;
        }

        @Override
        public StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
            return StreamTagVisitor.EntryResult.ENTER;
        }

        @Override
        public StreamTagVisitor.EntryResult visitElement(TagType<?> type, int index) {
            return StreamTagVisitor.EntryResult.ENTER;
        }

        @Override
        public StreamTagVisitor.ValueResult visitContainerEnd() {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }

        @Override
        public StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
            return StreamTagVisitor.ValueResult.CONTINUE;
        }
    }

    private static final class LimitTracker {
        private int nodes;

        private void value(int depth, long bytes) throws NbtBinaryException {
            if (depth > NbtValue.MAX_DEPTH) {
                throw limit("NBT exceeds maximum depth of " + NbtValue.MAX_DEPTH);
            }
            if (++nodes > NbtValue.MAX_NODES) {
                throw limit("NBT exceeds maximum node count of " + NbtValue.MAX_NODES);
            }
        }

        private void compoundEntry(String key) throws NbtBinaryException {
            string(key, "NBT compound key");
        }

        private void listEntries(int size) throws NbtBinaryException {
            if (size > NbtValue.MAX_NODES) {
                throw limit("NBT list exceeds " + NbtValue.MAX_NODES + " values");
            }
        }

        private void string(String value, String label) throws NbtBinaryException {
            if (value == null) {
                throw invalid(label + " must not be null");
            }
            if (value.length() > NbtValue.MAX_STRING_CHARS) {
                throw limit(label + " exceeds " + NbtValue.MAX_STRING_CHARS + " characters");
            }
            int modifiedUtfBytes = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                modifiedUtfBytes += character >= 1 && character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
                if (modifiedUtfBytes > 65_535) {
                    throw limit(label + " exceeds 65535 modified UTF-8 bytes");
                }
            }
        }

        private void array(int length, int elementBytes, String label) throws NbtBinaryException {
            if (length > NbtValue.MAX_NODES) {
                throw limit(label + " exceeds " + NbtValue.MAX_NODES + " values");
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
                throw limit("Decoded NBT exceeds " + maximum + " bytes");
            }
            size += bytes;
        }

        private void addModifiedUtf(String value) throws NbtBinaryException {
            int bytes = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                bytes += character >= 1 && character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
                if (bytes > 65_535) {
                    throw limit("NBT binary string exceeds 65535 modified UTF-8 bytes");
                }
            }
            add(2L + bytes);
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final int maxBytes;
        private long written;

        private LimitedOutputStream(OutputStream delegate, int maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            if (values == null) {
                throw new NullPointerException("values");
            }
            if (offset < 0 || length < 0 || length > values.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            reserve(length);
            delegate.write(values, offset, length);
        }

        private void reserve(int bytes) throws CompressedLimitException {
            if (written > maxBytes - (long) bytes) {
                throw new CompressedLimitException(maxBytes);
            }
            written += bytes;
        }
    }

    private static final class CompressedLimitException extends IOException {
        private static final long serialVersionUID = 1L;

        private CompressedLimitException(int maxBytes) {
            super("Compressed NBT exceeds " + maxBytes + " bytes");
        }
    }
}
