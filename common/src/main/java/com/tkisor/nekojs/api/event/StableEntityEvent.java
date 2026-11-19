package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of an entity lifecycle, damage, or combat event.
 */
@Doc("Immutable portable snapshot of an entity lifecycle, damage, or combat event.")
public final class StableEntityEvent {
    private final String entityTypeId;
    private final String entityUuid;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float amount;

    /**
     * Creates an immutable entity event snapshot.
     *
     * @param entityTypeId namespaced entity type id; null is normalized to an empty string
     * @param entityUuid canonical string representation of the entity UUID; null becomes empty
     * @param dimension namespaced dimension id; null is normalized to an empty string
     * @param x entity X coordinate
     * @param y entity Y coordinate
     * @param z entity Z coordinate
     * @param amount event-specific numeric amount such as damage or heal value, or 0 when not applicable
     */
    @Doc("Creates an immutable entity event snapshot.")
    @Param(name = "entityTypeId", value = "namespaced entity type id; null is normalized to an empty string")
    @Param(name = "entityUuid", value = "canonical string representation of the entity UUID; null becomes empty")
    @Param(name = "dimension", value = "namespaced dimension id; null is normalized to an empty string")
    @Param(name = "x", value = "entity X coordinate")
    @Param(name = "y", value = "entity Y coordinate")
    @Param(name = "z", value = "entity Z coordinate")
    @Param(name = "amount", value = "event-specific numeric amount such as damage or heal value, or 0 when not applicable")
    public StableEntityEvent(String entityTypeId, String entityUuid, String dimension, double x, double y, double z, float amount) {
        this.entityTypeId = Objects.requireNonNullElse(entityTypeId, "");
        this.entityUuid = Objects.requireNonNullElse(entityUuid, "");
        this.dimension = Objects.requireNonNullElse(dimension, "");
        this.x = x;
        this.y = y;
        this.z = z;
        this.amount = amount;
    }

    /**
     * Returns the namespaced entity type id.
     *
     * @return non-null entity type id
     */
    @Doc("Returns the namespaced entity type id.")
    @Return("non-null entity type id")
    public String getEntityTypeId() {
        return entityTypeId;
    }

    /**
     * Returns the canonical entity UUID string.
     *
     * @return non-null entity UUID string
     */
    @Doc("Returns the canonical entity UUID string.")
    @Return("non-null entity UUID string")
    public String getEntityUuid() {
        return entityUuid;
    }

    /**
     * Returns the namespaced dimension id.
     *
     * @return non-null dimension id
     */
    @Doc("Returns the namespaced dimension id.")
    @Return("non-null dimension id")
    public String getDimension() {
        return dimension;
    }

    /**
     * Returns the entity X coordinate.
     *
     * @return entity X coordinate
     */
    @Doc("Returns the entity X coordinate.")
    @Return("entity X coordinate")
    public double getX() {
        return x;
    }

    /**
     * Returns the entity Y coordinate.
     *
     * @return entity Y coordinate
     */
    @Doc("Returns the entity Y coordinate.")
    @Return("entity Y coordinate")
    public double getY() {
        return y;
    }

    /**
     * Returns the entity Z coordinate.
     *
     * @return entity Z coordinate
     */
    @Doc("Returns the entity Z coordinate.")
    @Return("entity Z coordinate")
    public double getZ() {
        return z;
    }

    /**
     * Returns the numeric amount associated with this entity event (for example damage or healing).
     *
     * @return event numeric amount
     */
    @Doc("Returns the numeric amount associated with this entity event (for example damage or healing).")
    @Return("event numeric amount")
    public float getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableEntityEvent that)) return false;
        return Objects.equals(this.entityTypeId, that.entityTypeId)
                && Objects.equals(this.entityUuid, that.entityUuid)
                && Objects.equals(this.dimension, that.dimension)
                && Double.compare(this.x, that.x) == 0
                && Double.compare(this.y, that.y) == 0
                && Double.compare(this.z, that.z) == 0
                && Float.compare(this.amount, that.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTypeId, entityUuid, dimension, x, y, z, amount);
    }

    @Override
    public String toString() {
        return "StableEntityEvent{"
                + "entityTypeId='" + entityTypeId + "'"
                + ", entityUuid='" + entityUuid + "'"
                + ", dimension='" + dimension + "'"
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z
                + ", amount=" + amount
                + "}";
    }
}
