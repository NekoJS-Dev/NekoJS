package net.neoforged.fml;

import java.util.List;

/**
 * Dedicated evaluator-test JVM arrangement, not loader lifecycle initialization.
 * This calls the real loader's package-private index builder with no containers.
 * It must never be packaged or used by a running Minecraft instance.
 */
public final class NekuJSModListTestFixture {
    private NekuJSModListTestFixture() {
    }

    public static void initializeAbsentOptionalMods() {
        if (ModList.get() != null) {
            throw new IllegalStateException("NEKO-NEKU-PROOF-LOADER: requires an isolated JVM without an existing ModList");
        }
        ModList.of(List.of(), List.of()).setLoadedMods(List.of());
    }
}
