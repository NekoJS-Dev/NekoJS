package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.resources.Identifier;
import org.graalvm.polyglot.Value;

public class IdentifierAdapter implements JSTypeAdapter<Identifier> {
    @Override
    public Class<Identifier> getTargetClass() {
        return Identifier.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isString();
    }

    @Override
    public Identifier convert(Value value) {
        return Identifier.tryParse(value.asString());
    }
}
