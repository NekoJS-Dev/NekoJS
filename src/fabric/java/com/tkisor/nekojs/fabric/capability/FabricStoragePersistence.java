package com.tkisor.nekojs.fabric.capability;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.level.storage.ValueInput;

import java.util.Objects;

final class FabricStoragePersistence {
    private FabricStoragePersistence() {}

    static void requireLoadOutsideTransaction() {
        if (Transaction.isOpen()) {
            throw new IllegalStateException("[NEKO-4027] Storage cannot be loaded during a transfer transaction");
        }
    }

    static void requireCommittedSave() {
        Transaction.Lifecycle lifecycle = Transaction.getLifecycle();
        if (lifecycle == Transaction.Lifecycle.OPEN || lifecycle == Transaction.Lifecycle.CLOSING) {
            throw new IllegalStateException("[NEKO-4027] Uncommitted storage cannot be saved during a transfer transaction");
        }
    }

    static <T> T readRequired(ValueInput input, String key, Codec<T> codec) {
        Objects.requireNonNull(input);
        return input.read(key, strict(codec)).orElseThrow(() ->
                new IllegalArgumentException("[NEKO-4024] Saved storage field is missing or invalid: " + key));
    }

    static <T> Codec<T> strict(Codec<T> codec) {
        return new Codec<>() {
            @Override
            public <Encoded> DataResult<Pair<T, Encoded>> decode(DynamicOps<Encoded> ops, Encoded input) {
                DataResult<Pair<T, Encoded>> result = codec.decode(ops, input);
                return result.error().<DataResult<Pair<T, Encoded>>>map(error -> DataResult.error(error::message))
                        .orElse(result);
            }

            @Override
            public <Encoded> DataResult<Encoded> encode(T input, DynamicOps<Encoded> ops, Encoded prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }
}
