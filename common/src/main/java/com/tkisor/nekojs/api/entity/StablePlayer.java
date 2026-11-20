package com.tkisor.nekojs.api.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.stage.StageHandle;

/** Live player on either logical side. */
@Doc("Live player on either logical side.")
public interface StablePlayer extends StableLivingEntity {
    @Doc("Returns whether the player is in Creative mode.")
    boolean isCreative();

    @Doc("Returns whether the player is in Spectator mode.")
    boolean isSpectator();

    @Doc("Returns the player's stage handle, read-only on the logical client.")
    StageHandle getStages();
}
