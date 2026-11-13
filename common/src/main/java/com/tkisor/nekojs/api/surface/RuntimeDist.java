package com.tkisor.nekojs.api.surface;

/**
 * Runtime distribution: physical client, dedicated server, or integrated server.
 *
 * <p>{@link EnvironmentKey#current} currently maps {@code Platform.isClient()} to two
 * values: client to {@link #CLIENT}, everything else to {@link #DEDICATED_SERVER}.
 * {@link #INTEGRATED_SERVER} is reserved and takes no part in that mapping yet.
 */
public enum RuntimeDist {
    /** Physical client. */
    CLIENT,
    /** Dedicated server (every non-client runtime is currently mapped to this value). */
    DEDICATED_SERVER,
    /** Integrated server (embedded in a single-player world). Reserved; not part of the
     * mapping yet. */
    INTEGRATED_SERVER
}
