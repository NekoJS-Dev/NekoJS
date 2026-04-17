package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.resources.Identifier;
import graal.graalvm.polyglot.Value;

public class IdentifierAdapter implements JSTypeAdapter<Identifier> {

    private static final String DEFAULT_NAMESPACE = NekoJS.MODID;

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
        String id = value.asString();

        if (id.contains(":")) {
            return Identifier.parse(id);
        } else {
            return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, id);
        }
    }
}