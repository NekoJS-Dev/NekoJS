package com.tkisor.nekojs.api.stage;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.data.NekoId;

import java.util.List;

/**
 * Script-facing {@code Stages} global binding facade for querying and mutating player and server stages.
 */
@Doc("Global Stages facade for querying and mutating player and server stages.")
public interface StagesFacade {

    /**
     * Returns the stage handle for the given player reference.
     *
     * @param player portable player reference
     * @return player stage handle (or {@link StageHandle#EMPTY} for fake/absent players)
     */
    @Doc("Returns the stage handle for the given player reference.")
    @Param(name = "player", value = "portable player reference")
    @Return("player stage handle")
    StageHandle player(StagePlayerRef player);

    /**
     * Returns the global server stage handle.
     *
     * @return server stage handle
     */
    @Doc("Returns the global server stage handle.")
    @Return("server stage handle")
    StageHandle server();

    /**
     * Returns a read-only view of the local client player's synchronized stages.
     *
     * @return read-only client player stage view
     */
    @Doc("Returns a read-only view of the local client player's synchronized stages.")
    @Return("read-only client player stage view")
    StageReadView clientPlayer();

    /**
     * Returns a read-only view of the client's synchronized server stages.
     *
     * @return read-only client server stage view
     */
    @Doc("Returns a read-only view of the client's synchronized server stages.")
    @Return("read-only client server stage view")
    StageReadView clientServer();

    /**
     * Returns the list of stages required to use or craft the specified item.
     *
     * @param itemId namespaced item identifier
     * @return immutable list of required stage identifiers
     */
    @Doc("Returns the list of stages required to use or craft the specified item.")
    @Param(name = "itemId", value = "namespaced item identifier")
    @Return("immutable list of required stage identifiers")
    List<String> requiredStagesForItem(NekoId itemId);
}
