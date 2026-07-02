package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.ValueConversionException;
import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;
import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * EntityType 适配器。修复旧实现：
 * <ul>
 *   <li>B3：id 解析失败不再 {@code return null}（apply 返回 null 会导致下游 NPE），改为抛
 *       {@link ValueConversionException}。</li>
 *   <li>B7：找不到 entity type 不再抛 {@code NoSuchElementException}，统一抛
 *       {@link ValueConversionException}。</li>
 * </ul>
 */
public class EntityTypeAdapter extends AbstractJSTypeAdapter<EntityType<?>> {

    @Override
    @SuppressWarnings("unchecked")
    public Class<EntityType<?>> getTargetClass() {
        // 泛型化 Class 的双转型不可避免触发 unchecked 警告；运行期类型擦除后即 EntityType.class。
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
    protected EntityType<?> fromString(String rawId) {
        return entityFromId(parseId(rawId));
    }

    @Override
    protected EntityType<?> fromHostObject(Object host) {
        if (host instanceof EntityType<?> type) return type;
        if (host instanceof NekoId id) return entityFromId(ResourceLocation.fromNamespaceAndPath(id.namespace(), id.path()));
        return null;
    }

    private static EntityType<?> entityFromId(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
            .orElseThrow(() -> new ValueConversionException(EntityType.class, "entity type id", id,
                "Could not find EntityType with ID: " + id));
    }

    private static ResourceLocation parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ValueConversionException(EntityType.class, "entity type id string", rawId,
                "empty entity type id");
        }
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new ValueConversionException(EntityType.class, "entity type id string", rawId,
                "invalid entity type id: " + rawId);
        }
        return location;
    }
}
