package com.tkisor.nekojs.platform;

import net.neoforged.fml.ModList;
import java.lang.reflect.Field;

public class ModInfo implements IModInfo {
    private static final Field DISPLAY_NAME_FIELD;

    static {
        Field field = null;
        try {
            field = net.neoforged.fml.loading.moddiscovery.ModInfo.class.getDeclaredField("displayName");
            field.setAccessible(true);
        } catch (Exception e) {
        }
        DISPLAY_NAME_FIELD = field;
    }

    private final String id;
    private String name;
    private String version;
    private String customName;

    public ModInfo(String i) {
        id = i;
        name = id;
        version = "0.0.0";
        customName = "";
    }

    public ModInfo(String id, String name, String version) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.customName = "";
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getName() { return name; }

    @Override
    public String getVersion() { return version; }

    @Override
    public String getCustomName() { return customName; }

    @Override
    public void setName(String newName) {
        this.name = newName;
        this.customName = newName;

        if (DISPLAY_NAME_FIELD != null) {
            try {
                ModList.get().getModContainerById(id).ifPresent(container -> {
                    Object info = container.getModInfo();
                    if (info instanceof net.neoforged.fml.loading.moddiscovery.ModInfo fmlInfo) {
                        try {
                            DISPLAY_NAME_FIELD.set(fmlInfo, newName);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}