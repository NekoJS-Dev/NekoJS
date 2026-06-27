package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.NoSuchElementException;

public class EntityTypeAdapter implements JSTypeAdapter<EntityType> {

    @Override
    public Class<EntityType> getTargetClass() {
        return EntityType.class;
    }

    @Override
    public boolean test(Value value) {
        if (value.isString()) {
            return true;
        }
        return value.isHostObject() && value.asHostObject() instanceof NekoId;
    }

    @Override
    public EntityType<?> apply(Value value) {
        Identifier id;
        if (value.isHostObject() && value.asHostObject() instanceof NekoId nekoId) {
            id = Identifier.fromNamespaceAndPath(nekoId.namespace(), nekoId.path());
        } else {
            id = parseId(value.asString());
        }

        if (id == null) {
            return null;
        }

        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElseThrow(() ->
                new NoSuchElementException("Could not find EntityType with ID: " + id)
        );
    }

    private Identifier parseId(String rawId) {
        if (rawId == null) return null;
        String id = rawId.trim();
        if (id.isEmpty()) return null;
        if (!id.contains(":")) id = "minecraft:" + id;
        return Identifier.tryParse(id);
    }
}
