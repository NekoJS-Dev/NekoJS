package com.tkisor.nekojs.api.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.spec.inject.ServerSpec;
import com.tkisor.nekojs.api.stage.StageHandle;
import net.minecraft.server.MinecraftServer;

/**
 * @see MinecraftServer
 */
@RemapByPrefix("neko$")
public interface ServerExtension extends ServerSpec {
    /**
     * Returns the in-memory data container of this server. The first access creates it in the mixin
     * and fires {@code attachServerData}.
     */
    @Override
    AttachedData<MinecraftServer> neko$data();

    /** Returns the server-wide stage handle attached to this server. */
    @Override
    StageHandle neko$getStages();
}
