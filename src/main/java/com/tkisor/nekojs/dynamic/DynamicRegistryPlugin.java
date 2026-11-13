// TODO(fabric): there is no Fabric implementation yet; remove this whole-file guard once the port is done
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroupRegistry;
import com.tkisor.nekojs.core.dynamic.facade.DynamicRegistryEvents;
import com.tkisor.nekojs.core.dynamic.plan.DynamicBuilderSurfaces;
import com.tkisor.nekojs.core.plugin.EventsPoint;
import com.tkisor.nekojs.core.plugin.TypeDocsPoint;

/**
 * Registers the runtime dynamic-registry event facade ({@link DynamicRegistryEvents#GROUP}:
 * inert candidate plans and typed callback builders, a lifecycle separate from startup
 * {@code RegistryEvents}) plus the derived dynamic-builder declaration entries. The platform
 * assembly ({@code NekoJSMod}) registers the facade's candidate domain collector into the
 * runtime root, not this plugin, because the root does not exist yet during plugin bootstrap.
 */
@RegisterNekoJSPlugin
public final class DynamicRegistryPlugin implements NekoJSPlugin,
        EventsPoint.Contributor,
        TypeDocsPoint.Contributor {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        // The platform installs the root-owned collector after plugin bootstrap.
        registry.register(DynamicRegistryEvents.GROUP);
    }

    @Override
    public void registerTypeDocs(com.tkisor.nekojs.core.plugin.TypeDocsRegister registry) {
        // The same builder contract supplies runtime members, fingerprints and declarations.
        DynamicBuilderSurfaces.derive().forEach(registry::registerRegistryBuilderSurface);
    }
}
//?}
//?}
