//? if neoforge {
// NeoForge-side facade: its signatures reference NeoForge-only types (PayloadRegistrar), so the
// fabric node does not compile this file (fabric's network channel uses FabricPlayNetwork's own
// implementation).
package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ServiceLoader;

/**
 * Version facade for symbol differences shared by both sides (client and server). It covers three
 * kinds of differences:
 * <ul>
 *   <li>dist check: {@code FMLEnvironment.getDist()} in 26.x, the {@code FMLEnvironment.dist} field
 *       in 1.21.1;</li>
 *   <li>OP check: {@code Commands.LEVEL_GAMEMASTERS.check(permissions())} in 26.x,
 *       {@code Player#hasPermissions(2)} in 1.21.1;</li>
 *   <li>bidirectional registration of NekoScriptPayload: {@code playBidirectional} takes 4
 *       arguments in 26.x (one handler per direction) and only 3 in 1.21.1 (the handler tells the
 *       direction from the flow), so the whole registration moves down into the implementation.</li>
 * </ul>
 * The implementations live in the per-version modules ({@code Nf1211PlatformCompat} /
 * {@code Nf261PlatformCompat} / {@code Nf262PlatformCompat}, deliberately with different class
 * names because the drift comparison forbids the same name with a different body) and are resolved
 * by ServiceLoader through {@code META-INF/services} on first use.
 *
 * <p>The signatures only reference types that exist on both sides and load on both sides;
 * client-only symbols go through {@link McClientCompat}.
 */
public final class McPlatformCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McPlatformCompat.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No McPlatformCompat.Impl provider on classpath: expected a per-version impl "
                            + "(META-INF/services)"));

    private McPlatformCompat() {}

    public static Impl get() {
        return IMPL;
    }

    public interface Impl {
        /**
         * Whether the current process is on the client side (26.x: {@code FMLEnvironment.getDist()};
         * 1.21.1: {@code FMLEnvironment.dist}).
         */
        boolean isClientDist();

        /**
         * Whether the player is a game master (26.x: {@code Commands.LEVEL_GAMEMASTERS.check};
         * 1.21.1: {@code hasPermissions(2)}).
         */
        boolean isGameMaster(Player player);

        /**
         * Bidirectional registration of NekoScriptPayload; the whole registration shape moves down into each
         * version's implementation.
         */
        void registerScriptPayload(PayloadRegistrar registrar);

        /**
         * Registers the single dynamic registry payload for configuration and play on supported nodes.
         * The default remains inert for 1.21.1; Fabric has its own explicit networking subset.
         * NekoJSNetwork calls this once at assembly, never during reload or connection handling.
         */
        default void registerDynamicSyncPayload(PayloadRegistrar registrar) {
        }
    }
}
//?}
