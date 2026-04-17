package com.tkisor.nekojs.wrapper.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import graal.graalvm.polyglot.Value;

public class PersistentDataJS {
    private final ItemStack rawStack;

    public PersistentDataJS(ItemStack rawStack) {
        this.rawStack = rawStack;
    }

    private CompoundTag getTag() {
        return rawStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private void saveTag(CompoundTag tag) {
        if (tag.isEmpty()) {
            rawStack.remove(DataComponents.CUSTOM_DATA);
        } else {
            rawStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
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

    public byte getByte(String key) { return getTag().getByteOr(key, (byte) 0); }
    public short getShort(String key) { return getTag().getShortOr(key, (short) 0); }
    public int getInt(String key) { return getTag().getIntOr(key, 0); }
    public long getLong(String key) { return getTag().getLongOr(key, 0L); }
    public float getFloat(String key) { return getTag().getFloatOr(key, 0.0f); }
    public double getDouble(String key) { return getTag().getDoubleOr(key, 0.0); }
    public String getString(String key) { return getTag().getStringOr(key, ""); }
    public boolean getBoolean(String key) { return getTag().getBooleanOr(key, false); }

    public byte[] getByteArray(String key) { return getTag().getByteArray(key).orElse(new byte[0]); }
    public int[] getIntArray(String key) { return getTag().getIntArray(key).orElse(new int[0]); }
    public long[] getLongArray(String key) { return getTag().getLongArray(key).orElse(new long[0]); }

    // ================= 🌟 强类型 Putter (支持链式调用) =================

    public PersistentDataJS putByte(String key, byte value) {
        CompoundTag tag = getTag();
        tag.putByte(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putShort(String key, short value) {
        CompoundTag tag = getTag();
        tag.putShort(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putInt(String key, int value) {
        CompoundTag tag = getTag();
        tag.putInt(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putLong(String key, long value) {
        CompoundTag tag = getTag();
        tag.putLong(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putFloat(String key, float value) {
        CompoundTag tag = getTag();
        tag.putFloat(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putDouble(String key, double value) {
        CompoundTag tag = getTag();
        tag.putDouble(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putString(String key, String value) {
        CompoundTag tag = getTag();
        tag.putString(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putBoolean(String key, boolean value) {
        CompoundTag tag = getTag();
        tag.putBoolean(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putByteArray(String key, byte[] value) {
        CompoundTag tag = getTag();
        tag.putByteArray(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putIntArray(String key, int[] value) {
        CompoundTag tag = getTag();
        tag.putIntArray(key, value);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS putLongArray(String key, long[] value) {
        CompoundTag tag = getTag();
        tag.putLongArray(key, value);
        saveTag(tag);
        return this;
    }

    // ================= 🔮 动态类型/兜底读写 =================

    /**
     * 万能 Put：自动识别 JS 传入的基础类型
     */
    public PersistentDataJS put(String key, Value value) {
        if (value.isNull()) return remove(key);
        if (value.isBoolean()) return putBoolean(key, value.asBoolean());
        if (value.isString()) return putString(key, value.asString());
        if (value.isNumber()) {
            double d = value.asDouble();
            if (d == Math.floor(d)) {
                return putInt(key, value.asInt());
            } else {
                return putDouble(key, d);
            }
        }
        return putString(key, value.asString());
    }

    /**
     * 万能 Get：读取并自动转化为 JS 基础类型，如果不存在返回 null
     */
    public Object get(String key) {
        CompoundTag tag = getTag();
        if (!tag.contains(key)) return null;

        Tag element = tag.get(key);

        if (element instanceof NumericTag num) {
            double d = num.doubleValue();
            if (d == Math.floor(d)) return num.intValue();
            return d;
        }

        if (element instanceof StringTag str) {
            return str.value();
        }

        return element.toString();
    }
}