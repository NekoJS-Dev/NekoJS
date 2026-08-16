package com.tkisor.nekojs.wrapper.pdata;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
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

import java.util.Arrays;
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
@Doc("Script-facing persistent data view backed by a mutable NBTTagCompound.")
@Doc("Scalar getters return type defaults (0/''/false) for missing keys per 1.12.2 NBT semantics; putters write in place and return this for chaining.")
public class PersistentDataJS {
    private final Supplier<NBTTagCompound> getter;
    private final Consumer<NBTTagCompound> saver;
    private final Runnable dirtyMarker;
    private final Runnable syncer;
    private final boolean readOnly;

    /** 以 getter/saver 构造（无 dirty 标记与同步回调）。 */
    public PersistentDataJS(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> saver) {
        this(getter, saver, () -> {}, () -> {}, false);
    }

    /** 完整构造（含 dirty 标记与同步回调）。 */
    public PersistentDataJS(Supplier<NBTTagCompound> getter, Consumer<NBTTagCompound> saver, Runnable dirtyMarker, Runnable syncer) {
        this(getter, saver, dirtyMarker, syncer, false);
    }

    /** 构造只读视图（客户端镜像，写操作抛异常）。 */
    @Doc("Creates a read-only view over a tag supplier; all write operations throw.")
    @Param(name = "getter", value = "supplier of the backing NBTTagCompound")
    @Return("a read-only PersistentDataJS")
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

    /** 是否包含 key。 */
    @Doc("Checks whether a key exists.")
    @Param(name = "key", value = "the NBT key")
    @Return("true if the key exists")
    public boolean contains(String key) {
        return getTag().hasKey(key);
    }

    /** 移除 key。 */
    @Doc("Removes a key.")
    @Param(name = "key", value = "the NBT key to remove")
    @Return("this, for chaining")
    public PersistentDataJS remove(String key) {
        NBTTagCompound tag = getTag();
        tag.removeTag(key);
        saveTag(tag);
        return this;
    }

    /** 合并另一个 compound。 */
    @Doc("Merges another NBTTagCompound into this data.")
    @Param(name = "other", value = "the compound to merge; its entries overwrite same-named keys")
    @Return("this, for chaining")
    public PersistentDataJS merge(NBTTagCompound other) {
        NBTTagCompound tag = getTag();
        tag.merge(other);
        saveTag(tag);
        return this;
    }

    /** 清空全部数据。 */
    @Doc("Removes all entries.")
    @Return("this, for chaining")
    public PersistentDataJS clear() {
        saveTag(new NBTTagCompound());
        return this;
    }

    /** 深拷贝底层 tag。 */
    @Doc("Gets a deep copy of the backing tag.")
    @Return("an independent NBTTagCompound copy")
    public NBTTagCompound copyTag() {
        return getTag().copy();
    }

    /** 整体替换底层 tag（拷贝后写入）。 */
    @Doc("Replaces the whole backing tag with a copy of the given tag.")
    @Param(name = "tag", value = "the replacement compound; a copy is stored")
    @Return("this, for chaining")
    public PersistentDataJS replaceTag(NBTTagCompound tag) {
        saveTag(tag.copy());
        return this;
    }

    /** 标记 dirty。 */
    @Doc("Marks the data dirty for persistence.")
    @Return("this, for chaining")
    public PersistentDataJS markDirty() {
        dirtyMarker.run();
        return this;
    }

    /** 触发同步。 */
    @Doc("Triggers the sync callback (e.g. client mirroring).")
    @Return("this, for chaining")
    public PersistentDataJS sync() {
        syncer.run();
        return this;
    }

    /**
     * 批量编辑事务：在当前 tag 上执行多次修改，结束统一写回。
     * 脚本示例：{@code entity.pdata.edit(tag => { tag.setInteger("a",1); tag.setString("b","x"); })}
     */
    @Doc("Applies multiple mutations in one transaction, saving once at the end.")
    @Param(name = "editor", value = "callback mutating the current tag directly")
    @Return("this, for chaining")
    public PersistentDataJS edit(Consumer<NBTTagCompound> editor) {
        NBTTagCompound tag = getTag();
        editor.accept(tag);
        saveTag(tag);
        return this;
    }

    // ===================== scalar getter（缺失返回默认值，1.12.2 原生语义） =====================

    /** 取 byte（缺失返回 0）。 */
    @Doc("Gets a byte value; 0 when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the byte value, or 0")
    public byte getByte(String key) { return getTag().getByte(key); }

    /** 取 short（缺失返回 0）。 */
    @Doc("Gets a short value; 0 when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the short value, or 0")
    public short getShort(String key) { return getTag().getShort(key); }

    /** 取 int（缺失返回 0）。 */
    @Doc("Gets an int value; 0 when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the int value, or 0")
    public int getInt(String key) { return getTag().getInteger(key); }

    /** 取 long（缺失返回 0）。 */
    @Doc("Gets a long value; 0 when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the long value, or 0")
    public long getLong(String key) { return getTag().getLong(key); }

    /** 取 float（缺失返回 0）。 */
    @Doc("Gets a float value; 0 when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the float value, or 0")
    public float getFloat(String key) { return getTag().getFloat(key); }

    /** 取 double（缺失返回 0）。 */
    @Doc("Gets a double value; 0 when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the double value, or 0")
    public double getDouble(String key) { return getTag().getDouble(key); }

    /** 取 string（缺失返回空串）。 */
    @Doc("Gets a string value; '' when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the string value, or an empty string")
    public String getString(String key) { return getTag().getString(key); }

