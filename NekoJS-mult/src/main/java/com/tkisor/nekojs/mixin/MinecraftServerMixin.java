package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.inject.ServerExtension;
import com.tkisor.nekojs.core.plugin.AttachedDataHooks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements ServerExtension {
    @Unique
    private AttachedData<MinecraftServer> nekojs$attachedData;

    @Override
    public AttachedData<MinecraftServer> neko$data() {
        if (nekojs$attachedData == null) {
            nekojs$attachedData = new AttachedData<>((MinecraftServer) (Object) this);
            AttachedDataHooks.fireAttachServer(nekojs$attachedData);
        }
        return nekojs$attachedData;
    }
}
