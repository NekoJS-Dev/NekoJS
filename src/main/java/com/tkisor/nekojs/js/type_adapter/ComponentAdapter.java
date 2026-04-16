package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.network.chat.Component;
import graal.graalvm.polyglot.Value;

public class ComponentAdapter implements JSTypeAdapter<Component> {
    @Override
    public Class<Component> getTargetClass() {
        return Component.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isString();
    }

    @Override
    public Component convert(Value value) {
        return Component.literal(value.asString());
    }
}
