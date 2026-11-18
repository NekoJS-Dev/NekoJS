package com.tkisor.nekojs.core.api;

import org.jetbrains.annotations.ApiStatus;

/** Identifies engine-owned module namespaces before package resolution or plugin source execution. */
@ApiStatus.Internal
public final class ApiModuleNamespace {
    private ApiModuleNamespace() {}

    public static boolean isReserved(String id) {
        return id.equals("nekojs/api") || id.startsWith("nekojs/api/")
                || id.equals("nekojs/plugins") || id.startsWith("nekojs/plugins/")
                || id.equals("nekojs:event") || id.startsWith("nekojs:event/");
    }
}
