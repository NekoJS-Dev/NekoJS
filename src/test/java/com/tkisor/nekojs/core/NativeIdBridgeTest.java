package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.bindings.static_access.NativeBridgeJS;
import com.tkisor.nekojs.platform.NekoIdCompat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeIdBridgeTest {
    @Test
    void classLoadingIsNotExposed() {
        assertFalse(java.util.Arrays.stream(NativeBridgeJS.class.getMethods())
                .anyMatch(method -> method.getName().equals("loadClass")));
        try (var context = graal.graalvm.polyglot.Context.newBuilder("js").allowAllAccess(true).build()) {
            context.enter();
            try {
                context.getBindings("js").putMember("nativeBridge", new com.tkisor.nekojs.core.api.BindingValueProxy(
                        context.asValue(new NativeBridgeJS()), NativeBridgeJS.class, false));
                assertEquals("undefined", context.eval("js", "typeof nativeBridge.loadClass").asString());
                assertEquals("function", context.eval("js", "typeof nativeBridge.ids.toNative").asString());
            } finally { context.leave(); }
        }
    }

    @Test
    void realProfileAdapterRoundTripsStrictIdsAndRejectsLookalikes() {
//? if neoforge {
        NekoIdCompat.Adapter adapter = new com.tkisor.nekojs.platform.NeoForgeIdCompat();
//?} else {
        /*NekoIdCompat.Adapter adapter = new com.tkisor.nekojs.platform.FabricIdCompat();
*///?}
        NekoIdCompat.init(adapter);
        var bridge = new NativeBridgeJS().getIds();
        var id = NekoId.resource("minecraft:stone");
        var nativeId = bridge.toNative(id);
        assertEquals(adapter.platformIdType(), nativeId.getClass());
        assertEquals(id, bridge.fromNative(nativeId));
        assertEquals("nekojs:stone", bridge.fromNative(bridge.toNative(NekoId.resource("stone"))).asString());
        assertThrows(IllegalArgumentException.class, () -> bridge.toNative(NekoId.of("Upper:Path")));
        assertThrows(IllegalArgumentException.class, () -> bridge.toNative(null));
        assertThrows(IllegalArgumentException.class, () -> bridge.fromNative(null));
        assertThrows(IllegalArgumentException.class, () -> NekoIdCompat.fromPlatformId("minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> NekoIdCompat.fromPlatformId(java.util.Map.of("namespace", "minecraft", "path", "stone")));
    }
}
