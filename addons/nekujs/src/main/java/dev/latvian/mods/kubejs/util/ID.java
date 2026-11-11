package dev.latvian.mods.kubejs.util;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Resource identifiers interpreted without a global registry or script context. */
public interface ID {
    static ResourceLocation mc(Object value) { return of(value, "minecraft"); }
    static ResourceLocation of(Object value, String namespace) {
        if (value instanceof ResourceLocation id) return id;
        if (value instanceof ResourceKey<?> key) return key.location();
        if (value instanceof Holder<?> holder) return holder.unwrapKey().orElseThrow(() ->
                new IllegalArgumentException("[NEKO-4283] Direct holder has no resource identifier")).location();
        if (value instanceof CharSequence text) return text.toString().indexOf(':') >= 0 ? ResourceLocation.parse(text.toString()) : ResourceLocation.fromNamespaceAndPath(namespace, text.toString());
        throw new IllegalArgumentException("[NEKO-4283] Unsupported resource identifier value: " + value);
    }
    static boolean isKey(Object value) { return value instanceof CharSequence || value instanceof ResourceLocation || value instanceof ResourceKey<?>; }
    static String reduce(ResourceLocation id) { return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString(); }
    static String resourcePath(ResourceLocation id) { return id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "/" + id.getPath(); }
    static DataResult<ResourceLocation> read(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && ResourceLocation.isAllowedInResourceLocation(reader.peek())) reader.skip();
        return ResourceLocation.read(reader.getString().substring(start, reader.getCursor()));
    }
}
