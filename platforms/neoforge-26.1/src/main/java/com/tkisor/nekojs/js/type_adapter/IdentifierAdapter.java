package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.resources.Identifier;

public class IdentifierAdapter implements JSTypeAdapter<Identifier> {

    private static final String DEFAULT_NAMESPACE = NekoJS.MODID;

    @Override
    public Class<Identifier> getTargetClass() {
        return Identifier.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isString() || value.isHostObject() && value.asHostObject() instanceof NekoId;
    }

    @Override
    public Identifier convert(Value value) {
        if (value.isHostObject() && value.asHostObject() instanceof NekoId id) {
            return Identifier.fromNamespaceAndPath(id.namespace(), id.path());
        }

        String id = value.asString();
        if (id.contains(":")) {
            return Identifier.parse(id);
        }
        return Identifier.fromNamespaceAndPath(DEFAULT_NAMESPACE, id);
    }
}
