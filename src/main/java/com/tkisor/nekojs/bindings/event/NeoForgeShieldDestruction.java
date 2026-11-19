package com.tkisor.nekojs.bindings.event;

import com.tkisor.nekojs.api.ScriptType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;

import java.util.function.Consumer;

public final class NeoForgeShieldDestruction {
    private static final ThreadLocal<Capture> ACTIVE_BREAK = new ThreadLocal<>();

    private NeoForgeShieldDestruction() {}

    public static Consumer<Item> captureBreak(LivingEntity actor, ItemStack stack, InteractionHand hand,
                                              Consumer<Item> onBreak) {
        if (!(actor instanceof ServerPlayer player) || !ServerLevelEventThread.isCurrent(player.level())
                || stack.isEmpty() || !PlayerEvents.DESTROYED.hasListeners()) {
            return onBreak;
        }
        ItemStack snapshot = stack.copy();
        return item -> {
            if (item != snapshot.getItem() || !stack.isEmpty()) {
                onBreak.accept(item);
                return;
            }
            Capture previous = ACTIVE_BREAK.get();
            Capture capture = new Capture(player, stack, snapshot, hand);
            ACTIVE_BREAK.set(capture);
            try {
                onBreak.accept(item);
            } finally {
                if (previous == null) ACTIVE_BREAK.remove();
                else ACTIVE_BREAK.set(previous);
            }
        };
    }

    static boolean forwardNative(PlayerDestroyItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return false;
        ItemStack original = event.getOriginal();
        if (original.isEmpty()) {
            Capture capture = ACTIVE_BREAK.get();
            if (capture != null && capture.emptyNativeEvent == null && capture.player == player
                    && capture.stack == original && capture.hand == event.getHand()) {
                capture.emptyNativeEvent = event;
                PlayerEvents.DESTROYED.postStableModuleNotification(
                        new StableShieldBreak(player, capture.snapshot, capture.hand), capture.snapshot.getItem(), ScriptType.SERVER);
            }
        }
        return true;
    }

    static boolean isNative(PlayerDestroyItemEvent event) {
        return !(event instanceof StableShieldBreak);
    }

    static boolean isStable(PlayerDestroyItemEvent event) {
        if (event.getOriginal().isEmpty()) return false;
        Capture capture = ACTIVE_BREAK.get();
        return capture == null || capture.emptyNativeEvent != event;
    }

    private static final class Capture {
        private final ServerPlayer player;
        private final ItemStack stack;
        private final ItemStack snapshot;
        private final InteractionHand hand;
        private PlayerDestroyItemEvent emptyNativeEvent;

        private Capture(ServerPlayer player, ItemStack stack, ItemStack snapshot, InteractionHand hand) {
            this.player = player;
            this.stack = stack;
            this.snapshot = snapshot;
            this.hand = hand;
        }
    }

    private static final class StableShieldBreak extends PlayerDestroyItemEvent {
        private StableShieldBreak(ServerPlayer player, ItemStack snapshot, InteractionHand hand) {
            super(player, snapshot, hand);
        }
    }
}
