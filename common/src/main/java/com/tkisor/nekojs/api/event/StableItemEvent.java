package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of an item interaction, toss, or use event.
 */
@Doc("Immutable portable snapshot of an item interaction, toss, or use event.")
public final class StableItemEvent {
    private final String itemId;
    private final int count;
    private final String actorName;
    private final String dimension;

    /**
     * Creates an immutable item event snapshot.
     *
     * @param itemId namespaced item id; null is normalized to an empty string
     * @param count item stack count
     * @param actorName acting player or entity name when present; null becomes empty
     * @param dimension namespaced dimension id; null is normalized to an empty string
     */
    @Doc("Creates an immutable item event snapshot.")
    @Param(name = "itemId", value = "namespaced item id; null is normalized to an empty string")
    @Param(name = "count", value = "item stack count")
    @Param(name = "actorName", value = "acting player or entity name when present; null becomes empty")
    @Param(name = "dimension", value = "namespaced dimension id; null is normalized to an empty string")
    public StableItemEvent(String itemId, int count, String actorName, String dimension) {
        this.itemId = Objects.requireNonNullElse(itemId, "");
        this.count = count;
        this.actorName = Objects.requireNonNullElse(actorName, "");
        this.dimension = Objects.requireNonNullElse(dimension, "");
    }

    /**
     * Returns the namespaced item id.
     *
     * @return non-null item id
     */
    @Doc("Returns the namespaced item id.")
    @Return("non-null item id")
    public String getItemId() {
        return itemId;
    }

    /**
     * Returns the item stack count.
     *
     * @return item stack count
     */
    @Doc("Returns the item stack count.")
    @Return("item stack count")
    public int getCount() {
        return count;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableItemEvent that)) return false;
        return Objects.equals(this.itemId, that.itemId)
                && this.count == that.count
                && Objects.equals(this.actorName, that.actorName)
                && Objects.equals(this.dimension, that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, count, actorName, dimension);
    }

    @Override
    public String toString() {
        return "StableItemEvent{"
                + "itemId='" + itemId + "'"
                + ", count=" + count
                + ", actorName='" + actorName + "'"
                + ", dimension='" + dimension + "'"
                + "}";
    }
}
