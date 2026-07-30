package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultNbtFacadeTest {
    private final DefaultNbtFacade nbt = new DefaultNbtFacade(
            java.nio.file.Path.of("."), NbtBinaryCodec.unsupported());

    @Test
    void rendersDeterministicSnbtAndPreservesExplicitWidths() {
        LinkedHashMap<String, NbtValue> values = new LinkedHashMap<>();
        values.put("count", nbt.byteValue(2));
        values.put("long", nbt.longValue("9223372036854775807"));
        values.put("items", NbtValue.list(List.of(NbtValue.intValue(1), NbtValue.intValue(2))));

        assertEquals("{count:2b,long:9223372036854775807l,items:[1,2]}", nbt.toSnbt(NbtValue.compound(values)));
        assertEquals("9223372036854775807", nbt.scalar(nbt.longValue("9223372036854775807")));
        assertEquals("[B;1B,-2B]", nbt.toSnbt(nbt.byteArray(List.of(1, -2))));
        assertEquals("\"line1\nline2\"", nbt.toSnbt(NbtValue.string("line1\nline2")));
    }

    @Test
    void rejectsOutOfRangeExplicitWidths() {
        assertThrows(ApiInvocationException.class, () -> nbt.byteValue(128));
        assertThrows(ApiInvocationException.class, () -> nbt.longValue("not-a-long"));
        assertThrows(ApiInvocationException.class, () -> nbt.floatValue(3.5e38));
    }
}
