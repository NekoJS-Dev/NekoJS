//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * NeoForge data map query helper (bound as the global {@code DataMap} and modelled on KubeJS
 * {@code DataMapWrapper} — only the built-in data maps scripts use most are moved here; query the
 * rest through {@code Registry.get(...).dataMapValue(...)}).
 *
 * <p>This is a query binding: read-only queries, no events and no lifecycle Point. The
 * MC-facing data map types ({@link NeoForgeDataMaps}) are held by this class, which is
 * neoforge-only, so the shared contract takes no MC/loader dependency (the portable counterpart,
 * {@code RegistryView.dataMapValue}, returns a JSON string).
 *
 * <p>A missing value (the entry has no data map value) returns {@code null} instead of failing, so
 * scripts can fall back with {@code ??}. The methods are read-only and never expose a mutable
 * registry view.
 *
 * <p><b>What a {@code null} argument really means</b>: a script-side {@code null}/{@code undefined}
 * is mapped to {@code ItemStack.EMPTY} by the engine value adapter ({@code ItemStackAdapter.apply},
 * which explicitly accepts {@code isNull}). So {@code DataMap.furnaceFuel(null)} takes the "missing"
 * path and <b>returns {@code null}</b> instead of failing — that is the {@code ??} fallback
 * behaviour. The null guard in {@link #itemHolder} is <b>only reachable from direct Java
 * callers</b> and not from scripts. It is kept so that Java callers do not hit a bare NPE; it is
 * not script-side behavior and must not be treated as such.
 */
public class DataMapJS {

    /** Burn time in ticks of this item as furnace fuel; returns {@code null} when it is not a fuel. */
    public Integer furnaceFuel(ItemStack stack) {
        // ItemStack in 26.x has no getItemHolder, so wrap an equivalent holder from the registry (see the
        // same comment in ItemStackExtension).
        FurnaceFuel fuel = itemHolder(stack, "furnaceFuel").getData(NeoForgeDataMaps.FURNACE_FUELS);
        return fuel == null ? null : fuel.burnTime();
    }

    /** Compost chance of this item (0.0 ~ 1.0); returns {@code null} when it is not compostable. */
    public Float compostable(ItemStack stack) {
        Compostable compostable = itemHolder(stack, "compostable").getData(NeoForgeDataMaps.COMPOSTABLES);
        return compostable == null ? null : compostable.chance();
    }

    /**
     * Argument guard: the error message carries the domain and the entry point (the shared error
     * pipeline takes the source location from the script stack); it gives no fix hint.
     *
     * <p><b>Reachability</b>: only direct Java callers reach this. A script-side {@code null} is
     * already adapted to {@code ItemStack.EMPTY} before it enters this method (see the class
     * javadoc), so this guard is not script-side behavior; it only stops a bare NPE for Java
     * callers.
     */
    private static Holder<Item> itemHolder(ItemStack stack, String entry) {
        if (stack == null) {
            throw new IllegalArgumentException("DataMap." + entry + ": stack must not be null");
        }
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem());
    }
}
//?}
