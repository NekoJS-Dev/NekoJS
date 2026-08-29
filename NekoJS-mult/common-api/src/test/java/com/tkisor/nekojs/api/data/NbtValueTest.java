package com.tkisor.nekojs.api.data;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbtValueTest {
    @Test
    void arraysAreDefensiveAndListsRequireOneElementKind() {
        byte[] source = {1, 2};
        NbtValue.ByteArrayValue value = NbtValue.byteArray(source);
        source[0] = 9;
        assertArrayEquals(new byte[] {1, 2}, value.values());

        assertThrows(IllegalArgumentException.class,
                () -> NbtValue.list(List.of(NbtValue.intValue(1), NbtValue.string("mixed"))));
        assertEquals(NbtValue.Kind.END, NbtValue.list(List.of()).elementKind());
    }

    @Test
    void compoundsPreserveOrderAndCannotBeMutated() {
        LinkedHashMap<String, NbtValue> source = new LinkedHashMap<>();
        source.put("first", NbtValue.intValue(1));
        source.put("second", NbtValue.string("two"));
        NbtValue.CompoundValue compound = NbtValue.compound(source);

        assertEquals(List.of("first", "second"), compound.values().keySet().stream().toList());
        assertThrows(UnsupportedOperationException.class,
                () -> compound.values().put("third", NbtValue.intValue(3)));
    }
}
