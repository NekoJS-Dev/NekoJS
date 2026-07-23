package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 EntityType adapter - returns EntityEntry from ForgeRegistries.
 * In 1.12.2, entity types are EntityEntry objects, not EntityType.
 */
public class EntityTypeAdapter extends AbstractJSTypeAdapter<EntityEntry> {

    @Override
    public Class<EntityEntry> getTargetClass() {
        return EntityEntry.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("EntityType"),
                host(NekoId.class),
                string());
    }

    @Override
    protected EntityEntry fromString(String rawId) {
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl = new ResourceLocation(id);
        EntityEntry entry = ForgeRegistries.ENTITIES.getValue(rl);
        if (entry == null) {
            throw new ValueConversionException(EntityEntry.class, "registered entity id", rawId,
                    "Entity not found: " + id);
        }
        return entry;
    }

    @Override
    protected EntityEntry fromHostObject(Object host) {
        if (host instanceof EntityEntry ee) return ee;
        if (host instanceof NekoId(String ns, String path)) {
            return ForgeRegistries.ENTITIES.getValue(new ResourceLocation(ns, path));
        }
        if (host instanceof ResourceLocation rl) {
            return ForgeRegistries.ENTITIES.getValue(rl);
        }
        return null;
    }
}
