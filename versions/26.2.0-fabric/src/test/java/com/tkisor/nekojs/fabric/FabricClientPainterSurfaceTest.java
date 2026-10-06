package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import com.tkisor.nekojs.wrapper.client.ScreenRenderEventJS;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fabric Painter surface and adapter source trace for the 26.x client nodes. */
class FabricClientPainterSurfaceTest {
    @Test
    void painterEventsAreClientOnlyAndCarryTheSharedPayloads() {
        EventGroup group = FabricClientEventBindings.CLIENT_EVENTS;
        EventBusJS<?, ?> hud = group.getBusHolder("hud").getBus(ScriptType.CLIENT);
        EventBusJS<?, ?> screen = group.getBusHolder("screenRender").getBus(ScriptType.CLIENT);

        assertEquals(PainterJS.class, hud.eventType());
        assertEquals(ScreenRenderEventJS.class, screen.eventType());
        assertEquals(ScriptType.CLIENT, hud.scriptType());
        assertEquals(ScriptType.CLIENT, screen.scriptType());
    }

    @Test
    void painterAdapterUsesNativeFabricRenderCallbacks() throws Exception {
        Path source = Path.of("src/fabric/java/com/tkisor/nekojs/fabric/event/FabricClientEventBindings.java");
        if (!Files.exists(source)) {
            source = Path.of("../../src/fabric/java/com/tkisor/nekojs/fabric/event/FabricClientEventBindings.java");
        }
        String code = Files.readString(source);

        assertTrue(code.contains("HudElementRegistry.attachElementAfter"),
                "HUD Painter must be mounted through Fabric's real HUD layer API");
        assertTrue(code.contains("ScreenEvents.AFTER_INIT"),
                "screen Painter must be mounted through Fabric screen lifecycle");
        assertTrue(code.contains("ScreenEvents.afterExtract"),
                "screen Painter must run after native screen extraction");
        assertTrue(code.contains("new PainterJS(graphics"),
                "native graphics must be wrapped instead of silently ignored");
    }
}
