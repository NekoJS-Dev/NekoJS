package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

/**
 * Event facade for dynamic registration at server runtime. Its lifetime and semantics are fully
 * separate from the startup-time {@code RegistryEvents.register}: this bus fires when the server
 * registry first becomes ready and during the candidate phase of every transactional script/data
 * reload. Script callbacks only submit inert candidate plans; they never perform a registry
 * mutation right away on the script thread.
 *
 * <h2>JS API (SERVER scripts)</h2>
 * <pre>
 * DynamicRegistryEvents.dynamicRegistry(event => {
 *     event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' })
 *     event.soundEvent('mymod:boom', b => { b.fixedRange(16) })
 *     event.mobEffect('mymod:wither_touch', b => { b.category('harmful').color(0x8B0000) })
 * })
 * </pre>
 *
 * <p><b>Naming</b>: the spec keeps the dynamic registry and lets it use a server runtime dynamic
 * registration event facade under the working name {@code ServerEvents.dynamicRegistry}, leaving
 * the exact event name, payload, full Builder method set and open registry types to be frozen
 * later. This implementation uses a separate group, {@code DynamicRegistryEvents}. ServerEvents
 * lives in the NeoForge-only tree, and the fabric side is another file with the same FQCN, so
 * attaching the runtime facade there would tie its separate lifetime to a loader-specific group
 * and would keep the common layer from hosting or testing it. A separate group mirrors the
 * startup {@code RegistryEvents}, which is also its own group, and expresses the two lifetimes.
 * The typed entry points keep the frozen names {@code event.item/soundEvent/mobEffect}. This
 * decision feeds the group's declaration/contract derivation and the migration table
 * (baseline MIGRATION.md).
 */
public final class DynamicRegistryEvents {

    /** Group name. It is separate from ServerEvents; see the class comment for why. */
    public static final EventGroup GROUP = EventGroup.of("DynamicRegistryEvents");

    /** The only collection bus, listened to by SERVER scripts. The payload is a ProxyObject whose
     *  member directory is frozen to the three typed entry points. */
    public static final EventBusJS<DynamicRegistryEventJS, Void> DYNAMIC_REGISTRY =
            GROUP.server("dynamicRegistry", DynamicRegistryEventJS.class);

    private DynamicRegistryEvents() {}
}
