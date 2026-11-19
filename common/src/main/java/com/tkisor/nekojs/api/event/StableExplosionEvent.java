package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a level explosion event.
 */
@Doc("Immutable portable snapshot of a level explosion event.")
public final class StableExplosionEvent {
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float radius;
    private final int affectedBlockCount;
    private final int affectedEntityCount;

    /**
     * Creates an immutable explosion event snapshot.
     *
     * @param dimension namespaced dimension id where the explosion occurred; null becomes empty
     * @param x explosion center X coordinate
     * @param y explosion center Y coordinate
     * @param z explosion center Z coordinate
     * @param radius explosion radius
     * @param affectedBlockCount number of blocks affected at snapshot time
     * @param affectedEntityCount number of entities affected at snapshot time
     */
    @Doc("Creates an immutable explosion event snapshot.")
    @Param(name = "dimension", value = "namespaced dimension id where the explosion occurred; null becomes empty")
    @Param(name = "x", value = "explosion center X coordinate")
    @Param(name = "y", value = "explosion center Y coordinate")
    @Param(name = "z", value = "explosion center Z coordinate")
    @Param(name = "radius", value = "explosion radius")
    @Param(name = "affectedBlockCount", value = "number of blocks affected at snapshot time")
    @Param(name = "affectedEntityCount", value = "number of entities affected at snapshot time")
    public StableExplosionEvent(String dimension, double x, double y, double z, float radius, int affectedBlockCount, int affectedEntityCount) {
        this.dimension = Objects.requireNonNullElse(dimension, "");
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.affectedBlockCount = affectedBlockCount;
        this.affectedEntityCount = affectedEntityCount;
    }

    /**
     * Returns the namespaced dimension id where the explosion occurred.
     *
     * @return non-null dimension id
     */
    @Doc("Returns the namespaced dimension id where the explosion occurred.")
    @Return("non-null dimension id")
    public String getDimension() {
        return dimension;
    }

    /**
     * Returns the explosion center X coordinate.
     *
     * @return explosion X coordinate
     */
    @Doc("Returns the explosion center X coordinate.")
    @Return("explosion X coordinate")
    public double getX() {
        return x;
    }

    /**
     * Returns the explosion center Y coordinate.
     *
     * @return explosion Y coordinate
     */
    @Doc("Returns the explosion center Y coordinate.")
    @Return("explosion Y coordinate")
    public double getY() {
        return y;
    }

    /**
     * Returns the explosion center Z coordinate.
     *
     * @return explosion Z coordinate
     */
    @Doc("Returns the explosion center Z coordinate.")
    @Return("explosion Z coordinate")
    public double getZ() {
        return z;
    }

    /**
     * Returns the explosion radius.
     *
     * @return explosion radius
     */
    @Doc("Returns the explosion radius.")
    @Return("explosion radius")
    public float getRadius() {
        return radius;
    }

    /**
     * Returns the number of affected blocks captured in this snapshot.
     *
     * @return affected block count
     */
    @Doc("Returns the number of affected blocks captured in this snapshot.")
    @Return("affected block count")
    public int getAffectedBlockCount() {
        return affectedBlockCount;
    }

    /**
     * Returns the number of affected entities captured in this snapshot.
     *
     * @return affected entity count
     */
    @Doc("Returns the number of affected entities captured in this snapshot.")
    @Return("affected entity count")
    public int getAffectedEntityCount() {
        return affectedEntityCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableExplosionEvent that)) return false;
        return Objects.equals(this.dimension, that.dimension)
                && Double.compare(this.x, that.x) == 0
                && Double.compare(this.y, that.y) == 0
                && Double.compare(this.z, that.z) == 0
                && Float.compare(this.radius, that.radius) == 0
                && this.affectedBlockCount == that.affectedBlockCount
                && this.affectedEntityCount == that.affectedEntityCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, x, y, z, radius, affectedBlockCount, affectedEntityCount);
    }

    @Override
    public String toString() {
        return "StableExplosionEvent{"
                + "dimension='" + dimension + "'"
                + ", x=" + x
                + ", y=" + y
                + ", z=" + z
                + ", radius=" + radius
                + ", affectedBlockCount=" + affectedBlockCount
                + ", affectedEntityCount=" + affectedEntityCount
                + "}";
    }
}
