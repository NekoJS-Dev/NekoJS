package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.LegacyStageNotificationClient;
import com.tkisor.nekojs.network.PlayPacketDispatchers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@EventBusSubscriber(modid = NekoJS.MODID, bus = EventBusSubscriber.Bus.MOD)
public record LegacyStageNotification(
        ResourceLocation icon,
        Optional<Component> title,
        Optional<Component> subtitle,
        Optional<Component> actionBar,
        Optional<Component> chat
) implements CustomPacketPayload {
    private static final Type<LegacyStageNotification> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NekoJS.MODID, "stage_notification"));
    private static final StreamCodec<RegistryFriendlyByteBuf, LegacyStageNotification> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LegacyStageNotification::icon,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, LegacyStageNotification::title,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, LegacyStageNotification::subtitle,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, LegacyStageNotification::actionBar,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, LegacyStageNotification::chat,
            LegacyStageNotification::new
    );

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC, LegacyStageNotification::receive);
    }

    private static void receive(LegacyStageNotification notification, IPayloadContext context) {
        LegacyStageNotificationClient.receive(notification, context);
    }

    public static boolean trySend(
            ServerPlayer recipient,
            ResourceLocation icon,
            @Nullable Component title,
            @Nullable Component subtitle,
            @Nullable Component actionBar,
            @Nullable Component chat
    ) {
        if (recipient.connection == null || !recipient.connection.hasChannel(TYPE)) {
            return false;
        }
        if (recipient.getChatVisibility() == ChatVisiblity.HIDDEN) {
            chat = null;
        }
        if (title != null || actionBar != null || chat != null) {
            PlayPacketDispatchers.get().sendToPlayer(recipient, new LegacyStageNotification(
                    icon, Optional.ofNullable(title), Optional.ofNullable(subtitle),
                    Optional.ofNullable(actionBar), Optional.ofNullable(chat)
            ));
        }
        return true;
    }

    @Override
    public Type<LegacyStageNotification> type() {
        return TYPE;
    }
}
