package com.tkisor.nekojs.bindings.event.client;

import com.tkisor.nekojs.api.event.EventBusForgeBridge;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

public interface ClientEvents {
    EventGroup GROUP = EventGroup.of("ClientEvents");

    EventBusJS<ClientTickEvent.Pre, Void> TICK_PRE =
            GROUP.client("tickPre", ClientTickEvent.Pre.class);
    EventBusJS<ClientTickEvent.Post, Void> TICK_POST =
            GROUP.client("tickPost", ClientTickEvent.Post.class);
    EventBusJS<ClientTickEvent.Post, Void> TICK =
            GROUP.client("tick", ClientTickEvent.Post.class);
    EventBusJS<ClientPlayerNetworkEvent.LoggingIn, Void> LOGGED_IN =
            GROUP.client("loggedIn", ClientPlayerNetworkEvent.LoggingIn.class);
    EventBusJS<ClientPlayerNetworkEvent.LoggingOut, Void> LOGGED_OUT =
            GROUP.client("loggedOut", ClientPlayerNetworkEvent.LoggingOut.class);
    EventBusJS<ClientPlayerNetworkEvent.Clone, Void> CLONED =
            GROUP.client("cloned", ClientPlayerNetworkEvent.Clone.class);
    EventBusJS<RegisterClientCommandsEvent, Void> COMMAND_REGISTRY =
            GROUP.client("commandRegistry", RegisterClientCommandsEvent.class);
    EventBusJS<RegisterKeyMappingsEvent, Void> REGISTER_KEY_MAPPINGS =
            GROUP.client("registerKeyMappings", RegisterKeyMappingsEvent.class);
    EventBusJS<RegisterMenuScreensEvent, Void> REGISTER_MENU_SCREENS =
            GROUP.client("registerMenuScreens", RegisterMenuScreensEvent.class);
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_RENDERERS =
            GROUP.client("registerRenderers", EntityRenderersEvent.RegisterRenderers.class);
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_ENTITY_RENDERERS =
            GROUP.client("registerEntityRenderers", EntityRenderersEvent.RegisterRenderers.class);
    EventBusJS<EntityRenderersEvent.RegisterRenderers, Void> REGISTER_BLOCK_ENTITY_RENDERERS =
            GROUP.client("registerBlockEntityRenderers", EntityRenderersEvent.RegisterRenderers.class);
    EventBusJS<RegisterParticleProvidersEvent, Void> REGISTER_PARTICLE_PROVIDERS =
            GROUP.client("registerParticleProviders", RegisterParticleProvidersEvent.class);

    EventBusForgeBridge MAIN_BRIDGE = EventBusForgeBridge.create(NeoForge.EVENT_BUS)
            .bind(TICK_PRE)
            .bind(TICK_POST)
            .bind(TICK)
            .bind(LOGGED_IN)
            .bind(LOGGED_OUT)
            .bind(CLONED)
            .bind(COMMAND_REGISTRY);

    static void bindModBus(IEventBus modEventBus) {
        EventBusForgeBridge.create(modEventBus)
                .bind(REGISTER_KEY_MAPPINGS)
                .bind(REGISTER_MENU_SCREENS)
                .bind(REGISTER_RENDERERS)
                .bind(REGISTER_ENTITY_RENDERERS)
                .bind(REGISTER_BLOCK_ENTITY_RENDERERS)
                .bind(REGISTER_PARTICLE_PROVIDERS);
    }
}
