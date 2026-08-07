package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.facade.NbtFacade;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.core.api.nbt.NbtFileStore;
import com.tkisor.nekojs.core.api.nbt.NbtSnbtParser;
import com.tkisor.nekojs.core.api.nbt.NbtSnbtSerializer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultNbtFacade implements NbtFacade {
    private final NbtFileStore fileStore;

    public DefaultNbtFacade(Path dataRoot, NbtBinaryCodec codec) {
        this.fileStore = new NbtFileStore(dataRoot, codec);
    }

    @Override public NbtValue of(NbtValue value) { return value; }
    @Override public Object compound() { return new com.tkisor.nekojs.wrapper.nbt.CompoundTagBuilderJS(); }
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

    @Override public NbtValue parse(String snbt) {
        try { return NbtSnbtParser.parse(snbt); }
        catch (IllegalArgumentException error) {
            throw new ApiInvocationException(ApiErrorCodes.INVALID_NBT, error.getMessage(), Map.of(), error);
        }
    }

    @Override public Object toObject(NbtValue value) {
        if (value instanceof NbtValue.CompoundValue compound) {
            Map<String, Object> map = new LinkedHashMap<>();
            compound.values().forEach((key, child) -> map.put(key, toObject(child)));
            return map;
        }
        if (value instanceof NbtValue.ListValue list) {
            List<Object> elements = new ArrayList<>(list.values().size());
            for (NbtValue child : list.values()) elements.add(toObject(child));
            return elements;
        }
        if (value instanceof NbtValue.ByteArrayValue array) {
            List<Byte> elements = new ArrayList<>(array.values().length);
            for (byte b : array.values()) elements.add(b);
            return elements;
        }
        if (value instanceof NbtValue.IntArrayValue array) {
            List<Integer> elements = new ArrayList<>(array.values().length);
            for (int i : array.values()) elements.add(i);
            return elements;
        }
        Object scalar = scalar(value);
        return scalar != null ? scalar : value;
    }

    @Override public NbtValue fromObject(Object value) {
        if (value == null) throw mismatch("cannot convert null to NBT");
        if (value instanceof NbtValue nbt) return nbt;
        if (value instanceof Map<?, ?> map) {
            Map<String, NbtValue> compound = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                compound.put(String.valueOf(entry.getKey()), fromObject(entry.getValue()));
            }
            return NbtValue.compound(compound);
        }
        if (value instanceof Collection<?> collection) {
            List<NbtValue> list = new ArrayList<>(collection.size());
            for (Object element : collection) list.add(fromObject(element));
            return NbtValue.list(list);
        }
        if (value instanceof Boolean bool) return NbtValue.byteValue((byte) (bool ? 1 : 0));
        if (value instanceof Byte number) return NbtValue.byteValue(number);
        if (value instanceof Short number) return NbtValue.shortValue(number);
        if (value instanceof Integer number) return NbtValue.intValue(number);
        if (value instanceof Long number) return NbtValue.longValue(number);
        if (value instanceof Float number) return NbtValue.floatValue(number);
        if (value instanceof Double number) return NbtValue.doubleValue(number);
        if (value instanceof Number number) return NbtValue.doubleValue(number.doubleValue());
        if (value instanceof String string) return NbtValue.string(string);
        throw mismatch("cannot convert " + value.getClass().getName() + " to NBT");
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
