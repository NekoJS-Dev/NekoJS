package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

/**
 * Binds the generic registry entry point: the {@code register} bus hangs under the
 * {@code RegistryEvents} group, which carries the same name as the old typed entry point.
 * {@code EventGroupRegistry} merges groups with the same name, so both entry points stay live.
 * {@link NekoRegistryPointsPlugin} registers it, and the platform adapter posts it once before the
 * first registry pass.
 *
 * <pre>
 * RegistryEvents.register(event =&gt; {
 *     event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16 })
 * })
 * </pre>
 */
public final class RegistryEvents {

    /** Group name shared with the old entry point; registering into a group with the same name merges it. */
    public static final EventGroup GROUP = EventGroup.of("RegistryEvents");

    /** The single collection entry point; STARTUP scripts listen to it, and it is posted once per game start. */
    public static final EventBusJS<RegistryEventJS, Void> REGISTER = GROUP.startup("register", RegistryEventJS.class);

    private RegistryEvents() {}
}
