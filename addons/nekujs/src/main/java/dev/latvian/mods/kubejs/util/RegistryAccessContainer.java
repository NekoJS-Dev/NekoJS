package dev.latvian.mods.kubejs.util;

import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.Context;
import java.util.Objects;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;

/** Borrowed registry operations; no global current-server slot or reload cache. */
public final class RegistryAccessContainer extends RegistryOpsContainer {
    public static final RegistryAccessContainer BUILTIN = new RegistryAccessContainer(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    private final RegistryAccess.Frozen access;
    public RegistryAccessContainer(RegistryAccess.Frozen access) {
        super(Objects.requireNonNull(access).createSerializationContext(NbtOps.INSTANCE),
                access.createSerializationContext(JsonOps.INSTANCE), access.createSerializationContext(JavaOps.INSTANCE));
        this.access = access;
    }
    public RegistryAccess.Frozen access() { return access; }
    public static RegistryAccessContainer of(Context context) {
        if (context instanceof KubeJSContext nativeContext && nativeContext.getType() != ScriptType.SERVER) return BUILTIN;
        throw new UnsupportedOperationException("[NEKO-4282] Legacy context has no borrowed native registry-access service");
    }
}
