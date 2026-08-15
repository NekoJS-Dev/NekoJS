package com.tkisor.nekojs.wrapper.pdata;

import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S5 cleanroom regression test: array getters (and the array branches of
 * {@code get(String)}) must return defensive copies; mutating the returned array
 * must not mutate the underlying 1.12.2 NBT tag and must not fire the dirty marker.
 * The putter methods must also copy their source arrays.
 */
class PersistentDataJSTest {

    @Test
    void arrayGettersReturnDefensiveCopiesAndDoNotMarkDirty() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("bytes", new NBTTagByteArray(new byte[] {1, 2, 3}));
        tag.setTag("ints", new NBTTagIntArray(new int[] {4, 5, 6}));

        AtomicInteger dirty = new AtomicInteger();
        PersistentDataJS pdata = new PersistentDataJS(() -> tag, t -> {}, dirty::incrementAndGet, () -> {});

        byte[] bytes = pdata.getByteArray("bytes");
        bytes[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3}, ((NBTTagByteArray) tag.getTag("bytes")).getByteArray());

        int[] ints = pdata.getIntArray("ints");
        ints[0] = 99;
        assertArrayEquals(new int[] {4, 5, 6}, ((NBTTagIntArray) tag.getTag("ints")).getIntArray());

        byte[] bytesViaGet = (byte[]) pdata.get("bytes");
        bytesViaGet[1] = 88;
        int[] intsViaGet = (int[]) pdata.get("ints");
        intsViaGet[1] = 88;

        assertArrayEquals(new byte[] {1, 2, 3}, ((NBTTagByteArray) tag.getTag("bytes")).getByteArray());
        assertArrayEquals(new int[] {4, 5, 6}, ((NBTTagIntArray) tag.getTag("ints")).getIntArray());

        assertEquals(0, dirty.get(), "array reads must not fire the dirty marker");
    }

    @Test
    void arrayPutsCopySourceArrays() {
        NBTTagCompound tag = new NBTTagCompound();
        AtomicInteger dirty = new AtomicInteger();
        PersistentDataJS pdata = new PersistentDataJS(() -> tag, t -> {}, dirty::incrementAndGet, () -> {});

        byte[] bytes = {1, 2, 3};
        int[] ints = {4, 5, 6};
        pdata.putByteArray("bytes", bytes);
        pdata.putIntArray("ints", ints);

        bytes[0] = 99;
        ints[0] = 99;

        assertArrayEquals(new byte[] {1, 2, 3}, ((NBTTagByteArray) tag.getTag("bytes")).getByteArray());
        assertArrayEquals(new int[] {4, 5, 6}, ((NBTTagIntArray) tag.getTag("ints")).getIntArray());
    }
}
