package com.tkisor.nekojs.fabric.capability;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedSlottedStorage;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Item slots using Fabric's native snapshots and explicit owner-controlled persistence. */
public final class FabricItemStorage extends CombinedSlottedStorage<ItemVariant, SingleItemStorage> {
    public FabricItemStorage(int size, Runnable onChange) {
        super(createSlots(size, onChange));
    }

    public void writeValue(ValueOutput output) {
        FabricStoragePersistence.requireCommittedSave();
        for (int slot = 0; slot < parts.size(); slot++) {
            parts.get(slot).writeValue(output.child("slot_" + slot));
        }
    }

    public void readValue(ValueInput input) {
        FabricStoragePersistence.requireLoadOutsideTransaction();
        Objects.requireNonNull(input);
        List<SlotContents> loaded = new ArrayList<>(parts.size());
        for (int slot = 0; slot < parts.size(); slot++) {
            String key = "slot_" + slot;
            ValueInput saved = input.child(key).orElseThrow(() ->
                    new IllegalArgumentException("[NEKO-4024] Saved item slot is missing or invalid: " + key));
            loaded.add(readSlot(saved, key));
        }
        for (int slot = 0; slot < loaded.size(); slot++) {
            parts.get(slot).variant = loaded.get(slot).variant();
            parts.get(slot).amount = loaded.get(slot).amount();
        }
    }

    private record SlotContents(ItemVariant variant, long amount) {}

    private static SlotContents readSlot(ValueInput input, String key) {
        ItemVariant savedVariant = FabricStoragePersistence.readRequired(input, "variant", ItemVariant.CODEC);
        long savedAmount = FabricStoragePersistence.readRequired(input, "amount", Codec.LONG);
        if (savedAmount < 0 || savedAmount > slotCapacity(savedVariant)
                || savedVariant.isBlank() != (savedAmount == 0)) {
            throw new IllegalArgumentException("[NEKO-4024] Saved item slot is outside its configured bounds: " + key);
        }
        return new SlotContents(savedVariant, savedAmount);
    }

    private static long slotCapacity(ItemVariant variant) {
        return variant.isBlank() ? 64 : variant.toStack().getMaxStackSize();
    }

    private static List<SingleItemStorage> createSlots(int size, Runnable onChange) {
        if (size < 0) {
            throw new IllegalArgumentException("[NEKO-4012] Item slot count must be non-negative: " + size);
        }
        Objects.requireNonNull(onChange);
        List<SingleItemStorage> slots = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            slots.add(new SingleItemStorage() {
                @Override
                protected long getCapacity(ItemVariant variant) {
                    return slotCapacity(variant);
                }

                @Override
                public void readValue(ValueInput input) {
                    FabricStoragePersistence.requireLoadOutsideTransaction();
                    SlotContents saved = readSlot(input, "slot");
                    variant = saved.variant();
                    amount = saved.amount();
                }

                @Override
                public void writeValue(ValueOutput output) {
                    FabricStoragePersistence.requireCommittedSave();
                    super.writeValue(output);
                }

                @Override
                protected void onFinalCommit() {
                    onChange.run();
                }
            });
        }
        return List.copyOf(slots);
    }
}
