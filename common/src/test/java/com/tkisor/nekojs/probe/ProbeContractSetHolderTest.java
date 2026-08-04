package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.api.surface.ApiVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeContractSetHolderTest {
    @AfterEach
    void reset() {
        ProbeContractSetHolder.reset();
    }

    @Test
    void defaultHolderLoadsTheBundledCoreContract() {
        var contracts = ProbeContractSetHolder.contractSet();
        var portable = contracts.requirePortable("nekojs-core");

        assertEquals(ApiVersion.parse("0.13.0"), portable.identity().version());
        assertTrue(portable.contract().symbols().stream()
                .anyMatch(symbol -> symbol.id().value().equals("global:JsonIO")));
        assertTrue(portable.contract().symbols().stream()
                .anyMatch(symbol -> symbol.id().value().equals("global:NBT")));
        assertTrue(portable.contract().symbols().stream()
                .anyMatch(symbol -> symbol.id().value().equals("global:Registry")));
    }
}
