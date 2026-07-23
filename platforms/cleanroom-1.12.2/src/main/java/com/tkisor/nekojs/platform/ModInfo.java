package com.tkisor.nekojs.platform;

public class ModInfo implements IModInfo {
    private final String id;
    private String name;
    private final String version;

    public ModInfo(String id) {
        this(id, id, "unknown");
    }

    public ModInfo(String id, String name, String version) {
        this.id = id;
        this.name = name != null ? name : id;
        this.version = version != null ? version : "unknown";
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
    public String getVersion() {
        return version;
    }

    @Override
    public String getCustomName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }
}
