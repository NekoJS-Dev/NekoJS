package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface StageLootDrop {

    @Nullable NekoId nekojs$takeLootTableId();

    static Consumer<ItemStack> withTableId(Consumer<ItemStack> consumer, NekoId tableId) {
        return stack -> {
            Origin previous = Origin.CURRENT.get();
            Origin.CURRENT.set(new Origin(stack, tableId));
            try {
                consumer.accept(stack);
            } finally {
                if (previous == null) {
                    Origin.CURRENT.remove();
                } else {
                    Origin.CURRENT.set(previous);
                }
            }
        };
    }

    static @Nullable NekoId identify(ItemStack stack) {
        Origin origin = Origin.CURRENT.get();
        return origin != null && origin.stack() == stack ? origin.tableId() : null;
    }

    record Origin(ItemStack stack, NekoId tableId) {
        private static final ThreadLocal<Origin> CURRENT = new ThreadLocal<>();
    }
}
