package com.tkisor.nekojs.platform;

/**
 * Fabric-side {@link IModInfo}: Fabric Loader mod metadata is immutable, so
 * {@link #setName(String)} only changes the local display name. The NeoForge side rewrites FML's
 * displayName by reflection; Fabric has no writable field for it. That difference is intentional,
 * and no reflection tricks are used.
 */
public final class FabricModInfo implements IModInfo {

    private final String id;
    private final String version;
    private String name;

    public FabricModInfo(String id, String name, String version) {
        this.id = id;
        this.name = name;
        this.version = version;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getCustomName() {
        return name;
    }
}
