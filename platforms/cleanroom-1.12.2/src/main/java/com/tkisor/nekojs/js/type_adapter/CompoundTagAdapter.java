package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import graal.graalvm.polyglot.Value;
import net.minecraft.nbt.*;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 NBT 适配器：使用 {@link NBTTagCompound} 替代 1.21.1 的 {@code CompoundTag}。
 *
 * <p>1.12.2 NBT 类型映射：
 * <li>String -> NBTTagString</li>
 * <li>boolean -> NBTTagByte (0/1)</li>
 * <li>int -> NBTTagInt</li>
 * <li>long -> NBTTagLong</li>
 * <li>double -> NBTTagDouble</li>
 * <li>array -> NBTTagList</li>
 * <li>object -> NBTTagCompound (递归)</li>
 * </p>
 */
public final class CompoundTagAdapter implements JSTypeAdapter<NBTTagCompound> {

    @Override
    public Class<NBTTagCompound> getTargetClass() {
        return NBTTagCompound.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                object());
    }

    @Override
    public boolean test(Value value) {
        return value.isNull() || value.hasMembers()
            || (value.isHostObject() && value.asHostObject() instanceof NBTTagCompound);
    }

    @Override
    public NBTTagCompound apply(Value value) {
        if (value.isNull()) return new NBTTagCompound();
        if (value.isHostObject() && value.asHostObject() instanceof NBTTagCompound tag) return tag;

        NBTTagCompound tag = new NBTTagCompound();
        for (String key : value.getMemberKeys()) {
            Value member = value.getMember(key);
            tag.setTag(key, valueToTag(member));
        }
        return tag;
    }

    private NBTBase valueToTag(Value val) {
        if (val.isNull()) return new NBTTagString("");
        if (val.isBoolean()) return new NBTTagByte(val.asBoolean() ? (byte) 1 : (byte) 0);
        if (val.isNumber()) {
            if (val.fitsInInt()) return new NBTTagInt(val.asInt());
            if (val.fitsInLong()) return new NBTTagLong(val.asLong());
            return new NBTTagDouble(val.asDouble());
        }
        if (val.isString()) return new NBTTagString(val.asString());

        // 嵌套数组 -> NBTTagList
        if (val.hasArrayElements()) {
            NBTTagList list = new NBTTagList();
            for (long i = 0; i < val.getArraySize(); i++) {
                list.appendTag(valueToTag(val.getArrayElement(i)));
            }
            return list;
        }

        // 嵌套对象 -> NBTTagCompound (递归)
        if (val.hasMembers()) {
            return apply(val);
        }

        return new NBTTagString(val.toString());
    }
}
