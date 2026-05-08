package com.tkisor.nekojs.client;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.ClassFilter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "nekojs", value = Dist.CLIENT)
public class NekoSecurityWarningHandler {

    private static boolean titleWarningShown = false;
    private static boolean chatWarningShown = false;

    private static boolean checkedHost = false;
    private static boolean needsWarningThisSession = false;

    private static void checkHostOnce() {
        if (!checkedHost) {
            checkedHost = true;
            if (ClassFilter.isAnyUnsafeFeatureEnabled()) {
                if (!NekoHostIdentifier.isHostAcknowledged()) {
                    needsWarningThisSession = true;
                    NekoHostIdentifier.saveHostCode();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMainMenuInit(ScreenEvent.Init.Post event) {
        checkHostOnce();

        if (needsWarningThisSession && !titleWarningShown && event.getScreen() instanceof TitleScreen) {
            SystemToast.addOrUpdate(
                    Minecraft.getInstance().getToastManager(),
                    SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                    Component.translatable("nekojs.security.toast.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.translatable("nekojs.security.toast.desc")
            );
            NekoJS.LOGGER.warn("[NekoJS Security] High-risk permissions detected, sent warning to player on main menu.");
            titleWarningShown = true;
        }
    }

    @SubscribeEvent
    public static void onClientJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        checkHostOnce();

        if (needsWarningThisSession && !chatWarningShown) {
            HoverEvent hoverEvent = new HoverEvent.ShowText(getDetailedWarningText());

            MutableComponent details = Component.literal("\n")
                    .append(Component.translatable("nekojs.security.chat.hover_hint"))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.UNDERLINE)
                    .withStyle(style -> style.withHoverEvent(hoverEvent));

            MutableComponent warningMsg = Component.literal("\n")
                    .append(Component.translatable("nekojs.security.chat.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("nekojs.security.chat.body1").withStyle(ChatFormatting.YELLOW))
                    .append(Component.translatable("nekojs.security.chat.body2").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.translatable("nekojs.security.chat.body3").withStyle(ChatFormatting.YELLOW))
                    .append(details)
                    .append(Component.literal("\n"));

            event.getPlayer().sendSystemMessage(warningMsg);
            chatWarningShown = true;
        }
    }

    private static Component getDetailedWarningText() {
        MutableComponent text = Component.translatable("nekojs.security.detail.header").append(Component.literal("\n\n"));

        if (ClassFilter.allowThreads) {
            text.append(Component.translatable("nekojs.security.detail.threads.title").withStyle(ChatFormatting.RED))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("nekojs.security.detail.threads.desc").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n\n"));
        }

        if (ClassFilter.allowReflection) {
            text.append(Component.translatable("nekojs.security.detail.reflection.title").withStyle(ChatFormatting.RED))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("nekojs.security.detail.reflection.desc").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("\n\n"));
        }

        if (ClassFilter.allowAsm) {
            text.append(Component.translatable("nekojs.security.detail.asm.title").withStyle(ChatFormatting.RED))
                    .append(Component.literal("\n"))
                    .append(Component.translatable("nekojs.security.detail.asm.desc").withStyle(ChatFormatting.WHITE));
        }

        return text;
    }
}