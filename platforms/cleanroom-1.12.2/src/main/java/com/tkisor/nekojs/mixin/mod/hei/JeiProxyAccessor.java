package com.tkisor.nekojs.mixin.mod.hei;

import mezz.jei.api.IModPlugin;
import mezz.jei.gui.textures.Textures;
import mezz.jei.startup.JeiStarter;
import mezz.jei.startup.ProxyCommonClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for JEI/HEI's client proxy, exposing its private {@code plugins},
 * {@code starter}, and {@code textures} fields so {@link com.tkisor.nekojs.client.HeiRefresher}
 * can rebuild the recipe panel after a recipe hot-reload.
 *
 * <p>Lives in the {@code mixin.mod.hei} sub-package on purpose: {@link com.tkisor.nekojs.ModMixinConfigPlugin}
 * gates any mixin whose 6th package segment is {@code "hei"} on {@code Loader.isModLoaded("jei")},
 * so this accessor is only applied when HEI/JEI is present. Without that gate the mixin would
 * crash on clients that don't have HEI installed (target class {@code ProxyCommonClient} absent).
 *
 * <p>{@code remap = false} because HEI ships already-mapped (no SRG remap needed).
 * Pattern adapted from GroovyScript's {@code JeiProxyAccessor}.
 */
@Mixin(value = ProxyCommonClient.class, remap = false)
public interface JeiProxyAccessor {

    @Accessor
    List<IModPlugin> getPlugins();

    @Accessor
    JeiStarter getStarter();

    @Accessor
    Textures getTextures();
}
