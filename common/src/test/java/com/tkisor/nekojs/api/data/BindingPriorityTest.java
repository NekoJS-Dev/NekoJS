package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingPriorityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void secondRegistrationDoesNotOverwrite() {
        var reg = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);
        assertTrue(reg.register("dup", "first"), "first registration should succeed");
        assertFalse(reg.register("dup", "second"), "duplicate registration must not overwrite");
        var view = reg.viewRegistered();
        assertEquals("first", view.get("dup").value(),
                "original value must be retained after rejected duplicate");
    }

    @Test
    void differentNamesBothRegister() {
        var reg = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);
        assertTrue(reg.register("a", 1));
        assertTrue(reg.register("b", 2));
        assertEquals(2, reg.viewRegistered().size());
    }
}
