package dev.latvian.mods.kubejs.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Wrapper;
import java.util.Objects;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Native codec operations with explicit input representations, not JavaScript evaluation. */
public class OpsContainer {
    public static final OpsContainer DEFAULT = new OpsContainer(NbtOps.INSTANCE, JsonOps.INSTANCE, JavaOps.INSTANCE);
    private final DynamicOps<Tag> nbt;
    private final DynamicOps<JsonElement> json;
    private final DynamicOps<Object> java;
    public OpsContainer(DynamicOps<Tag> nbt, DynamicOps<JsonElement> json, DynamicOps<Object> java) {
        this.nbt = Objects.requireNonNull(nbt);
        this.json = Objects.requireNonNull(json);
        this.java = Objects.requireNonNull(java);
    }
    public DynamicOps<Tag> nbt() { return nbt; }
    public DynamicOps<JsonElement> json() { return json; }
    public DynamicOps<Object> java() { return java; }
    public <T> T decode(Context context, Codec<T> codec, Object input) {
        input = Wrapper.unwrapped(input);
        if (input instanceof Tag tag) return codec.parse(nbt, tag).getOrThrow();
        if (input instanceof JsonElement element) return codec.parse(json, element).getOrThrow();
        return codec.parse(java, input).getOrThrow();
    }
    public <T> T decodeMap(Context context, MapCodec<T> codec, Object input) { return decode(context, codec.codec(), input); }
}
