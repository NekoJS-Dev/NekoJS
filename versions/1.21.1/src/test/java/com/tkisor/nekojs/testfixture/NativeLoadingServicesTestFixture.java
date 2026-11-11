package com.tkisor.nekojs.testfixture;

import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.LoadingModList;

/** Supplies the 1.21.1 loader signature without adding version checks to shared tests. */
public final class NativeLoadingServicesTestFixture {
    private NativeLoadingServicesTestFixture() {}

    /** This node supports genuine registry metadata bootstrap in the isolated test JVM. */
    public static boolean requiresIsolatedRegistryMetadata() { return true; }

    public static void initialize() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
    }
}
