package dev.latvian.mods.kubejs.holder;

import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.Holder;

public interface HolderWrapper {
    static Holder<?> wrap(KubeJSContext context, Object input, TypeInfo elementType) {
        input = Wrapper.unwrapped(input);
        if (input instanceof Holder<?> holder && elementType.asClass().isInstance(holder.value())) return holder;
        throw new UnsupportedOperationException("[NEKO-4286] Holder conversion requires a matching native holder or an explicit contextual registry lookup");
    }
}
