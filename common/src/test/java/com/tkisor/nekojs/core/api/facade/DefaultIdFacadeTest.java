package com.tkisor.nekojs.core.api.facade;

import com.tkisor.nekojs.api.data.NekoId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultIdFacadeTest {
    private final DefaultIdFacade facade = new DefaultIdFacade();

    @Test
    void exposesPortableIdOperations() {
        NekoId id = facade.of("minecraft", "stone");
        assertEquals("minecraft", facade.namespace(id));
        assertEquals("stone", facade.path(id));
        assertEquals("minecraft:stone", facade.asString(id));
    }

    @Test
    void doesNotAcceptNullValues() {
        assertThrows(NullPointerException.class, () -> facade.of((String) null));
        assertThrows(NullPointerException.class, () -> facade.asString(null));
    }
}
