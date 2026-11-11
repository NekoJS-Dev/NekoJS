package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.ScriptNativeBridge;
import com.tkisor.nekojs.api.data.Binding;
import com.tkisor.nekojs.api.data.BindingRegistry.BindingRegistryImpl;
import net.minecraft.nekojstest.NativeToken;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModulePublicTypesTest {
    public interface Portable { String name(); }
    public interface Leaked { NativeToken nativeValue(); }
    public interface GenericLeak { List<NativeToken> nativeValues(); }
    public interface NestedLeak { Leaked child(); }
    public interface GenericNestedLeak { List<Leaked> children(); }
    @ScriptNativeBridge public interface Bridge { NativeToken nativeValue(); }

    @Test void stableNativeLeaksFailUnlessExplicitlyClassifiedAsABridge() {
        assertDoesNotThrow(() -> ModulePublicTypes.validateStable(Portable.class, false));
        for (Class<?> type : List.of(Leaked.class, GenericLeak.class, NestedLeak.class, GenericNestedLeak.class, NativeToken.class)) {
            var failure = assertThrows(IllegalArgumentException.class, () -> ModulePublicTypes.validateStable(type, false));
            assertTrue(failure.getMessage().contains("NEKO-4057"));
        }
        assertDoesNotThrow(() -> ModulePublicTypes.validateStable(Bridge.class, false));
    }

    @Test void failedStableRegistrationDoesNotPublishButVersionRegistrationIsAllowed() {
        var registry = new BindingRegistryImpl(ScriptType.SERVER).scopedTo("test");
        var binding = Binding.of("Leaked", null, Leaked.class);
        assertThrows(IllegalArgumentException.class, () -> registry.register(binding, expose -> expose.stable()));
        assertTrue(registry.moduleBindings().isEmpty());
        assertTrue(registry.register(binding, expose -> expose.versioned()));
    }
}
