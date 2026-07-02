package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public class EntityTypeAdapter extends AbstractJSTypeAdapter<EntityType<?>> {

    @Override
    public Class<EntityType<?>> getTargetClass() {
        return (Class<EntityType<?>>) (Class<?>) EntityType.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("EntityType"),
                host(NekoId.class));
    }

    @Override
    protected EntityType<?> fromString(String s) {
        String id = s.trim();
        if (id.isEmpty()) {
            throw new ValueConversionException(EntityType.class, "entity type id", s,
                "empty id");
        }
        if (!id.contains(":")) id = "minecraft:" + id;
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            // B3/B7: 解析失败统一抛 ValueConversionException，不再 return null / NoSuchElementException
            throw new ValueConversionException(EntityType.class, "valid entity type id", s,
                "invalid id syntax");
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location)
            .orElseThrow(() -> new ValueConversionException(EntityType.class, "registered entity type id", s,
                "entity type not found: " + location));
    }

    @Override
    protected EntityType<?> fromHostObject(Object host) {
        if (host instanceof EntityType<?> type) return type;
        if (host instanceof NekoId id) {
            return fromString(id.namespace() + ":" + id.path());
        }
        return null; // 不识别
    }
}