    /** 取 boolean（缺失返回 false）。 */
    @Doc("Gets a boolean value; false when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("the boolean value, or false")
    public boolean getBoolean(String key) { return getTag().getBoolean(key); }

    /** 取 byte 数组（返回拷贝）。 */
    @Doc("Gets a byte array; an empty array when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("a copy of the byte array, never null")
    public byte[] getByteArray(String key) { byte[] v = getTag().getByteArray(key); return Arrays.copyOf(v, v.length); }

    /** 取 int 数组（返回拷贝）。 */
    @Doc("Gets an int array; an empty array when the key is missing.")
    @Param(name = "key", value = "the NBT key")
    @Return("a copy of the int array, never null")
    public int[] getIntArray(String key) { int[] v = getTag().getIntArray(key); return Arrays.copyOf(v, v.length); }

    /** 取子 compound（返回拷贝）。 */
    @Doc("Gets a sub-compound; a copy is returned so mutations do not alias.")
    @Param(name = "key", value = "the NBT key")
    @Return("a copy of the sub-compound; an empty compound when the key is missing")
    public NBTTagCompound getCompound(String key) { return getTag().getCompoundTag(key).copy(); }

    // ===================== scalar putter（可变语义，setXxx 原地改） =====================

    /** 写入 byte。 */
    @Doc("Stores a byte value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the byte to store")
    @Return("this, for chaining")
    public PersistentDataJS putByte(String key, byte value) { NBTTagCompound tag = getTag(); tag.setByte(key, value); saveTag(tag); return this; }

    /** 写入 short。 */
    @Doc("Stores a short value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the short to store")
    @Return("this, for chaining")
    public PersistentDataJS putShort(String key, short value) { NBTTagCompound tag = getTag(); tag.setShort(key, value); saveTag(tag); return this; }

    /** 写入 int。 */
    @Doc("Stores an int value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the int to store")
    @Return("this, for chaining")
    public PersistentDataJS putInt(String key, int value) { NBTTagCompound tag = getTag(); tag.setInteger(key, value); saveTag(tag); return this; }

    /** 写入 long。 */
    @Doc("Stores a long value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the long to store")
    @Return("this, for chaining")
    public PersistentDataJS putLong(String key, long value) { NBTTagCompound tag = getTag(); tag.setLong(key, value); saveTag(tag); return this; }

    /** 写入 float。 */
    @Doc("Stores a float value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the float to store")
    @Return("this, for chaining")
    public PersistentDataJS putFloat(String key, float value) { NBTTagCompound tag = getTag(); tag.setFloat(key, value); saveTag(tag); return this; }

    /** 写入 double。 */
    @Doc("Stores a double value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the double to store")
    @Return("this, for chaining")
    public PersistentDataJS putDouble(String key, double value) { NBTTagCompound tag = getTag(); tag.setDouble(key, value); saveTag(tag); return this; }

    /** 写入 string。 */
    @Doc("Stores a string value.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the string to store")
    @Return("this, for chaining")
    public PersistentDataJS putString(String key, String value) { NBTTagCompound tag = getTag(); tag.setString(key, value); saveTag(tag); return this; }

    /** 写入 boolean。 */
    @Doc("Stores a boolean value (as a byte).")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the boolean to store")
    @Return("this, for chaining")
    public PersistentDataJS putBoolean(String key, boolean value) { NBTTagCompound tag = getTag(); tag.setBoolean(key, value); saveTag(tag); return this; }

    /** 写入 byte 数组（拷贝后写入）。 */
    @Doc("Stores a byte array; a copy is written.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the byte array to store")
    @Return("this, for chaining")
    public PersistentDataJS putByteArray(String key, byte[] value) { NBTTagCompound tag = getTag(); tag.setByteArray(key, Arrays.copyOf(value, value.length)); saveTag(tag); return this; }

    /** 写入 int 数组（拷贝后写入）。 */
    @Doc("Stores an int array; a copy is written.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the int array to store")
    @Return("this, for chaining")
    public PersistentDataJS putIntArray(String key, int[] value) { NBTTagCompound tag = getTag(); tag.setIntArray(key, Arrays.copyOf(value, value.length)); saveTag(tag); return this; }

    /** 写入子 compound（拷贝后写入）。 */
    @Doc("Stores a sub-compound; a copy is written.")
    @Param(name = "key", value = "the NBT key")
    @Param(name = "value", value = "the compound to store")
    @Return("this, for chaining")
    public PersistentDataJS putCompound(String key, NBTTagCompound value) { NBTTagCompound tag = getTag(); tag.setTag(key, value.copy()); saveTag(tag); return this; }

    // ===================== 动态分发 get（按运行时 NBT 子类型还原 Java 值） =====================

    /**
     * 按 key 取值并按运行时 NBT 类型还原为 Java 值。缺失返回 null，复合返回 {@code .copy()}。
     * 注：1.12.2 NBTPrimitive 抽象类包私有，无法跨模块 instanceof，必须对 public 子类逐一分发。
     */
    @Doc("Gets a value by key, restored to a Java value by its runtime NBT type.")
    @Param(name = "key", value = "the NBT key")
    @Return("the Java value (compounds and arrays are copies), null when the key is missing; raw NBT nodes for lists and unknown types")
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
        if (element instanceof NBTTagByteArray ba) { byte[] v = ba.getByteArray(); return Arrays.copyOf(v, v.length); }
        if (element instanceof NBTTagIntArray ia) { int[] v = ia.getIntArray(); return Arrays.copyOf(v, v.length); }
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
