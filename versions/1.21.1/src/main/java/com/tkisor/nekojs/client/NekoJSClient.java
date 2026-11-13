// 1.21.1 implementation. It pairs with the same-named 26.x file under src/ in the version
// tree: this file is that file evaluated for this node. Keep them in sync when 26.x changes.
package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.bindings.event.client.ClientEvents;
import com.tkisor.nekojs.client.renderer.NekoEntityRenderers;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.lifecycle.NekoRuntimeRoot;
import com.tkisor.nekojs.core.pack.ScriptPackRegistry;
import com.tkisor.nekojs.core.plugin.PluginGenerationHooks;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;
import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import com.tkisor.nekojs.wrapper.registry.gen.EntityTypeBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.Path;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public class NekoJSClient {

    public static void register(IEventBus modEventBus, NekoRuntimeRoot root) {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(Commands.literal("nekojs")
                        .then(Commands.literal("browse").executes(context -> BrowseCommand.open()))));
        modEventBus.addListener((FMLConstructModEvent event) -> onClientSetup(event, root));
        modEventBus.addListener((RegisterClientReloadListenersEvent event) -> onClientResourceReload(event, root));
        modEventBus.addListener(NekoJSClient::onRegisterEntityRenderers);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTickPost(event, root));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> onClientLoggingIn(event, root));
        NeoForge.EVENT_BUS.addListener(NekoJSClient::onLevelUnload);
        ClientEvents.bindModBus(modEventBus);
    }

    /// Some events need a very early hook point, such as RegisterKeyMappingsEvent
    private static void onClientSetup(FMLConstructModEvent event, NekoRuntimeRoot root) {
        event.enqueueWork(() -> {
            NekoJS.LOGGER.debug("Client environment ready, loading CLIENT scripts...");
            root.load(ScriptType.CLIENT);
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.CLIENT).debug("Early script injection...");
        });
    }

    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EntityTypeBuilder.registeredEntityTypes().forEach(type -> event.registerEntityRenderer(type,
                NekoEntityRenderers.provider(EntityTypeBuilder.renderConfiguration(type))));
    }

    private static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event, NekoRuntimeRoot root) {
        // Login runs on the client thread after the integrated server publishes WORLD sources.
        // Remote sources already activate during configuration; do not reload them again here.
        if (!event.getConnection().isMemoryConnection() || ScriptPackRegistry.get().worldPacks().isEmpty()) {
            return;
        }
        try {
            root.reload(ScriptType.CLIENT);
        } catch (Exception failure) {
            NekoJS.LOGGER.error("[NEKO-1017] CLIENT reload after local world pack activation failed", failure);
            root.errorTracker().recordCallbackError(ScriptType.CLIENT, "world_pack_login", failure);
        }
    }

    private static void onClientTickPost(ClientTickEvent.Post event, NekoRuntimeRoot root) {
        root.flushReadyNodeTimers(ScriptType.CLIENT);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PDataSyncService.clearClientMirrors();
            // The ClientData key-value store is cleared on disconnect and on world switch; the server
            // pushes the values again after a reconnect
            ClientDataStore.SHARED.clear();
        }
    }

    private static void onClientResourceReload(RegisterClientReloadListenersEvent event, NekoRuntimeRoot root) {
        // 1.21.1: NeoForge does not need an explicit id when registering a reload listener, so
        // calling registerReloadListener is enough
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                root.reload(ScriptType.CLIENT);
            } catch (Exception e) {
                // If the reload already cleared the old environment's listeners and the new environment never
                // came up, the player gets no indication at all. Log at error level and add an error panel
                // entry (rt/ item) instead of only logging at DEBUG
                NekoJS.LOGGER.error("CLIENT script reload (F3+T) failed", e);
                root.errorTracker().recordCallbackError(ScriptType.CLIENT, "client_reload", e);
            }
            postClientGeneration(root);
        });
    }

    /**
     * Client generation event: scripts write asset JSON into
     * {@code <gameDir>/nekojs/assets} (a disk resource pack, read lazily so the reload order
     * stays correct). Langs are collected first, then assets are generated, the same order the
     * KubeJS flow uses.
     */
    private static void postClientGeneration(NekoRuntimeRoot root) {
        try {
            Path assets = NekoJSPaths.get().assets();
            DataGeneratorJS generator = new DataGeneratorJS(assets, "after_mods");
            com.tkisor.nekojs.resource.NekoJSPackLoader.prepareItemAssets(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
            PluginGenerationHooks.fireGenerateAssets(generator);
            ClientEvents.GENERATE_ASSETS.post(generator, "after_mods");
            // Once the script model files are on disk, fill in a default model for blocks that declared a
            // renderType but did not write their own model
            // (26.x model driver: translucent needs a force_translucent texture reference)
            generateSpawnEggModels(generator);
            // The language set for this round is the plugin's generatedLangs() declarations plus the
            // languages of script keyed listeners, in lexicographic order. Validate the whole set before
            // generating per language: one invalid language code rejects the batch before any lang file is
            // written, so no partial language files appear (a plugin language other than en_us has to be
            // declared explicitly, see generatedLangs()).
            var langs = PluginGenerationHooks.resolveGeneratedLangs(ClientEvents.LANG.registeredKeys());
            for (String lang : langs) {
                LangGeneratorJS langGenerator = new LangGeneratorJS(lang);
                PluginGenerationHooks.fireGenerateLang(langGenerator);
                ClientEvents.LANG.post(langGenerator, lang);
                langGenerator.writeTo(assets, lang);
            }
        } catch (Exception e) {
            // Asset generation failure means the client script output is incomplete (missing models or
            // langs), so it goes to the error panel as well
            NekoJS.LOGGER.error("Client asset generation failed", e);
            root.errorTracker().recordCallbackError(ScriptType.CLIENT, "generate_assets", e);
        }
    }
    private static void generateSpawnEggModels(DataGeneratorJS generator) {
        for (ResourceLocation id : EntityTypeBuilder.registeredSpawnEggs()) {
            String modelPath = id.getNamespace() + "/models/item/" + id.getPath() + ".json";
            if (generator.getJson(modelPath) != null) {
                continue;
            }
            com.google.gson.JsonObject model = new com.google.gson.JsonObject();
            model.addProperty("parent", "minecraft:item/template_spawn_egg");
            generator.json(modelPath, model);
        }
    }
}
