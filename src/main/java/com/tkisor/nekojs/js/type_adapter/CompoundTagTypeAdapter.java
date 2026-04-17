package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.nbt.*;
import graal.graalvm.polyglot.Value;

public final class CompoundTagTypeAdapter implements JSTypeAdapter<CompoundTag> {

    @Override
    public Class<CompoundTag> getTargetClass() {
        return CompoundTag.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isNull() || value.hasMembers() || (value.isHostObject() && value.asHostObject() instanceof CompoundTag);
    }

    @Override
    public CompoundTag convert(Value value) {
        if (value.isNull()) return new CompoundTag();
        if (value.isHostObject() && value.asHostObject() instanceof CompoundTag tag) return tag;

        CompoundTag tag = new CompoundTag();
        for (String key : value.getMemberKeys()) {
            Value member = value.getMember(key);
            tag.put(key, valueToTag(member));
        }
        return tag;
    }

    private Tag valueToTag(Value val) {
        if (val.isNull()) return StringTag.valueOf("");
        if (val.isBoolean()) return ByteTag.valueOf(val.asBoolean());
        if (val.isNumber()) {
            double d = val.asDouble();
            if (d == Math.floor(d)) return IntTag.valueOf(val.asInt());
            return DoubleTag.valueOf(d);
        }
        if (val.isString()) return StringTag.valueOf(val.asString());

        // 如果遇到嵌套数组：转成 ListTag
        if (val.hasArrayElements()) {
            ListTag list = new ListTag();
            for (long i = 0; i < val.getArraySize(); i++) {
                list.add(valueToTag(val.getArrayElement(i)));
            }
            return list;
        }

        // 如果遇到嵌套对象：递归转成 CompoundTag
        if (val.hasMembers()) {
            return convert(val);
        }

        return StringTag.valueOf(val.toString());
    }
}