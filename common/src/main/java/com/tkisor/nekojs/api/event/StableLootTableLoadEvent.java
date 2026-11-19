package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;

import java.util.Objects;

/**
 * Immutable portable snapshot of a loot table load notification.
 */
@Doc("Immutable portable snapshot of a loot table load notification.")
public final class StableLootTableLoadEvent {
    private final String tableId;

    /**
     * Creates an immutable loot table load event snapshot.
     *
     * @param tableId namespaced loot table id; null is normalized to an empty string
     */
    @Doc("Creates an immutable loot table load event snapshot.")
    @Param(name = "tableId", value = "namespaced loot table id; null is normalized to an empty string")
    public StableLootTableLoadEvent(String tableId) {
        this.tableId = Objects.requireNonNullElse(tableId, "");
    }

    /**
     * Returns the namespaced loot table id being loaded.
     *
     * @return non-null loot table id
     */
    @Doc("Returns the namespaced loot table id being loaded.")
    @Return("non-null loot table id")
    public String getTableId() {
        return tableId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StableLootTableLoadEvent that)) return false;
        return Objects.equals(this.tableId, that.tableId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId);
    }

    @Override
    public String toString() {
        return "StableLootTableLoadEvent{" + "tableId='" + tableId + "'" + "}";
    }
}
