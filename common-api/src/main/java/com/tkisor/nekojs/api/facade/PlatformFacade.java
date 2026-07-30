package com.tkisor.nekojs.api.facade;

import java.util.List;

public interface PlatformFacade {
    boolean isClient();

    boolean isDevelopment();

    String getMcVersion();

    String getLoaderId();

    String getLoaderVersion();

    boolean isLoaded(String modId);

    ModInfoValue getInfo(String modId);

    List<String> getList();

    List<String> capabilities();
}
