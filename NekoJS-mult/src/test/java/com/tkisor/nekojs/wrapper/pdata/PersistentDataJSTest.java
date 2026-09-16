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

    /**
     * 票 18 AC2 前半：写入触发 dirty 标记、读操作不触发；{@code sync()} 只触发 syncer
     * （绕过脏队列的立即同步入口），不带 dirty。生产装配里 dirty → PDataSyncService.markDirty
     * （下一 server tick flush），syncer → syncNow——语义在 PersistentDataJS 这一层钉住。
     */
    @Test
    void writesFireDirtyMarkerButReadsAndExplicitSyncDoNot() {
        CompoundTag tag = new CompoundTag();
        AtomicInteger dirty = new AtomicInteger();
        AtomicInteger synced = new AtomicInteger();
        PersistentDataJS pdata = new PersistentDataJS(() -> tag, t -> {}, dirty::incrementAndGet, synced::incrementAndGet);

        pdata.putInt("mana", 7);
        assertEquals(1, dirty.get(), "a write must fire the dirty marker exactly once");
        assertEquals(0, synced.get(), "plain writes must not bypass the dirty queue");

        // 读面：getters/contains/copyTag 一概不触发 dirty 或 syncer
        pdata.getInt("mana");
        pdata.getString("missing");
        pdata.getBoolean("missing");
        pdata.getCompound("missing");
        pdata.contains("mana");
        pdata.copyTag();
        assertEquals(1, dirty.get(), "reads must not fire the dirty marker");
        assertEquals(0, synced.get(), "reads must not fire the syncer");

        // 批量事务与清空同样各记一次 dirty
        pdata.edit(t -> t.putInt("hp", 3));
        assertEquals(2, dirty.get());
        pdata.clear();
        assertEquals(3, dirty.get());

        // 显式 sync()：只触发 syncer，不重复记 dirty
        pdata.sync();
        assertEquals(1, synced.get(), "sync() must invoke the immediate-sync hook");
        assertEquals(3, dirty.get(), "sync() must not fire the dirty marker");
    }
}
