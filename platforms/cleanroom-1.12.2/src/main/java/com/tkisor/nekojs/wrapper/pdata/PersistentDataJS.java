package com.tkisor.nekojs.wrapper.pdata;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 1.12.2 PersistentDataJS - 基于可变 {@link NBTTagCompound} 的脚本侧持久化数据包装。
 *
 * <p>与 1.21.x 版本差异：
 * <ul>
 *   <li>NBT 直接可变（setXxx 原地改），putter 不需要 copy-then-write，但仍统一走 saveTag 触发 dirty</li>
 *   <li>无 long[] getter/setter（1.12.2 NBTTagCompound 不支持 setLongArray/getLongArray）</li>
 *   <li>子 compound 取引用（getCompoundTag 返回内部节点），getter 用 {@code .copy()} 防别名</li>
 *   <li>缺失 key 的 scalar getter 返回类型默认值（0/""/false）—— 这是 1.12.2 NBTTagCompound 的原生语义</li>
 * </ul>
 */
public class PersistentDataJS {
    private final Supplier<NBTTagCompound> getter;
    private final Consumer<NBTTagCompound> saver;
    private final Runnable dirtyMarker;
    private final Runnable syncer;
    private final boolean readOnly;

    public PersistentDataJS(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> saver) {
        this(getter, saver, () -> {}, () -> {}, false);
    }

    public PersistentDataJS(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> saver, Runnable dirtyMarker, Runnable syncer) {
        this(getter, saver, dirtyMarker, syncer, false);
    }

    public static PersistentDataJS readOnly(Supplier<NBTTagCompound> getter) {
        return new PersistentDataJS(getter, tag -> {}, () -> {}, () -> {}, true);
    }

    private PersistentDataJS(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> saver, Runnable dirtyMarker, Runnable syncer, boolean readOnly) {
        this.getter = Objects.requireNonNull(getter);
        this.saver = Objects.requireNonNull(saver);
        this.dirtyMarker = dirtyMarker == null ? () -> {} : dirtyMarker;
        this.syncer = syncer == null ? () -> {} : syncer;
        this.readOnly = readOnly;
    }

    // ===================== contains / remove / merge / clear =====================

    public boolean contains(String key) {
        return getTag().hasKey(key);
    }

    public PersistentDataJS remove(String key) {
        NBTTagCompound tag = getTag();
        tag.removeTag(key);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS merge(NBTTagCompound other) {
        NBTTagCompound tag = getTag();
        tag.merge(other);
        saveTag(tag);
        return this;
    }

    public PersistentDataJS clear() {
        saveTag(new NBTTagCompound());
        return this;
    }

    public NBTTagCompound copyTag() {
        return getTag().copy();
    }

    public PersistentDataJS replaceTag(NBTTagCompound tag) {
        saveTag(tag.copy());
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
     * 批量编辑事务：在当前 tag 上执行多次修改，结束统一写回。
     * 脚本示例：{@code entity.pdata.edit(tag => { tag.setInteger("a",1); tag.setString("b","x"); })}
     */
    public PersistentDataJS edit(Consumer<NBTTagCompound> editor) {
        NBTTagCompound tag = getTag();
        editor.accept(tag);
        saveTag(tag);
        return this;
    }

    // ===================== scalar getter（缺失返回默认值，1.12.2 原生语义） =====================

    public byte getByte(String key) { return getTag().getByte(key); }
    public short getShort(String key) { return getTag().getShort(key); }
    public int getInt(String key) { return getTag().getInteger(key); }
    public long getLong(String key) { return getTag().getLong(key); }
    public float getFloat(String key) { return getTag().getFloat(key); }
    public double getDouble(String key) { return getTag().getDouble(key); }
    public String getString(String key) { return getTag().getString(key); }
    public boolean getBoolean(String key) { return getTag().getBoolean(key); }
    public byte[] getByteArray(String key) { return getTag().getByteArray(key); }
    public int[] getIntArray(String key) { return getTag().getIntArray(key); }
    public NBTTagCompound getCompound(String key) { return getTag().getCompoundTag(key).copy(); }

    // ===================== scalar putter（可变语义，setXxx 原地改） =====================

    public PersistentDataJS putByte(String key, byte value) { NBTTagCompound tag = getTag(); tag.setByte(key, value); saveTag(tag); return this; }
    public PersistentDataJS putShort(String key, short value) { NBTTagCompound tag = getTag(); tag.setShort(key, value); saveTag(tag); return this; }
    public PersistentDataJS putInt(String key, int value) { NBTTagCompound tag = getTag(); tag.setInteger(key, value); saveTag(tag); return this; }
    public PersistentDataJS putLong(String key, long value) { NBTTagCompound tag = getTag(); tag.setLong(key, value); saveTag(tag); return this; }
    public PersistentDataJS putFloat(String key, float value) { NBTTagCompound tag = getTag(); tag.setFloat(key, value); saveTag(tag); return this; }
    public PersistentDataJS putDouble(String key, double value) { NBTTagCompound tag = getTag(); tag.setDouble(key, value); saveTag(tag); return this; }
    public PersistentDataJS putString(String key, String value) { NBTTagCompound tag = getTag(); tag.setString(key, value); saveTag(tag); return this; }
    public PersistentDataJS putBoolean(String key, boolean value) { NBTTagCompound tag = getTag(); tag.setBoolean(key, value); saveTag(tag); return this; }
    public PersistentDataJS putByteArray(String key, byte[] value) { NBTTagCompound tag = getTag(); tag.setByteArray(key, value); saveTag(tag); return this; }
    public PersistentDataJS putIntArray(String key, int[] value) { NBTTagCompound tag = getTag(); tag.setIntArray(key, value); saveTag(tag); return this; }
    public PersistentDataJS putCompound(String key, NBTTagCompound value) { NBTTagCompound tag = getTag(); tag.setTag(key, value.copy()); saveTag(tag); return this; }

    // ===================== 动态分发 get（按运行时 NBT 子类型还原 Java 值） =====================

    /**
     * 按 key 取值并按运行时 NBT 类型还原为 Java 值。缺失返回 null，复合返回 {@code .copy()}。
     * 注：1.12.2 NBTPrimitive 抽象类包私有，无法跨模块 instanceof，必须对 public 子类逐一分发。
     */
    public Object get(String key) {
        NBTTagCompound tag = getTag();
        if (!tag.hasKey(key)) return null;

        NBTBase element = tag.getTag(key);
        if (element instanceof NBTTagLong l) return l.getLong();
        if (element instanceof NBTTagInt i) return i.getInt();
        if (element instanceof NBTTagByte b) return b.getByte();
        if (element instanceof NBTTagShort s) return s.getShort();
        if (element instanceof NBTTagFloat f) return f.getFloat();
        if (element instanceof NBTTagDouble d) return d.getDouble();
        if (element instanceof NBTTagString str) return str.getString();
        if (element instanceof NBTTagByteArray ba) return ba.getByteArray();
        if (element instanceof NBTTagIntArray ia) return ia.getIntArray();
        if (element instanceof NBTTagCompound compound) return compound.copy();
        // NBTTagList / 其它未知类型：返回 NBT 节点本身
        return element;
    }

    // ===================== 内部 =====================

    private NBTTagCompound getTag() {
        NBTTagCompound tag = getter.get();
        return tag != null ? tag : new NBTTagCompound();
    }

    private void saveTag(NBTTagCompound tag) {
        if (readOnly) {
            throw new UnsupportedOperationException("Client pdata mirror is read-only");
        }
        saver.accept(tag);
        dirtyMarker.run();
    }
}
