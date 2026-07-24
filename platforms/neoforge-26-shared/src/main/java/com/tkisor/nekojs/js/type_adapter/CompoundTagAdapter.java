package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.JSTypeAdapter;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.nbt.*;
import graal.graalvm.polyglot.Value;

public final class CompoundTagAdapter implements JSTypeAdapter<CompoundTag> {

    @Override
    public Class<CompoundTag> getTargetClass() {
        return CompoundTag.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                object());
    }

    @Override
    public boolean test(Value value) {
        return value.isNull() || value.hasMembers() || (value.isHostObject() && value.asHostObject() instanceof CompoundTag);
    }

    @Override
    public CompoundTag apply(Value value) {
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
            // B5: 分级 int / long / double，避免大整数精度丢失
            if (val.fitsInInt()) return IntTag.valueOf(val.asInt());
            if (val.fitsInLong()) return LongTag.valueOf(val.asLong());
            return DoubleTag.valueOf(val.asDouble());
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
            return apply(val);
        }

        return StringTag.valueOf(val.toString());
    }
}