package com.tkisor.nekojs.wrapper.pdata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PersistentDataJS {
    private final Supplier<CompoundTag> getter;
    private final Consumer<CompoundTag> saver;
    private final Runnable dirtyMarker;
    private final Runnable syncer;
    private final boolean readOnly;

    public PersistentDataJS(Supplier<CompoundTag> getter, Consumer<CompoundTag> saver) {
        this(getter, saver, () -> {}, () -> {}, false);
    }

    public PersistentDataJS(Supplier<CompoundTag> getter, Consumer<CompoundTag> saver, Runnable dirtyMarker, Runnable syncer) {
        this(getter, saver, dirtyMarker, syncer, false);
    }

    public static PersistentDataJS readOnly(Supplier<CompoundTag> getter) {
        return new PersistentDataJS(getter, tag -> {}, () -> {}, () -> {}, true);
    }

    private PersistentDataJS(Supplier<CompoundTag> getter, Consumer<CompoundTag> saver, Runnable dirtyMarker, Runnable syncer, boolean readOnly) {
        this.getter = Objects.requireNonNull(getter);
        this.saver = Objects.requireNonNull(saver);
        this.dirtyMarker = dirtyMarker == null ? () -> {} : dirtyMarker;
        this.syncer = syncer == null ? () -> {} : syncer;
        this.readOnly = readOnly;
    }

    public boolean contains(String key) {
        return getTag().contains(key);
    }

    public PersistentDataJS remove(String key) {
        CompoundTag tag = getTag();
        tag.remove(key);
        saveTag(tag);
        return this;
    }

    public byte getByte(String key) { return getTag().getByte(key); }
    public short getShort(String key) { return getTag().getShort(key); }
    public int getInt(String key) { return getTag().getInt(key); }
    public long getLong(String key) { return getTag().getLong(key); }
    public float getFloat(String key) { return getTag().getFloat(key); }
    public double getDouble(String key) { return getTag().getDouble(key); }
    public String getString(String key) { return getTag().getString(key); }
    public boolean getBoolean(String key) { return getTag().getBoolean(key); }
    public byte[] getByteArray(String key) { return getTag().getByteArray(key); }
    public int[] getIntArray(String key) { return getTag().getIntArray(key); }
    public long[] getLongArray(String key) { return getTag().getLongArray(key); }
    public CompoundTag getCompound(String key) { return getTag().getCompound(key).copy(); }

    public PersistentDataJS putByte(String key, byte value) { CompoundTag tag = getTag(); tag.putByte(key, value); saveTag(tag); return this; }
    public PersistentDataJS putShort(String key, short value) { CompoundTag tag = getTag(); tag.putShort(key, value); saveTag(tag); return this; }
    public PersistentDataJS putInt(String key, int value) { CompoundTag tag = getTag(); tag.putInt(key, value); saveTag(tag); return this; }
    public PersistentDataJS putLong(String key, long value) { CompoundTag tag = getTag(); tag.putLong(key, value); saveTag(tag); return this; }
    public PersistentDataJS putFloat(String key, float value) { CompoundTag tag = getTag(); tag.putFloat(key, value); saveTag(tag); return this; }
    public PersistentDataJS putDouble(String key, double value) { CompoundTag tag = getTag(); tag.putDouble(key, value); saveTag(tag); return this; }
    public PersistentDataJS putString(String key, String value) { CompoundTag tag = getTag(); tag.putString(key, value); saveTag(tag); return this; }
    public PersistentDataJS putBoolean(String key, boolean value) { CompoundTag tag = getTag(); tag.putBoolean(key, value); saveTag(tag); return this; }
    public PersistentDataJS putByteArray(String key, byte[] value) { CompoundTag tag = getTag(); tag.putByteArray(key, value); saveTag(tag); return this; }
    public PersistentDataJS putIntArray(String key, int[] value) { CompoundTag tag = getTag(); tag.putIntArray(key, value); saveTag(tag); return this; }
    public PersistentDataJS putLongArray(String key, long[] value) { CompoundTag tag = getTag(); tag.putLongArray(key, value); saveTag(tag); return this; }
    public PersistentDataJS putCompound(String key, CompoundTag value) { CompoundTag tag = getTag(); tag.put(key, value.copy()); saveTag(tag); return this; }

    public CompoundTag copyTag() {
        return getTag().copy();
    }

    public PersistentDataJS replaceTag(CompoundTag tag) {
        saveTag(tag.copy());
        return this;
    }

    public PersistentDataJS merge(CompoundTag other) {
        CompoundTag tag = getTag();
        tag.merge(other);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS clear() {
        saveTag(new CompoundTag());
        return this;
    }

    public PersistentDataJS markDirty() {
        dirtyMarker.run();
        return this;
    }

    public PersistentDataJS sync() {
        syncer.run();
        return this;
    }

    /**
     * 批量编辑事务：在一次 copy 上执行多次修改，结束统一写回，避免连续 {@code putXxx} 的多次 copy+写回。
     * 脚本示例：{@code entity.pdata.edit(tag => { tag.putInt("a",1); tag.putString("b","x"); })}
     */
    public PersistentDataJS edit(Consumer<CompoundTag> editor) {
        CompoundTag tag = getTag();
        editor.accept(tag);
        saveTag(tag);
        return this;
    }

    public Object get(String key) {
        CompoundTag tag = getTag();
        if (!tag.contains(key)) return null;

        Tag element = tag.get(key);
        if (element instanceof LongTag l) return l.getAsLong();
        if (element instanceof IntTag i) return i.getAsInt();
        if (element instanceof ByteTag b) return b.getAsByte();
        if (element instanceof ShortTag s) return s.getAsShort();
        if (element instanceof FloatTag f) return f.getAsFloat();
        if (element instanceof DoubleTag d) return d.getAsDouble();
        if (element instanceof StringTag str) return str.getAsString();
        if (element instanceof ByteArrayTag ba) return ba.getAsByteArray();
        if (element instanceof IntArrayTag ia) return ia.getAsIntArray();
        if (element instanceof LongArrayTag la) return la.getAsLongArray();
        return element;
    }

    private CompoundTag getTag() {
        return getter.get();
    }

    private void saveTag(CompoundTag tag) {
        if (readOnly) {
            throw new UnsupportedOperationException("Client pdata mirror is read-only");
        }
        saver.accept(tag);
        dirtyMarker.run();
    }
}
