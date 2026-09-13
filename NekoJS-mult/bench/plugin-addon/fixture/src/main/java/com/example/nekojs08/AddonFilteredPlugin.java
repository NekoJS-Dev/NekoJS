package com.example.nekojs08;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ticket 08 AC6 control fixture: this class is discovered by the SAME real loader path as
 * {@link AddonPlugin}, but must be filtered out by {@code NekoJSBasePluginManager} because
 * {@code requiredMods} names a mod that is never loaded on the smoke server.
 *
 * <p>If the filter regressed, the binding below would be accepted and the smoke's
 * {@code addon_required_mods_filter_holds} check would fail.
 */
@RegisterNekoJSPlugin(priority = 778, requiredMods = {"nekojs08-absent-mod"})
public final class AddonFilteredPlugin implements NekoJSPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Addon08");

    /** Must never appear in the log: its presence means the requiredMods filter let it through. */
    public static final String LEAKED_MARKER = "NEKOJS-ADDON08 required-mods-filter-LEAKED";

    @Override
    public void registerBinding(BindingRegistry registry) {
        LOGGER.error("{}", LEAKED_MARKER);
        registry.register("AddonFilteredLeak", new AddonProbeJS());
    }
}
