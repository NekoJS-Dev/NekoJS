package com.tkisor.nekojs.api.facade;

import java.util.List;

/**
 * Platform information facade, exposed to scripts as the global object {@code Platform}.
 *
 * <p>It reports the runtime environment (logical client or server, development or not), the loader,
 * the Minecraft version and read-only information about loaded mods. Every method returns only
 * primitive types or {@link ModInfoValue}; Minecraft native objects are never exposed.
 */
public interface PlatformFacade {
    /** Whether the current process is the logical client. */
    boolean isClient();

    /** Whether the game runs in a development or debugging environment. */
    boolean isDevelopment();

    /** Current Minecraft version string, for example {@code "1.21.1"}. */
    String getMcVersion();

    /** Current mod loader id, for example {@code "neoforge"} or {@code "forge"}. */
    String getLoaderId();

    /** Current mod loader version string. */
    String getLoaderVersion();

    /** Whether the given modId is loaded; a {@code null} or blank modId throws. */
    boolean isLoaded(String modId);

    /** Returns the metadata for the given modId, or {@code null} if that mod is not loaded. */
    ModInfoValue getInfo(String modId);

    /** Ids of all loaded mods, sorted alphabetically. */
    List<String> getList();

    /**
     * Capability ids this platform supports: lowercase, underscores replaced by hyphens, sorted
     * alphabetically.
     */
    List<String> capabilities();
}
