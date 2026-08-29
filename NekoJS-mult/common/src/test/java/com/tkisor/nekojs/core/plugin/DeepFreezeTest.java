package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.data.BindingRegistry;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.script.ScriptTypedValue;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeepFreezeTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    @Test
    void frozenBindingSnapshotIsDetachedFromSource() {
        var typed = ScriptTypedValue.<BindingRegistry>of(BindingRegistry.BindingRegistryImpl::new);
        var serverReg = typed.at(ScriptType.SERVER);
        serverReg.register("before_freeze", "v1");

        Map<ScriptType, Map<String, com.tkisor.nekojs.api.data.Binding>> snapshot =
                NekoPluginBootstrap.freezeBindings(typed);

        serverReg.register("after_freeze", "v2");

        assertTrue(snapshot.get(ScriptType.SERVER).containsKey("before_freeze"));
        assertFalse(snapshot.get(ScriptType.SERVER).containsKey("after_freeze"),
                "frozen snapshot must not reflect post-freeze registrations");
    }

    @Test
    void frozenBindingSnapshotIsImmutable() {
        var typed = ScriptTypedValue.<BindingRegistry>of(BindingRegistry.BindingRegistryImpl::new);
        typed.at(ScriptType.CLIENT).register("x", 1);

        Map<ScriptType, Map<String, com.tkisor.nekojs.api.data.Binding>> snapshot =
                NekoPluginBootstrap.freezeBindings(typed);

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.get(ScriptType.CLIENT).put("y", null));
    }

    @Test
    void frozenEventGroupRejectsNewBuses() {
        var group = EventGroup.of("test_group");
        group.freeze();

        assertThrows(IllegalStateException.class,
                () -> group.add("bus2", ScriptType.SERVER, null),
                "frozen EventGroup must reject new bus registration");
    }
}
