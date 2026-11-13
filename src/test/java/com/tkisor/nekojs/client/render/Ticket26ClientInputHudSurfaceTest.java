//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.bindings.event.client.KeyBindEvents;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input and HUD script surface fixture: member names, member types, and the difference between a
 * registration entry and an event listener. Everything is built through the <b>production
 * registration path</b> ({@link NekoJSCorePlugin#registerEvents} + {@code registerClientEvents}) so
 * the {@link EventGroupRegistry} is real, and asserted against the real bus metadata.
 *
 * <p>Why it exists: {@code KeyBindEvents.register(...)} and {@code ClientEvents.hudRender(...)}
 * hang off event groups but register on call (they create a KeyMapping directly, or register a
 * resident renderer by id). That is one word away from the listener buses next to them. If someone
 * turns them into plain listeners (or the other way round), the script side changes silently, so
 * this fixture turns the difference into assertions.
 *
 * <p>Assertions only use the public surface: group member names, bus instance identity,
 * {@code eventType()}, {@code canDispatch()} and the runtime type. There is no parallel copy of the
 * member table.
 */
class Ticket26ClientInputHudSurfaceTest {

    /** Script-visible member names must be lowerCamelCase; the uppercase Java constant is only an internal locator. */
    private static final Pattern LOWER_CAMEL = Pattern.compile("[a-z][A-Za-z0-9]*");

    private static Map<String, EventGroup> productionGroups() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl().scopedTo("nekojs");
        new NekoJSCorePlugin().registerEvents(registry);
        new NekoJSCorePlugin().registerClientEvents(registry);
        return registry.view();
    }

    @Test
    void scriptVisibleMemberNamesAreLowerCamelCase() {
        EventGroup keyBinds = productionGroups().get("KeyBindEvents");
        EventGroup client = productionGroups().get("ClientEvents");
        assertNotNull(keyBinds,
                "KeyBindEvents must be registered by the production client registration path");
        assertNotNull(client,
                "ClientEvents must be registered by the production client registration path");

        for (EventGroup group : new EventGroup[] {keyBinds, client}) {
            for (String member : group.viewBuses().keySet()) {
                assertTrue(LOWER_CAMEL.matcher(member).matches(),
                        group.name() + "." + member + " is not a lowerCamelCase script member name"
                                + " (the Java field name is an internal locator, not the script name)");
            }
        }

        // The input member surface is compared as an exact set, not by substring-matching a string built
        // from the same set: a substring check would accept registerKeyMappings for
        // contains("register") and distinguish nothing.
        assertEquals(Set.of("pressed", "released", "tick", "register"),
                new TreeSet<>(keyBinds.viewBuses().keySet()),
                "the input group's script-visible members changed");
        assertTrue(client.viewBuses().keySet().containsAll(Set.of("hud", "hudRender", "registerKeyMappings")),
                "the HUD / key-mapping members must stay in ClientEvents: " + client.viewBuses().keySet());
    }

    @Test
    void registrationEntriesAreNotListenerBuses() {
        // register and hudRender are EventBusJS subclasses on the group that override execute to register.
        assertTrue(KeyBindEvents.REGISTER instanceof KeyBindEvents.RegisterBus,
                "KeyBindEvents.register must stay the direct binding-creation entry, not a listener bus");
        assertTrue(ClientEvents.HUD_RENDER instanceof RenderRegistrationBusJS,
                "ClientEvents.hudRender must stay the id-keyed renderer registration entry");
        assertFalse(KeyBindEvents.REGISTER.canDispatch(),
                "a registration entry is not key-dispatched");
        assertFalse(ClientEvents.HUD_RENDER.canDispatch(),
                "a registration entry is not key-dispatched");

        // The listener buses next to them stay plain listeners: hud is the per-frame PainterJS listener,
        // not a registration entry.
        assertFalse(RenderRegistrationBusJS.class.isInstance(ClientEvents.HUD),
                "ClientEvents.hud is the per-frame HUD listener bus, not the renderer registration entry");
        assertEquals(PainterJS.class, ClientEvents.HUD.eventType(),
                "ClientEvents.hud still delivers the documented PainterJS payload");
        assertFalse(ClientEvents.HUD.canDispatch(),
                "ClientEvents.hud is a plain (non-dispatched) listener bus");

        // pressed, released and tick remain listener buses dispatched by binding id.
        for (EventBusJS<KeyBindEvents.KeyBindEventJS, String> bus
                : java.util.List.of(KeyBindEvents.PRESSED, KeyBindEvents.RELEASED, KeyBindEvents.TICK)) {
            assertTrue(bus.canDispatch(),
                    "KeyBindEvents." + bus.eventName() + " must stay a dispatch bus (id-keyed subscriptions)");
            assertEquals(KeyBindEvents.KeyBindEventJS.class, bus.eventType(),
                    "the trigger buses deliver the KeyBindEventJS payload");
            assertEquals("KeyBindEvents", bus.groupName());
        }
    }

    @Test
    void productionRegistrationExposesTheDocumentedMembersOnce() {
        Map<String, EventGroup> groups = productionGroups();

        assertEquals(Set.of("pressed", "released", "tick", "register"),
                new TreeSet<>(groups.get("KeyBindEvents").viewBuses().keySet()),
                "the input group's script-visible member set changed");

        Set<String> clientMembers = groups.get("ClientEvents").viewBuses().keySet();
        assertTrue(clientMembers.containsAll(Set.of("hud", "hudRender", "registerKeyMappings")),
                "this ticket's HUD / key-mapping members must stay in ClientEvents: " + clientMembers);

        // The catalog member is the field singleton, not a copied bus.
        assertSame(ClientEvents.HUD,
                groups.get("ClientEvents").getBusHolder("hud").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.HUD_RENDER,
                groups.get("ClientEvents").getBusHolder("hudRender").getBus(ScriptType.CLIENT));
        assertSame(KeyBindEvents.REGISTER,
                groups.get("KeyBindEvents").getBusHolder("register").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.REGISTER_KEY_MAPPINGS,
                groups.get("ClientEvents").getBusHolder("registerKeyMappings").getBus(ScriptType.CLIENT));

        // Client only: these members are visible to client_scripts and have no server-side form.
        for (EventBusJS<?, ?> bus : java.util.List.of(ClientEvents.HUD, ClientEvents.HUD_RENDER,
                ClientEvents.REGISTER_KEY_MAPPINGS, KeyBindEvents.REGISTER, KeyBindEvents.PRESSED)) {
            assertEquals(ScriptType.CLIENT, bus.scriptType(),
                    bus.groupName() + "." + bus.eventName() + " must stay client_scripts only");
        }
    }

    @Test
    void inputHudSurfaceDoesNotDuplicateOtherDomains() {
        Map<String, EventGroup> groups = productionGroups();

        // Each bus appears exactly once across all groups, by identity: these members must not be copied
        // into a second group or catalog.
        Set<EventBusJS<?, ?>> identities = new java.util.HashSet<>();
        for (EventGroup group : groups.values()) {
            for (var holder : group.viewBuses().values()) {
                EventBusJS<?, ?> bus = holder.getBus(ScriptType.CLIENT);
                if (bus == null) bus = holder.getBus(ScriptType.SERVER);
                if (bus != null) {
                    assertTrue(identities.add(bus),
                            "bus instance shared across groups (duplicate declaration): " + group.name());
                }
            }
        }
        assertTrue(identities.contains(ClientEvents.HUD_RENDER) && identities.contains(KeyBindEvents.REGISTER),
                "this ticket's entries participate in the single registration surface");

        // No second PostEffects or Assets group is declared: those surfaces keep their own owners and are
        // not copied here.
        assertFalse(groups.containsKey("PostEffectEvents"),
                "post-effect declarations stay in ClientEvents.postEffects (ticket 28 owner)");
        assertFalse(groups.containsKey("AssetsEvents"),
                "asset declarations stay in ClientEvents.generateAssets/lang (ticket 29 owner)");
        for (String groupName : groups.keySet()) {
            assertFalse(groupName.startsWith("PostEffect"),
                    "no second post-effect group: " + groupName);
        }
    }
}
//?}
//?}
