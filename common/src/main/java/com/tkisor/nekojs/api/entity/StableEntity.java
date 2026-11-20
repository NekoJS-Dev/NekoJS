package com.tkisor.nekojs.api.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.ScriptNullable;
import org.jetbrains.annotations.Nullable;

/** Live, portable view of an entity. Script access is scoped to the delivering callback. */
@Doc("Live, portable view of an entity during a script callback.")
public interface StableEntity {
    @Doc("Returns the entity's plain scoreboard name.")
    String getName();

    @Doc("Returns the canonical entity UUID string.")
    String getUuid();

    @Doc("Returns the namespaced entity type ID.")
    String getTypeId();

    @Doc("Returns the namespaced ID of the entity's current dimension.")
    String getDimension();

    @Doc("Returns the entity's current X coordinate.")
    double getX();

    @Doc("Returns the entity's current Y coordinate.")
    double getY();

    @Doc("Returns the entity's current Z coordinate.")
    double getZ();

    @Doc("Returns whether the entity is alive.")
    boolean isAlive();

    @Doc("Returns whether the entity belongs to the logical client.")
    boolean isClientSide();

    @ScriptNullable
    @Doc("Returns this entity as a living entity, or null if it is not one.")
    default @Nullable StableLivingEntity asLivingEntity() {
        return this instanceof StableLivingEntity living ? living : null;
    }

    @ScriptNullable
    @Doc("Returns this entity as a player, or null if it is not one.")
    default @Nullable StablePlayer asPlayer() {
        return this instanceof StablePlayer player ? player : null;
    }

    @ScriptNullable
    @Doc("Returns this entity as a server player, or null if it is not one.")
    default @Nullable StableServerPlayer asServerPlayer() {
        return this instanceof StableServerPlayer player ? player : null;
    }
}
