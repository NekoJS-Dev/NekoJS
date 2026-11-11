//? if neoforge {
package com.tkisor.nekojs.testfixture;

/** Modern loading services belong to the active FML Loader, not bare-JVM fixtures. */
public final class NativeLoadingServicesTestFixture {
    private NativeLoadingServicesTestFixture() {}

    public static boolean requiresIsolatedRegistryMetadata() { return false; }

    public static void initialize() {
        // An active modern loader supplies its own loading list; isolated tests must not fabricate one.
    }
}
//?}
