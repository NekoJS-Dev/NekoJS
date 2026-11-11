package com.tkisor.nekojs.api.data;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModuleBindingRegistryTest {
    @BeforeAll static void initialize() { TestPlatformInit.ensureInitialized(); }

    @Test void moduleTargetsAreIndependentAndNeverBecomeGlobals() {
        var root = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);
        var engine = root.scopedTo("nekojs");
        var addon = root.scopedTo("example");
        engine.register(Binding.of("Value", "stable"), e -> e.stable());
        engine.register(Binding.of("Value", "native"), e -> e.versioned());
        addon.register(Binding.of("Value", "addon"), e -> e.stable());
        engine.register(Binding.of("Value", "global"));
        assertEquals("global", root.viewRegistered().get("Value").value());
        assertEquals(java.util.List.of("nekojs/api/stable", "nekojs/api/version", "nekojs/api/stable/example"),
                root.moduleBindings().stream().map(ModuleBinding::moduleId).toList());
        assertThrows(UnsupportedOperationException.class, () -> root.moduleBindings().clear());
    }

    @Test void invalidEsmExportNamesFailBeforePublishing() {
        var registry = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER).scopedTo("example");
        for (String name : java.util.List.of("default", "namespace", "class", "await", "__neko_module", "not-an-id")) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> registry.register(Binding.of(name, 1), expose -> expose.versioned()));
            assertTrue(failure.getMessage().contains("NEKO-4057"));
        }
        assertTrue(registry.moduleBindings().isEmpty());
    }

    @Test void invalidTargetsAndDuplicatesFailWithoutPublishing() {
        var root = new BindingRegistry.BindingRegistryImpl(ScriptType.SERVER);
        var registry = root.scopedTo("example");
        var binding = Binding.of("Value", "value");
        assertThrows(IllegalArgumentException.class, () -> root.register(binding, e -> e.stable()));
        assertThrows(IllegalArgumentException.class, () -> registry.register(binding, e -> {}));
        assertThrows(IllegalArgumentException.class, () -> registry.register(binding, e -> e.stable().versioned()));
        assertThrows(IllegalArgumentException.class, () -> registry.register(binding, e -> e.stable().stable()));
        assertTrue(root.moduleBindings().isEmpty());
        registry.register(binding, e -> e.stable());
        assertThrows(IllegalArgumentException.class, () -> registry.register(binding, e -> e.stable()));
        assertEquals(1, root.moduleBindings().size());
        assertTrue(root.viewRegistered().isEmpty());
    }
}
