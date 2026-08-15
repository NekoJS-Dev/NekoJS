package com.tkisor.nekojs.core.node;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NekoNodeBufferTest {

    @Test
    void allocRejectsSizesAboveMaxAllocBytes() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> NekoNodeBuffer.alloc(NekoNodeBuffer.MAX_ALLOC_BYTES + 1));
        assertTrue(error.getMessage().contains("" + NekoNodeBuffer.MAX_ALLOC_BYTES),
                "message should name the configured cap");
    }

    @Test
    void checkAllocSizeAllowsAtLimitAndRejectsAbove() {
        assertDoesNotThrow(() -> NekoNodeBuffer.checkAllocSize(NekoNodeBuffer.MAX_ALLOC_BYTES));
        assertThrows(IllegalArgumentException.class,
                () -> NekoNodeBuffer.checkAllocSize(NekoNodeBuffer.MAX_ALLOC_BYTES + 1L));
    }

    @Test
    void sliceSharesBackingMemoryWithOriginal() {
        NekoNodeBuffer original = NekoNodeBuffer.fromBytes(new byte[]{1, 2, 3, 4, 5});
        NekoNodeBuffer slice = original.slice(1, 4);

        slice.set(0, 20);
        assertEquals(20, original.get(1), "write through slice must be visible in original");

        original.set(2, 30);
        assertEquals(30, slice.get(1), "write through original must be visible in slice");
    }

    @Test
    void sliceClampsNegativeAndOutOfRangeIndices() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{1, 2, 3, 4, 5});

        assertEquals(0, buffer.slice(4, 1).length(), "start > end clamps to empty");
        assertEquals(5, buffer.slice(-100, 100).length(), "both sides out of range clamps to full view");
        assertEquals(1, buffer.slice(3, -1).length(), "negative end counts from the end");
        assertEquals(2, buffer.slice(0, -3).length(), "slice(0, -3) keeps the first two bytes");
        assertEquals(0, buffer.slice(-2, 3).length(), "negative start past clamped end is empty");
        assertEquals(5, buffer.slice(0, 100).length(), "end beyond length clamps to length");
    }

    @Test
    void indexOfEmptyNeedleReturnsClampedOffset() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{1, 2, 3, 4, 5});
        NekoNodeBuffer empty = NekoNodeBuffer.fromBytes(new byte[0]);

        assertEquals(0, buffer.indexOf(empty, -10));
        assertEquals(3, buffer.indexOf(empty, 3));
        assertEquals(5, buffer.indexOf(empty, 100));
    }

    @Test
    void indexOfNonEmptyNeedleClampsNegativeFromIndexAndIsViewRelative() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{'a', 'b', 'c', 'a', 'b', 'c'});
        NekoNodeBuffer slice = buffer.slice(2, 6); // view: c a b c
        NekoNodeBuffer a = NekoNodeBuffer.fromBytes(new byte[]{'a'});

        assertEquals(1, slice.indexOf(a, 0), "search must be view-relative: 'a' is at view index 1");
        assertEquals(1, slice.indexOf(a, -5), "negative fromIndex clamps to 0 for a non-empty needle");
        assertEquals(-1, slice.indexOf(NekoNodeBuffer.fromBytes(new byte[]{'a', 'b', 'c', 'a'}), 0),
                "'abca' exists in the backing array but not in the view");
    }

    @Test
    void includesIsViewRelative() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{'a', 'b', 'c', 'a', 'b', 'c'});
        NekoNodeBuffer slice = buffer.slice(2, 6); // view: c a b c

        assertFalse(slice.includes(NekoNodeBuffer.fromBytes(new byte[]{'a', 'b', 'c', 'a'})),
                "'abca' is only present before the view");
        assertTrue(slice.includes(NekoNodeBuffer.fromBytes(new byte[]{'c', 'a'})));
        assertTrue(slice.includes(NekoNodeBuffer.fromBytes(new byte[]{'a', 'b'})));
    }

    @Test
    void bytesReturnsDefensiveCopy() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{1, 2, 3});
        byte[] copy = buffer.bytes();
        copy[0] = 99;

        assertEquals(1, buffer.get(0), "mutating bytes() result must not affect the buffer");
    }

    @Test
    void toStringEncodesOnlyTheView() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromString("Hello, World!", "utf8");
        NekoNodeBuffer slice = buffer.slice(7, 12); // "World"

        assertEquals("World", slice.toString("utf8"));
        assertEquals("World", slice.toString());
        assertEquals("576f726c64", slice.toString("hex"));
        assertEquals(Base64.getEncoder().encodeToString("World".getBytes(StandardCharsets.UTF_8)),
                slice.toString("base64"));
    }

    @Test
    void fillIsViewRelative() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0});
        NekoNodeBuffer slice = buffer.slice(2, 5); // view: backing[2..4]

        slice.fill(9, 1, 3); // fill view indices 1..2 -> backing indices 3..4

        assertEquals(0, buffer.get(0));
        assertEquals(0, buffer.get(2));
        assertEquals(9, buffer.get(3));
        assertEquals(9, buffer.get(4));
        assertEquals(0, buffer.get(5));
    }

    @Test
    void fillThrowsForNegativeStartOrEndAndEndBeyondLength() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0});

        assertThrows(IllegalArgumentException.class, () -> buffer.fill(1, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> buffer.fill(1, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> buffer.fill(1, 0, 5));
        assertEquals(0, buffer.get(0), "failed fills must not modify the buffer");
    }

    @Test
    void fillStartAfterEndAndStartEqualsEndAreNoOps() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0});

        assertSame(buffer, buffer.fill(1, 3, 2), "start > end is a no-op and returns this");
        assertSame(buffer, buffer.fill(2, 2, 2), "start == end is a no-op and returns this");
        for (int i = 0; i < buffer.length(); i++) {
            assertEquals(0, buffer.get(i), "no-op fill must not modify bytes: index " + i);
        }
    }

    @Test
    void copyIsViewRelative() {
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        NekoNodeBuffer sourceSlice = source.slice(2, 7); // view: {2,3,4,5,6}
        NekoNodeBuffer target = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0});

        int copied = sourceSlice.copy(target, 1, 1, 3); // view[1..2] = {3,4} -> target[1..2]

        assertEquals(2, copied);
        assertEquals(0, target.get(0));
        assertEquals(3, target.get(1));
        assertEquals(4, target.get(2));
        assertEquals(0, target.get(3));
    }

    @Test
    void copyReturnsSourceAvailableCountForNullTargetWithoutTouchingAnything() {
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{0, 1, 2, 3, 4});

        assertEquals(2, source.copy(null, 0, 1, 3));
        assertEquals(0, source.copy(null, 0, 3, 1), "sourceStart > sourceEnd is a no-op even for a null target");
        assertThrows(IllegalArgumentException.class, () -> source.copy(null, 0, -2, 100),
                "negative sourceStart must throw even for a null target");
    }

    @Test
    void copyThrowsForNegativeTargetStart() {
        NekoNodeBuffer backing = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer target = backing.slice(2, 6); // view: backing[2..5]
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{10, 20, 30});

        assertThrows(IllegalArgumentException.class, () -> source.copy(target, -2, 0, 3));
        for (int i = 0; i < backing.length(); i++) {
            assertEquals(0, backing.get(i), "no bytes may be written when targetStart is negative: index " + i);
        }
    }

    @Test
    void copyThrowsForNegativeSourceStartOrEnd() {
        NekoNodeBuffer backing = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer target = backing.slice(2, 6);
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{10, 20, 30});

        assertThrows(IllegalArgumentException.class, () -> source.copy(target, 0, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> source.copy(target, 0, 1, -1));
        for (int i = 0; i < backing.length(); i++) {
            assertEquals(0, backing.get(i), "no bytes may be written for invalid source ranges: index " + i);
        }
    }

    @Test
    void copySourceStartAfterSourceEndIsNoOpReturningZero() {
        NekoNodeBuffer backing = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer target = backing.slice(2, 6);
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{10, 20, 30});

        int copied = source.copy(target, 0, 3, 1);

        assertEquals(0, copied);
        for (int i = 0; i < backing.length(); i++) {
            assertEquals(0, backing.get(i), "sourceStart > sourceEnd must write nothing: index " + i);
        }
    }

    @Test
    void copyThrowsWhenSourceStartBeyondSourceLength() {
        NekoNodeBuffer backing = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer target = backing.slice(2, 6);
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{10, 20, 30});

        assertThrows(IllegalArgumentException.class, () -> source.copy(target, 0, 4, 5));
        for (int i = 0; i < backing.length(); i++) {
            assertEquals(0, backing.get(i), "sourceStart beyond source length must write nothing: index " + i);
        }
    }

    @Test
    void copyClampsTargetStartBeyondSliceViewAndWritesNothing() {
        NekoNodeBuffer backing = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer target = backing.slice(2, 6); // view length 4
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{10, 20, 30});

        int copied = source.copy(target, 100, 0, 3);

        assertEquals(0, copied);
        for (int i = 0; i < backing.length(); i++) {
            assertEquals(0, backing.get(i), "no bytes may be written when targetStart is beyond the view: index " + i);
        }
    }

    @Test
    void copyCapsCountByTargetSliceRemainder() {
        NekoNodeBuffer backing = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer target = backing.slice(2, 6); // view: backing[2..5], length 4
        NekoNodeBuffer source = NekoNodeBuffer.fromBytes(new byte[]{10, 20, 30, 40, 50, 60, 70, 80});

        int copied = source.copy(target, 2, 0, 8);

        assertEquals(2, copied);
        assertEquals(10, backing.get(4));
        assertEquals(20, backing.get(5));
        assertEquals(0, backing.get(2), "target view bytes before targetStart must not be modified");
        assertEquals(0, backing.get(3), "target view bytes before targetStart must not be modified");
        assertEquals(0, backing.get(6), "bytes after the target view must not be modified");
        assertEquals(0, backing.get(7), "bytes after the target view must not be modified");
    }

    @Test
    void equalsAndCompareUseViewRange() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{1, 2, 3, 4, 5});
        NekoNodeBuffer slice = buffer.slice(1, 4); // view: {2,3,4}

        assertTrue(slice.equals(NekoNodeBuffer.fromBytes(new byte[]{2, 3, 4})));
        assertEquals(0, slice.compare(NekoNodeBuffer.fromBytes(new byte[]{2, 3, 4})));
        assertFalse(slice.equals(NekoNodeBuffer.fromBytes(new byte[]{1, 2, 3, 4, 5})));
    }

    @Test
    void multiByteReadHelpersAreViewRelative() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        NekoNodeBuffer slice = buffer.slice(2, 10); // view: {2,3,4,5,6,7,8,9}

        assertEquals(0x05040302L, slice.readUInt32LE(0));
        assertEquals(0x02030405L, slice.readUInt32BE(0));
        assertEquals(0x0302, slice.readUInt16LE(0));
        assertEquals(0x0203, slice.readUInt16BE(0));
        assertEquals(2, slice.readUInt8(0));
        assertEquals(2, slice.readInt8(0));
    }

    @Test
    void multiByteWriteHelpersAreViewRelative() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[]{0, 0, 0, 0, 0, 0, 0, 0});
        NekoNodeBuffer slice = buffer.slice(2, 6); // view: backing[2..5]

        slice.writeUInt32BE(0, 0x01020304L);

        assertEquals(0x01020304L, buffer.readUInt32BE(2));
        assertEquals(1, buffer.get(2));
        assertEquals(2, buffer.get(3));
        assertEquals(3, buffer.get(4));
        assertEquals(4, buffer.get(5));
        assertEquals(0, buffer.get(1));
        assertEquals(0, buffer.get(6));
    }

    @Test
    void readDoubleAndFloatHelpersAreViewRelative() {
        NekoNodeBuffer buffer = NekoNodeBuffer.fromBytes(new byte[16]);
        NekoNodeBuffer slice = buffer.slice(4, 12); // view: backing[4..11]

        slice.writeDoubleBE(0, Math.PI);
        assertEquals(Math.PI, buffer.readDoubleBE(4));

        slice.writeDoubleLE(0, Math.E);
        assertEquals(Math.E, buffer.readDoubleLE(4));

        slice.writeFloatLE(0, 1.5f);
        assertEquals(1.5f, buffer.readFloatLE(4));

        slice.writeFloatBE(0, 2.5f);
        assertEquals(2.5f, buffer.readFloatBE(4));
    }
}
