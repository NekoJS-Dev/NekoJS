package dev.latvian.mods.kubejs.script.data;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.resources.ResourceLocation;

/** Explicit borrowed data-map sink; it never claims to generate a datapack itself. */
public class VirtualDataMapFile<DT, RT> implements BiConsumer<ResourceLocation, DT> {
    private final BiConsumer<ResourceLocation, DT> sink;
    public VirtualDataMapFile(BiConsumer<ResourceLocation, DT> sink) { this.sink = Objects.requireNonNull(sink); }
    @Override public void accept(ResourceLocation id, DT data) { sink.accept(Objects.requireNonNull(id), Objects.requireNonNull(data)); }
}
