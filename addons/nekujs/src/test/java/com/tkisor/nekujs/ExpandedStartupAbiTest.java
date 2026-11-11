package com.tkisor.nekujs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;
import dev.latvian.mods.kubejs.script.ConsoleJS;
import dev.latvian.mods.kubejs.script.ConsoleLine;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExpandedStartupAbiTest {
    @Test
    void exposesExactFixtureStartupMemberDescriptors() throws Exception {
        var lookup = MethodHandles.publicLookup();
        for (String predicate : new String[] {"isStartup", "isServer", "isClient"}) {
            assertNotNull(lookup.findVirtual(ScriptType.class, predicate, MethodType.methodType(boolean.class)));
        }
        for (String operation : new String[] {"info", "error"}) {
            assertNotNull(lookup.findVirtual(ConsoleJS.class, operation,
                    MethodType.methodType(ConsoleLine.class, Object.class)));
        }
        assertNotNull(lookup.findVirtual(TargetedEventHandler.class, "findUniqueExtraIds",
                MethodType.methodType(Set.class, ScriptType.class)));
    }

    @Test
    void phasePredicatesIdentifyOnlyTheirOwnPhase() {
        for (ScriptType type : ScriptType.values()) {
            assertEquals(type == ScriptType.STARTUP, type.isStartup());
            assertEquals(type == ScriptType.SERVER, type.isServer());
            assertEquals(type == ScriptType.CLIENT, type.isClient());
        }
    }

    @Test
    void consoleObjectOverloadsRetainMessageMetadataAndExistingFailureOverload() {
        var console = new ConsoleJS("startup-abi-test");
        ConsoleLine info = console.info(37);
        assertEquals("37", info.message);
        assertNull(info.throwable);
        ConsoleLine error = console.error((Object) "fixture failure");
        assertEquals("fixture failure", error.message);
        assertNull(error.throwable);
        assertEquals("null", console.info(null).message);
        assertEquals("null", console.error((Object) null).message);
        var failure = new IllegalStateException("fixture cause");
        ConsoleLine caused = console.error("fixture failure", failure);
        assertEquals("fixture failure", caused.message);
        assertSame(failure, caused.throwable);
    }

    @Test
    void unusedTargetedChannelsExposeNoSelectorIdsWithoutCreatingListeners() {
        EventGroup group = EventGroup.of("StartupAbiEvents");
        TargetedEventHandler<String> handler = group.client("keyPress", () -> KubeEvent.class)
                .requiredTarget(EventTargetType.STRING);
        assertSame(handler, group.getHandlers().get("keyPress"));
        assertTrue(handler.targetRequired);
        for (ScriptType type : ScriptType.values()) {
            assertEquals(Set.of(), handler.findUniqueExtraIds(type));
        }
        assertFalse(handler.hasListeners());
        assertFalse(handler.hasListeners("key.keyboard.a"));
        assertThrows(NullPointerException.class, () -> handler.findUniqueExtraIds(null));
    }
}
