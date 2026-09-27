//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.core.NekoJSCorePlugin;
import com.tkisor.nekojs.wrapper.client.PainterJS;
import com.tkisor.nekojs.wrapper.client.ScreenRenderEventJS;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 27 GUI/render presentation-surface fixture: render, world render and screen render
 * callbacks keep reusing the existing {@code ClientEvents} events and Adapters — member
 * uniqueness, payload shapes, side filter, registration-entry vs listener-bus distinction,
 * platform type boundaries and catalog/golden consistency are all asserted through the
 * <b>production registration path</b> ({@link NekoJSCorePlugin#registerEvents} +
 * {@code registerClientEvents}) or read-only golden checks (AC2/AC3/AC6/AC8). Ordinary tests
 * only read the golden, never update it.
 */class Ticket27ClientGuiRenderSurfaceTest {

    /** Script-visible render / GUI presentation members (registration entries and listener buses). */
    private static final Set<String> RENDER_PRESENTATION_MEMBERS =
            Set.of("hud", "hudRender", "screenRender", "worldRender");

    private static Map<String, EventGroup> productionGroups() {
        EventGroupRegistry registry = new EventGroupRegistry.Impl();
        new NekoJSCorePlugin().registerEvents(registry);
        new NekoJSCorePlugin().registerClientEvents(registry);
        return registry.view();
    }

    @Test
    void renderAndScreenMembersStayInTheSingleClientEventsSurface() {
        Map<String, EventGroup> groups = productionGroups();
        EventGroup client = groups.get("ClientEvents");
        assertNotNull(client, "ClientEvents must be registered by the production client registration path");

        Set<String> members = client.viewBuses().keySet();
        assertTrue(members.containsAll(RENDER_PRESENTATION_MEMBERS),
                "the render / screen presentation members must stay in ClientEvents: " + members);
        assertTrue(members.contains("registerMenuScreens") && members.contains("registerRenderers"),
                "the native screen/renderer registration events stay in ClientEvents: " + members);

        // The catalog member is the field singleton: no duplicated bus (AC2: no second bus).
        assertSame(ClientEvents.HUD_RENDER, client.getBusHolder("hudRender").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.WORLD_RENDER, client.getBusHolder("worldRender").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.SCREEN_RENDER, client.getBusHolder("screenRender").getBus(ScriptType.CLIENT));
        assertSame(ClientEvents.HUD, client.getBusHolder("hud").getBus(ScriptType.CLIENT));

        // hudRender/worldRender stay call-to-register entries; hud/screenRender stay plain listeners.
        assertTrue(ClientEvents.HUD_RENDER instanceof RenderRegistrationBusJS,
                "ClientEvents.hudRender must stay the id-keyed renderer registration entry");
        assertTrue(ClientEvents.WORLD_RENDER instanceof RenderRegistrationBusJS,
                "ClientEvents.worldRender must stay the id-keyed renderer registration entry");
        assertFalse(RenderRegistrationBusJS.class.isInstance(ClientEvents.SCREEN_RENDER),
                "ClientEvents.screenRender is a plain per-frame listener, not a registration entry");
        assertFalse(RenderRegistrationBusJS.class.isInstance(ClientEvents.HUD),
                "ClientEvents.hud is a plain per-frame listener, not a registration entry");
        assertFalse(ClientEvents.HUD_RENDER.canDispatch() || ClientEvents.WORLD_RENDER.canDispatch(),
                "registration entries are not key-dispatched buses");

        // Payload and side filter: presentation members stay client_scripts only.
        assertEquals(PainterJS.class, ClientEvents.HUD.eventType());
        assertEquals(ScreenRenderEventJS.class, ClientEvents.SCREEN_RENDER.eventType());
        for (EventBusJS<?, ?> bus : List.of(ClientEvents.HUD, ClientEvents.HUD_RENDER,
                ClientEvents.SCREEN_RENDER, ClientEvents.WORLD_RENDER)) {
            assertEquals(ScriptType.CLIENT, bus.scriptType(),
                    bus.groupName() + "." + bus.eventName() + " must stay client_scripts only");
            assertEquals("ClientEvents", bus.groupName());
        }

        // Every bus appears exactly once across all groups (identity): render members must not be copied into a second group.
        Set<EventBusJS<?, ?>> identities = new HashSet<>();
        for (EventGroup group : groups.values()) {
            for (var holder : group.viewBuses().values()) {
                EventBusJS<?, ?> bus = holder.getBus(ScriptType.CLIENT);
                if (bus == null) bus = holder.getBus(ScriptType.SERVER);
                if (bus == null) bus = holder.getBus(ScriptType.STARTUP);
                if (bus == null) bus = holder.getBus(ScriptType.TEST);
                if (bus != null) {
                    assertTrue(identities.add(bus),
                            "bus instance shared across groups (duplicate declaration): " + group.name());
                }
            }
        }
        assertTrue(identities.containsAll(List.of(ClientEvents.HUD_RENDER, ClientEvents.WORLD_RENDER,
                ClientEvents.SCREEN_RENDER, ClientEvents.HUD)),
                "the render presentation members participate in the single registration surface");
    }

    @Test
    void sharedRenderRegistrationSurfaceCarriesNoPlatformTypes() {
        // The shared render registration surface (verbatim across 1.21.1 and 26.x) must not reference
        // MC/loader types in signatures or fields; render contexts are built version-side and passed as Object.
        assertNoTypesFrom(ClientRenderRegistry.class, "net.minecraft", "net.neoforged");
        assertNoTypesFrom(RenderRegistrationBusJS.class, "net.minecraft", "net.neoforged");

        // MC-facing presentation wrappers (Adapter side; holding MC types is their job): no loader (NeoForge) types.
        for (Class<?> wrapper : List.of(HudRenderContextJS.class, WorldRenderContextJS.class,
                ScreenRenderEventJS.class, PainterJS.class)) {
            assertNoTypesFrom(wrapper, "net.neoforged");
        }
    }

    private static void assertNoTypesFrom(Class<?> root, String... forbiddenPrefixes) {
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> todo = new HashSet<>(List.of(root));
        while (!todo.isEmpty()) {
            Class<?> current = todo.iterator().next();
            todo.remove(current);
            if (!visited.add(current) || !current.getName().startsWith(root.getPackageName())) {
                continue;
            }
            for (Method method : current.getDeclaredMethods()) {
                assertExecutableTypes(current, method, forbiddenPrefixes);
            }
            for (Constructor<?> constructor : current.getDeclaredConstructors()) {
                assertExecutableTypes(current, constructor, forbiddenPrefixes);
            }
            for (Field field : current.getDeclaredFields()) {
                assertAllowedType(current, field.getType(), "field " + field.getName(), forbiddenPrefixes);
            }
            todo.addAll(Arrays.asList(current.getDeclaredClasses()));
        }
    }

    private static void assertExecutableTypes(Class<?> owner, Executable executable, String[] forbiddenPrefixes) {
        for (Class<?> parameter : executable.getParameterTypes()) {
            assertAllowedType(owner, parameter, executable.getName() + " parameter", forbiddenPrefixes);
        }
        if (executable instanceof Method method) {
            assertAllowedType(owner, method.getReturnType(), method.getName() + " return", forbiddenPrefixes);
        }
    }

    private static void assertAllowedType(Class<?> owner, Class<?> type, String what, String[] forbiddenPrefixes) {
        for (String prefix : forbiddenPrefixes) {
            assertFalse(type.getName().startsWith(prefix),
                    owner.getSimpleName() + " " + what + " must not reference platform type " + type.getName());
        }
    }

    @Test
    void platformRenderAdapterMountsOnTheClientDistOnly() {
        // The platform Adapter (ClientRenderEvents) mounts via Dist.CLIENT: a dedicated server
        // never subscribes to the render hooks (AC3 mount-phase fact); the dispatch-thread
        // contract is carried by the shared registry javadoc and ClientReloadExecutor
        // (the CLIENT owner thread is the Render thread).
        EventBusSubscriber subscription = ClientRenderEvents.class.getAnnotation(EventBusSubscriber.class);
        assertNotNull(subscription, "the render dispatch hook must mount via EventBusSubscriber");
        assertEquals(List.of(Dist.CLIENT), Arrays.asList(subscription.value()),
                "the render dispatch hook must mount on the client dist only");
        assertEquals(NekoJS.MODID, subscription.modid(),
                "the render dispatch hook must belong to the NekoJS mod bus subscription");
    }

    @Test
    void catalogGoldenKeepsRenderMembersUniqueAndFabricExplicitlyWithoutThem() throws IOException {
        List<String[]> rows = readGoldenRows("/nekojs/platform-gates/event-surface-domains.txt");
        assertFalse(rows.isEmpty(), "the ticket-33 cross-node event surface baseline must exist");

        int clientEventsRows = 0;
        for (String[] row : rows) {
            String group = row[0], node = row[1], buses = row[2];
            boolean fabric = node.endsWith("-fabric");
            List<String> names = Arrays.asList(buses.split(","));
            if (group.equals("ClientEvents")) {
                clientEventsRows++;
                for (String member : RENDER_PRESENTATION_MEMBERS) {
                    if (fabric) {
                        assertFalse(names.contains(member),
                                "Fabric " + node + " explicitly has no GUI/render presentation member '"
                                        + member + "' (documented unavailable, no silent parity)");
                    } else {
                        assertEquals(1, names.stream().filter(member::equals).count(),
                                "ClientEvents." + member + " must be declared exactly once for " + node);
                    }
                }
            } else {
                for (String member : RENDER_PRESENTATION_MEMBERS) {
                    assertFalse(names.contains(member),
                            group + " on " + node + " must not redeclare the render presentation member '"
                                    + member + "' (single owner: ClientEvents)");
                }
            }
        }
        assertTrue(clientEventsRows >= 5, "the baseline covers all five nodes: " + clientEventsRows);
    }

    /** Golden row format: {@code <group> | <node> = present | buses=a,b,c}; comment lines start with #. */
    private static List<String[]> readGoldenRows(String resource) throws IOException {
        List<String[]> rows = new java.util.ArrayList<>();
        try (InputStream stream = Ticket27ClientGuiRenderSurfaceTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, "golden resource missing: " + resource);
            for (String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("|")) {
                    continue;
                }
                String[] parts = trimmed.split("\\|");
                if (parts.length < 3 || !parts[2].contains("buses=")) {
                    continue;
                }
                rows.add(new String[] {
                        parts[0].trim(),
                        parts[1].trim().replace("= present", "").trim(),
                        parts[2].trim().replaceFirst("(?i)buses=", "")
                });
            }
        }
        return rows;
    }
}
//?}
//?}
