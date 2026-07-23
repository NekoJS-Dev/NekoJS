package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 Potion adapter using ForgeRegistries.POTIONS.
 */
public class PotionAdapter extends AbstractJSTypeAdapter<Potion> {

    @Override
    public Class<Potion> getTargetClass() {
        return Potion.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("Potion"),
                host(NekoId.class),
                string());
    }

    @Override
    protected Potion fromString(String rawId) {
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl = new ResourceLocation(id);
        Potion potion = ForgeRegistries.POTIONS.getValue(rl);
        if (potion == null) {
            throw new ValueConversionException(Potion.class, "registered potion id", rawId,
                    "Potion not found: " + id);
        }
        return potion;
    }

    @Override
    protected Potion fromHostObject(Object host) {
        if (host instanceof Potion p) return p;
        if (host instanceof NekoId(String ns, String path)) {
            return ForgeRegistries.POTIONS.getValue(new ResourceLocation(ns, path));
        }
        if (host instanceof ResourceLocation rl) {
            return ForgeRegistries.POTIONS.getValue(rl);
        }
        return null;
    }
}
