package com.example.nekojs08;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ticket 08 AC6 control fixture: discovered by the same real loader path, but must be filtered out on
 * a dedicated server because it declares {@code clientOnly = true}.
 *
 * <p>If the filter regressed, the binding below would be accepted and the smoke's
 * {@code addon_client_only_filter_holds} check would fail.
 */
@RegisterNekoJSPlugin(priority = 779, clientOnly = true)
public final class AddonClientOnlyPlugin implements NekoJSPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Addon08");

    /** Must never appear on a dedicated server. */
    public static final String LEAKED_MARKER = "NEKOJS-ADDON08 client-only-filter-LEAKED";

    @Override
    public void registerBinding(BindingRegistry registry) {
        LOGGER.error("{}", LEAKED_MARKER);
        registry.register("AddonClientOnlyLeak", new AddonProbeJS());
    }
}
