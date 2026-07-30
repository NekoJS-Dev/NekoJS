package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.NbtFacade;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.core.api.nbt.NbtFileStore;
import com.tkisor.nekojs.core.api.nbt.NbtSnbtSerializer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultNbtFacade implements NbtFacade {
    private final NbtFileStore fileStore;

    public DefaultNbtFacade(Path dataRoot, NbtBinaryCodec codec) {
        this.fileStore = new NbtFileStore(dataRoot, codec);
    }

    @Override public NbtValue of(NbtValue value) { return value; }
    @Override public NbtValue byteValue(Number value) { return NbtValue.byteValue((byte) requireIntegral(value, Byte.MIN_VALUE, Byte.MAX_VALUE, "byte")); }
    @Override public NbtValue shortValue(Number value) { return NbtValue.shortValue((short) requireIntegral(value, Short.MIN_VALUE, Short.MAX_VALUE, "short")); }
    @Override public NbtValue intValue(Number value) { return NbtValue.intValue((int) requireIntegral(value, Integer.MIN_VALUE, Integer.MAX_VALUE, "int")); }
    @Override public NbtValue longValue(String value) {
        try { return NbtValue.longValue(Long.parseLong(value)); }
        catch (NumberFormatException error) { throw mismatch("NBT long must be a signed decimal string"); }
    }
    @Override public NbtValue floatValue(Number value) {
        float narrowed = requireFinite(value, "float").floatValue();
        if (!Float.isFinite(narrowed)) throw mismatch("NBT float must be in range");
        return NbtValue.floatValue(narrowed);
    }
    @Override public NbtValue doubleValue(Number value) { return NbtValue.doubleValue(requireFinite(value, "double").doubleValue()); }
    @Override public NbtValue byteArray(List<? extends Number> values) {
        byte[] result = new byte[values.size()];
        for (int index = 0; index < result.length; index++) result[index] = (byte) requireIntegral(values.get(index), Byte.MIN_VALUE, Byte.MAX_VALUE, "byte array value");
        return NbtValue.byteArray(result);
    }
    @Override public NbtValue intArray(List<? extends Number> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < result.length; index++) result[index] = (int) requireIntegral(values.get(index), Integer.MIN_VALUE, Integer.MAX_VALUE, "int array value");
        return NbtValue.intArray(result);
    }
    @Override public String toSnbt(NbtValue value) { return serialize(value); }
    @Override public String kind(NbtValue value) { return value.kind().name(); }
    @Override public Object scalar(NbtValue value) {
        if (value instanceof NbtValue.ByteValue number) return number.value();
        if (value instanceof NbtValue.ShortValue number) return number.value();
        if (value instanceof NbtValue.IntValue number) return number.value();
        if (value instanceof NbtValue.LongValue number) return Long.toString(number.value());
        if (value instanceof NbtValue.FloatValue number) return number.value();
        if (value instanceof NbtValue.DoubleValue number) return number.value();
        if (value instanceof NbtValue.StringValue string) return string.value();
        return null;
    }
    @Override public List<NbtValue> values(NbtValue value) {
        return value instanceof NbtValue.ListValue list ? list.values() : List.of();
    }
    @Override public List<NbtEntry> entries(NbtValue value) {
        if (!(value instanceof NbtValue.CompoundValue compound)) return List.of();
        List<NbtEntry> entries = new ArrayList<>();
        compound.values().forEach((key, child) -> entries.add(new NbtEntry(key, child)));
        return List.copyOf(entries);
    }
    @Override public NbtValue.CompoundValue read(String path) { return fileStore.read(path); }
    @Override public void write(String path, NbtValue value) {
        if (!(value instanceof NbtValue.CompoundValue compound)) {
            throw mismatch("NBT binary root must be a compound");
        }
        fileStore.write(path, compound);
    }

    private static String serialize(NbtValue value) {
        try { return NbtSnbtSerializer.serialize(value); }
        catch (IllegalArgumentException error) { throw new ApiInvocationException(ApiErrorCodes.NBT_LIMIT_EXCEEDED, "NBT value exceeds portable limits", Map.of(), error); }
    }
    private static long requireIntegral(Number value, long minimum, long maximum, String label) {
        double number = requireFinite(value, label).doubleValue();
        if (number != Math.rint(number) || number < minimum || number > maximum) throw mismatch("NBT " + label + " must be an integer in range");
        return (long) number;
    }
    private static Number requireFinite(Number value, String label) {
        if (value == null || !Double.isFinite(value.doubleValue())) throw mismatch("NBT " + label + " must be finite");
        return value;
    }
    private static ApiInvocationException mismatch(String message) {
        return new ApiInvocationException(ApiErrorCodes.TYPE_MISMATCH, message);
    }
}
