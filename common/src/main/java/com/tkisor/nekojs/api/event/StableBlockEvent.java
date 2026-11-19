package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a block interaction or modification event.
 */
@Doc("Immutable portable snapshot of a block interaction or modification event.")
public final class StableBlockEvent {
    private final String dimension;
    private final int x;
    private final int y;
    private final int z;
    private final String blockId;
    private final String actorName;

    /**
     * Creates an immutable block event snapshot.
     *
     * @param dimension namespaced dimension id; null is normalized to an empty string
     * @param x block X coordinate
     * @param y block Y coordinate
     * @param z block Z coordinate
     * @param blockId namespaced block id; null is normalized to an empty string
     * @param actorName acting player or entity name when present; null becomes empty
     */
    @Doc("Creates an immutable block event snapshot.")
    @Param(name = "dimension", value = "namespaced dimension id; null is normalized to an empty string")
    @Param(name = "x", value = "block X coordinate")
    @Param(name = "y", value = "block Y coordinate")
    @Param(name = "z", value = "block Z coordinate")
    @Param(name = "blockId", value = "namespaced block id; null is normalized to an empty string")
    @Param(name = "actorName", value = "acting player or entity name when present; null becomes empty")
    public StableBlockEvent(String dimension, int x, int y, int z, String blockId, String actorName) {
        this.dimension = Objects.requireNonNullElse(dimension, "");
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = Objects.requireNonNullElse(blockId, "");
        this.actorName = Objects.requireNonNullElse(actorName, "");
    }

    /**
     * Returns the namespaced dimension id containing the block.
     *
     * @return non-null dimension id
     */
    @Doc("Returns the namespaced dimension id containing the block.")
    @Return("non-null dimension id")
    public String getDimension() {
        return dimension;
    }

    /**
     * Returns the block X coordinate.
     *
     * @return block X coordinate
     */
    @Doc("Returns the block X coordinate.")
    @Return("block X coordinate")
    public int getX() {
        return x;
    }

    /**
     * Returns the block Y coordinate.
     *
     * @return block Y coordinate
     */
    @Doc("Returns the block Y coordinate.")
    @Return("block Y coordinate")
    public int getY() {
        return y;
    }

    /**
     * Returns the block Z coordinate.
     *
     * @return block Z coordinate
     */
    @Doc("Returns the block Z coordinate.")
    @Return("block Z coordinate")
    public int getZ() {
        return z;
    }

    /**
     * Returns the namespaced block id.
     *
     * @return non-null block id
     */
    @Doc("Returns the namespaced block id.")
    @Return("non-null block id")
    public String getBlockId() {
        return blockId;
    }

    /**
     * Returns the acting player or entity name, or an empty string when absent.
     *
     * @return non-null actor name
     */
    @Doc("Returns the acting player or entity name, or an empty string when absent.")
    @Return("non-null actor name")
    public String getActorName() {
        return actorName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableBlockEvent that)) return false;
        return Objects.equals(this.dimension, that.dimension)
                && this.x == that.x
                && this.y == that.y
                && this.z == that.z
                && Objects.equals(this.blockId, that.blockId)
                && Objects.equals(this.actorName, that.actorName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, x, y, z, blockId, actorName);
    }

    @Override
    public String toString() {
        return "StableBlockEvent{"
                + "dimension='" + dimension + "'"
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z
                + ", blockId='" + blockId + "'"
                + ", actorName='" + actorName + "'"
                + "}";
    }
}
