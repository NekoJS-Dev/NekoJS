package com.tkisor.nekojs.wrapper.pdata;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.LongArrayTag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S5 regression test: array getters (and the array branches of {@code get(String)})
 * must return defensive copies; mutating the returned array must not mutate the
 * underlying NBT tag and must not fire the dirty marker. The putter methods must
 * also copy their source arrays.
 */
class PersistentDataJSTest {

    @Test
    void arrayGettersReturnDefensiveCopiesAndDoNotMarkDirty() {
        CompoundTag tag = new CompoundTag();
        tag.put("bytes", new ByteArrayTag(new byte[] {1, 2, 3}));
        tag.put("ints", new IntArrayTag(new int[] {4, 5, 6}));
        tag.put("longs", new LongArrayTag(new long[] {7L, 8L, 9L}));

        AtomicInteger dirty = new AtomicInteger();
        PersistentDataJS pdata = new PersistentDataJS(() -> tag, t -> {}, dirty::incrementAndGet, () -> {});

        byte[] bytes = pdata.getByteArray("bytes");
        bytes[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, ((ByteArrayTag) tag.get("bytes")).getAsByteArray());

        int[] ints = pdata.getIntArray("ints");
        ints[0] = 99;
        assertArrayEquals(new int[] {4, 5, 6}, ((IntArrayTag) tag.get("ints")).getAsIntArray());

        long[] longs = pdata.getLongArray("longs");
        longs[0] = 99L;
        assertArrayEquals(new long[] {7L, 8L, 9L}, ((LongArrayTag) tag.get("longs")).getAsLongArray());

        byte[] bytesViaGet = (byte[]) pdata.get("bytes");
        bytesViaGet[1] = 88;
        int[] intsViaGet = (int[]) pdata.get("ints");
        intsViaGet[1] = 88;
        long[] longsViaGet = (long[]) pdata.get("longs");
        longsViaGet[1] = 88L;

        assertArrayEquals(new byte[] {1, 2, 3}, ((ByteArrayTag) tag.get("bytes")).getAsByteArray());
        assertArrayEquals(new int[] {4, 5, 6}, ((IntArrayTag) tag.get("ints")).getAsIntArray());
        assertArrayEquals(new long[] {7L, 8L, 9L}, ((LongArrayTag) tag.get("longs")).getAsLongArray());

        assertEquals(0, dirty.get(), "array reads must not fire the dirty marker");
    }

    @Test
    void arrayPutsCopySourceArrays() {
        CompoundTag tag = new CompoundTag();
        AtomicInteger dirty = new AtomicInteger();
        PersistentDataJS pdata = new PersistentDataJS(() -> tag, t -> {}, dirty::incrementAndGet, () -> {});

        byte[] bytes = {1, 2, 3};
        int[] ints = {4, 5, 6};
        long[] longs = {7L, 8L, 9L};
        pdata.putByteArray("bytes", bytes);
        pdata.putIntArray("ints", ints);
        pdata.putLongArray("longs", longs);

        bytes[0] = 99;
        ints[0] = 99;
        longs[0] = 99L;

        assertArrayEquals(new byte[] {1, 2, 3}, ((ByteArrayTag) tag.get("bytes")).getAsByteArray());
        assertArrayEquals(new int[] {4, 5, 6}, ((IntArrayTag) tag.get("ints")).getAsIntArray());
        assertArrayEquals(new long[] {7L, 8L, 9L}, ((LongArrayTag) tag.get("longs")).getAsLongArray());
    }
}
