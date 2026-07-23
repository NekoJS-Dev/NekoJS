package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.data.AbstractJSTypeAdapter;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.ValueConversionException;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.List;

import static com.tkisor.nekojs.api.AdapterInputShape.*;

/**
 * 1.12.2 SoundEvent adapter using ForgeRegistries.SOUND_EVENTS.
 */
public class SoundEventAdapter extends AbstractJSTypeAdapter<SoundEvent> {

    @Override
    public Class<SoundEvent> getTargetClass() {
        return SoundEvent.class;
    }

    @Override
    public List<AdapterInputShape> inputShapes() {
        return List.of(
                self(),
                registry("SoundEvent"),
                host(NekoId.class),
                string());
    }

    @Override
    protected SoundEvent fromString(String rawId) {
        String id = rawId.trim();
        if (!id.contains(":")) id = "minecraft:" + id;
        ResourceLocation rl = new ResourceLocation(id);
        SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (event == null) {
            throw new ValueConversionException(SoundEvent.class, "registered sound event id", rawId,
                    "SoundEvent not found: " + id);
        }
        return event;
    }

    @Override
    protected SoundEvent fromHostObject(Object host) {
        if (host instanceof SoundEvent se) return se;
        if (host instanceof NekoId(String ns, String path)) {
            return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(ns, path));
        }
        if (host instanceof ResourceLocation rl) {
            return ForgeRegistries.SOUND_EVENTS.getValue(rl);
        }
        return null;
    }
}
