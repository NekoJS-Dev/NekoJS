package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IPlatform {
    boolean isClient();

    boolean isDevelopment();

    /** Immutable startup opt-in from the engine config; platform adapters share their early loader snapshot. */
    default boolean isMixinJsEnabled() { return false; }

    default String getCurrentThreadName() {
        return Thread.currentThread().getName();
    }

    String getMcVersion();

    default int getMcVersionInt() {
        String versionStr = getMcVersion();
        if ("unknown".equals(versionStr)) return 0;

        try {
            String cleanVersion = versionStr.split("-")[0].split("\\+")[0];
            String[] parts = cleanVersion.split("\\.");

            int major = 0;
            int minor = 0;
            int patch = 0;

            if (parts.length >= 1) major = Integer.parseInt(parts[0]);
            if (parts.length >= 2) minor = Integer.parseInt(parts[1]);
            if (parts.length >= 3) patch = Integer.parseInt(parts[2]);

            // How the number is computed:
            // 1.21.1 -> 1 * 1000 + 21 * 10 + 1 = 1211
            // 26.1.2 -> 26 * 100 + 1 * 10 + 2 = 2612
            // 26.1   -> 26 * 100 + 1 * 10 + 0 = 2610

            if (major >= 10) {
                return major * 100 + minor * 10 + patch;
            } else {
                return major * 1000 + minor * 10 + patch;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    Path getGameDir();

    Map<String, IModInfo> getMods();

    /** Content paths from mods already admitted by the loader; adapters never evaluate scripts. */
    default Map<String, Path> loadedPackRoots() { return Map.of(); }
    default void visitLoadedPackRoots(java.util.function.BiConsumer<String, Path> visitor) {
        loadedPackRoots().forEach(visitor);
    }

    /** Plan resources from mods already admitted by the native loader; never scans a scripts directory. */
    default void visitLoadedMixinPlans(java.util.function.BiConsumer<String, java.net.URI> visitor) {}

    IModInfo getInfo(String modID);

    default boolean isLoaded(String modId) {
        return getMods().containsKey(modId);
    }

    default Set<String> getList() {
        return getMods().keySet();
    }

    default Set<PlatformCapability> capabilities() {
        return Set.of();
    }

    default NbtBinaryCodec nbtBinaryCodec() {
        return NbtBinaryCodec.unsupported();
    }

    /** Read-only registry query service; returns {@code null} when the platform does not support
     * it, and script-side {@code Registry} queries then return empty results. */
    default RegistryQueryService registryQueryService() {
        return null;
    }

    /** Loader identifier, e.g. "neoforge" or "fabric". */
    String getLoaderId();

    /** Loader version string, e.g. "4.0.0". */
    String getLoaderVersion();

    /**
     * Java package prefixes that probe type generation scans by default. Only the MC/loader
     * packages of the platform itself: {@code java}, {@code com.mojang} and
     * {@code com.tkisor.nekojs} are always appended by the probe config layer. A platform
     * overrides this to expose its own packages.
     */
    default List<String> defaultScanPackages() {
        return List.of("net.minecraft");
    }

    /**
     * Recipe schema field kind to the Java package of its value class, used for imports in the
     * probe recipes declaration. NeoForge 1.21+ uses {@code net.minecraft.world.item[.crafting]};
     * 1.12.2 overrides it to {@code net.minecraft.item[.crafting]}. Returns {@code null} when the
     * kind has no class mapping. The package must match the real class: otherwise the same alias
     * ($ItemStack_/$Ingredient_) in the generated file is imported twice from two modules, TS
     * reports Duplicate identifier and the whole recipes declaration fails.
     */
    default String recipeFieldKindPackage(RecipeFieldKind kind) {
        return switch (kind) {
            case INGREDIENT -> "net.minecraft.world.item.crafting";
            case ITEM_STACK -> "net.minecraft.world.item";
            case FLUID_STACK -> "net.neoforged.neoforge.fluids";
            case FLUID_INGREDIENT, SIZED_FLUID_INGREDIENT -> "net.neoforged.neoforge.fluids.crafting";
            default -> null;
        };
    }
}
