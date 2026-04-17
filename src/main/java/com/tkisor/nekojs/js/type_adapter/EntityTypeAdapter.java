package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.JSTypeAdapter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import graal.graalvm.polyglot.Value;

import java.util.NoSuchElementException;

public class EntityTypeAdapter implements JSTypeAdapter<EntityType> {

    @Override
    public Class<EntityType> getTargetClass() {
        return EntityType.class;
    }

    @Override
    public boolean canConvert(Value value) {
        return value.isString();
    }

    @Override
    public EntityType<?> convert(Value value) {
        String idStr = value.asString();

        if (!idStr.contains(":")) {
            idStr = "minecraft:" + idStr;
        }

        Identifier id = Identifier.tryParse(idStr);
        if (id == null) {
            return null;
        }

        String finalIdStr = idStr;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElseThrow(() ->
                new NoSuchElementException("Could not find EntityType with ID: " + finalIdStr)
        );
    }
}