package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.mixin.mod.hei.JeiProxyAccessor;
import mezz.jei.Internal;
import mezz.jei.JustEnoughItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.gui.textures.Textures;
import mezz.jei.startup.JeiStarter;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;

import java.util.List;

/**
 * Refreshes JEI/HEI's recipe panel after NekoJS hot-reloads recipes.
 *
 * <p>HEI is a {@code modCompileOnly}+{@code modRuntimeOnly} dependency, so this class
 * references {@code mezz.jei.*} directly and reaches the private fields of
 * {@code ProxyCommonClient} through the {@link JeiProxyAccessor} mixin (no reflection).
 * Mirrors GroovyScript's {@code ReloadableRegistryManager.reloadJei}: invoke
 * {@link JeiStarter#load} (HEI's recipe-only reload), fall back to {@link JeiStarter#start}
 * (vanilla JEI has no {@code load}), then re-block removed ingredients via
 * {@code Internal.getIngredientFilter().block()} (HEI fix so removals survive the rebuild).
 *
 * <p>Must run on the client render thread. {@link #scheduleRefresh} hops from the
 * integrated-server command thread to the client thread via
 * {@link Minecraft#addScheduledTask}. Deliberately not {@code @SideOnly}: client classes are
 * only resolved when a method actually executes, which never happens on a dedicated server
 * (callers gate on {@code Side.CLIENT}).
 */
public final class HeiRefresher {
    private HeiRefresher() {}

    /** HEI registers under the JEI modid, so this detects both JEI and HEI. */
    public static boolean isAvailable() {
        return Loader.isModLoaded("jei");
    }

    /**
     * Schedule the refresh on the client render thread. Safe to call from the integrated
     * server command thread (single-player shares one JVM).
     */
    public static void scheduleRefresh(boolean recipesOnly) {
        Minecraft.getMinecraft().addScheduledTask(() -> refreshOnClient(recipesOnly));
    }

    private static void refreshOnClient(boolean recipesOnly) {
        if (!isAvailable()) return;
        long t0 = System.currentTimeMillis();
        try {
            // getProxy() returns ProxyCommon; runtime instance is ProxyCommonClient, which
            // JeiProxyAccessor is mixed into. Double-cast via Object to satisfy the compiler.
            JeiProxyAccessor proxy = (JeiProxyAccessor) JustEnoughItems.getProxy();
            List<IModPlugin> plugins = proxy.getPlugins();
            JeiStarter starter = proxy.getStarter();
            Textures textures = proxy.getTextures();

            boolean usedHei;
            try {
                starter.load(plugins, textures, recipesOnly); // HEI: recipe-only reload
                usedHei = true;
            } catch (NoSuchMethodError | AbstractMethodError fallback) {
                starter.start(plugins, textures); // vanilla JEI: full restart
                usedHei = false;
            }
            NekoJS.LOGGER.info("JEI/HEI recipe panel refreshed (mode={}, recipesOnly={}, {}ms)",
                    usedHei ? "HEI load" : "JEI start", recipesOnly, System.currentTimeMillis() - t0);

            // HEI: re-apply the removed-ingredient blacklist so script removals stick.
            Internal.getIngredientFilter().block();
        } catch (Throwable t) {
            NekoJS.LOGGER.warn("JEI/HEI refresh failed", t);
        }
    }
}
