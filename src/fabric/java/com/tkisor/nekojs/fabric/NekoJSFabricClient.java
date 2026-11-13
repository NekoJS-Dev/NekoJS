package com.tkisor.nekojs.fabric;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.client.BrowseCommand;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import com.tkisor.nekojs.client.renderer.NekoEntityRenderers;
import com.tkisor.nekojs.fabric.event.FabricClientEventBindings;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entry: loads CLIENT scripts and wires the client event bridge (currently only
 * tick).
 *
 * <p>This mirrors {@code NekoJSClient} on NeoForge. There, CLIENT scripts load and the client
 * bus is wired inside a mod event (client setup, before resources are ready). Here the fabric
 * bridge registers in {@code ClientModInitializer} and script loading hangs off
 * {@code CLIENT_STARTED} (after the initial resource reload). See
 * {@link FabricClientEventBindings} for the timing difference.
 */
public final class NekoJSFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NekoJS-Fabric");

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("nekojs")
                        .then(ClientCommands.literal("browse")
                                .executes(context -> BrowseCommand.open()))));
        LOGGER.info("NekoJS fabric client entrypoint reached.");
        registerScriptEntityRenderers();
        Runnable refreshPackResources = com.tkisor.nekojs.fabric.resource.FabricClientPackResources.register(
                NekoJSFabricMod::runtimeRootOrNull);
        FabricPackSync.registerClient(refreshPackResources);
        FabricPlayNetwork.registerClient();
        FabricPDataSync.registerClient();
        // Script key bindings: the register entry plus pressed/released/tick polling through
        // ClientTickEvents.
        com.tkisor.nekojs.bindings.event.client.KeyBindEvents.register();
        FabricClientEventBindings.register(() -> {
            // The root comes from the loader entry's package-private accessor, because the loader
            // instantiates the fabric entrypoint reflectively and cannot inject it. If assembly is
            // not finished, skip as before.
            var root = NekoJSFabricMod.runtimeRootOrNull();
            if (root == null) {
                LOGGER.error("CLIENT_STARTED: runtime root not assembled yet, skipping CLIENT script load");
                return;
            }
            root.load(ScriptType.CLIENT);
        });
        LOGGER.info("NekoJS fabric client bindings registered (CLIENT scripts load at CLIENT_STARTED).");
    }

    /** Registers the configured visible renderer for every startup entity type. */
    @SuppressWarnings("deprecation")
    private static void registerScriptEntityRenderers() {
        EntityTypeBuilder.registeredEntityTypes().forEach(type ->
                EntityRenderers.register(type, NekoEntityRenderers.provider(EntityTypeBuilder.renderConfiguration(type))));
    }
}
