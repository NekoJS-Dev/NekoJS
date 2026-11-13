// 26.x implementation; no version guard should appear in this file. The 1.21.1 implementation is the
// file of the same name under versions/1.21.1/src, so behavior changes here must be mirrored there.
//? if neoforge {
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import com.tkisor.nekojs.wrapper.pdata.PDataSyncService;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.nio.file.Path;

public class NekoJSClient {

    public static void register(IEventBus modEventBus, NekoRuntimeRoot root) {
        com.tkisor.nekojs.client.gui.ErrorDashboardScreens.install(
                () -> root.openBuiltinUiSession(net.minecraft.client.Minecraft.getInstance()::schedule),
                com.tkisor.nekojs.platform.compat.McClientCompat.get());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                event.getDispatcher().register(Commands.literal("nekojs")
                        .then(Commands.literal("browse").executes(context -> BrowseCommand.open()))));
        modEventBus.addListener((FMLConstructModEvent event) -> onClientSetup(event, root));
        modEventBus.addListener((AddClientReloadListenersEvent event) -> onClientResourceReload(event, root));
        modEventBus.addListener(NekoJSClient::onRegisterEntityRenderers);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> onClientTickPost(event, root));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> onClientLoggingIn(event, root));
        NeoForge.EVENT_BUS.addListener(NekoJSClient::onLevelUnload);
        ClientEvents.bindModBus(modEventBus);
    }

    /// Some events need a very early point in time, such as RegisterKeyMappingsEvent
    private static void onClientSetup(FMLConstructModEvent event, NekoRuntimeRoot root) {
        event.enqueueWork(() -> {
            NekoJS.LOGGER.debug("Client environment ready, loading CLIENT scripts...");
            root.load(ScriptType.CLIENT);
            com.tkisor.nekojs.script.ScriptTypeEnv.logger(ScriptType.CLIENT).debug("Early script injection...");
            // Collection point for the initial generation declared by CLIENT scripts (scripts declare
            // register/unregister through ClientEvents.postEffects). The initial load is not a transactional
            // path, so there is no candidate and no commit: collect once against the active bus and install
            // the full generation only after the domain owner preflight passes. If it fails, the whole batch
            // is rejected and the previous active declarations stay. Later F3+T resource reloads go through
            // the DOMAIN_PLAN phase of the transactional reload, with the same owner and collect call.
            com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner postEffects = postEffectDomain(root);
            if (postEffects != null) {
                postEffects.applyInitialPlan();
            }
        });
    }

    /**
     * Returns the post-effect declaration domain owner (a domain collector authorized by root), or
     * {@code null} when none is registered, in which case the collection point is skipped.
     */
    private static com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner postEffectDomain(NekoRuntimeRoot root) {
        var collector = root == null ? null : root.domainCollector(com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner.DOMAIN);
        return collector instanceof com.tkisor.nekojs.client.posteffect.PostEffectDomainOwner owner ? owner : null;
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
            // The ClientData key-value store is cleared together with the disconnect or world switch;
            // the server pushes it again after a reconnect
            ClientDataStore.SHARED.clear();
        }
    }

    private static void onClientResourceReload(AddClientReloadListenersEvent event, NekoRuntimeRoot root) {
        Identifier listenerId = Identifier.fromNamespaceAndPath(NekoJS.MODID, "client_scripts_reload");

        event.addListener(listenerId, (ResourceManagerReloadListener) resourceManager -> {
            NekoJS.LOGGER.debug("Detected client resource reload (F3 + T), reloading CLIENT scripts...");
            try {
                root.reload(ScriptType.CLIENT);
            } catch (Exception e) {
                // When the reload has already cleared the old environment's listeners and the new one failed to come up,
                // the player gets no hint at all, so log at error level and record the error panel entry (rt/),
                // instead of only a DEBUG line
                NekoJS.LOGGER.error("CLIENT script reload (F3+T) failed", e);
                root.errorTracker().recordCallbackError(ScriptType.CLIENT, "client_reload", e);
            }
            postClientGeneration(root);
        });
    }

    /**
     * Client generation event: scripts write asset JSON into {@code <gameDir>/nekojs/assets}, a resource
     * pack on disk that is read lazily so reload ordering stays correct. Languages are aggregated before
     * assets are generated, the same order KubeJS uses.
     */
    private static void postClientGeneration(NekoRuntimeRoot root) {
        try {
            Path assets = NekoJSPaths.get().assets();
            DataGeneratorJS generator = new DataGeneratorJS(assets, "after_mods");
            com.tkisor.nekojs.resource.NekoJSPackLoader.prepareItemAssets(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
            PluginGenerationHooks.fireGenerateAssets(generator);
            ClientEvents.GENERATE_ASSETS.post(generator, "after_mods");
            // After the script model files are on disk, fill in default models for blocks that declared a renderType
            // and wrote no model themselves (26.x is model-driven: translucent needs a force_translucent texture
            // reference)
            BlockModelGenerator.generateDefaultModels(generator);
            // Entities that requested spawnEgg(): fill in a default egg model
            // (26.x has no runtime tinting; the texture is data-driven)
            BlockModelGenerator.generateSpawnEggModels(generator);
            // The language set for this round is the union of the plugin's generatedLangs() declarations and the
            // script keyed listener languages, in lexicographic order. All codes are validated before any
            // language is generated, so one invalid language code rejects the whole batch before any lang
            // file is written and no partial language files appear (plugin languages other than en_us must
            // be declared explicitly; see generatedLangs()).
            var langs = PluginGenerationHooks.resolveGeneratedLangs(ClientEvents.LANG.registeredKeys());
            for (String lang : langs) {
                LangGeneratorJS langGenerator = new LangGeneratorJS(lang);
                PluginGenerationHooks.fireGenerateLang(langGenerator);
                ClientEvents.LANG.post(langGenerator, lang);
                langGenerator.writeTo(assets, lang);
            }
        } catch (Exception e) {
            // A failed asset generation leaves the client script output incomplete (missing models and
            // lang), so it also goes to the error panel
            NekoJS.LOGGER.error("Client asset generation failed", e);
            root.errorTracker().recordCallbackError(ScriptType.CLIENT, "generate_assets", e);
        }
    }
}
//?}
