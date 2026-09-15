package com.tkisor.nekojs.platform;

/**
 * Fabric 侧 {@link IModInfo}：Fabric Loader 的 mod 元数据是不可变的，
 * 所以 {@link #setName(String)} 只改本地展示名（NeoForge 侧用反射改 FML 的 displayName，
 * fabric 无对应可写字段——这是刻意的行为差异，不做反射黑魔法）。
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
