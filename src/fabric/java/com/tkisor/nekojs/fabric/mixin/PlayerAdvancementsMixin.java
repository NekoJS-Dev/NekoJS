package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Fabric hook for the player advancement event ({@code PlayerEvents.advancement}).
 *
 * <p>NeoForge 26.1.2 fires it inside {@code PlayerAdvancements#award}:
 * {@code if (!wasDone && progress.isDone()) { rewards.grant(...);
 * display.ifPresent(display -> { ...announce...; onAdvancementEarnedEvent(player, holder); }) }}
 * — only an advancement that has just completed and <b>has a display</b> fires (the announce
 * gamerule does not matter).
 *
 * <p>Injection point (checked with javap -c on minecraft-merged-deobf-26.1.2): the
 * {@code holder.value().display().ifPresent(...)} call in {@code award} (offsets 70-84,
 * InvokeDynamic #334) becomes the private synthetic method
 * {@code lambda$award$0(AdvancementHolder, DisplayInfo)}. Injecting at its HEAD yields the holder
 * and the display (the player comes from a @Shadow field), which matches firing inside NeoForge's
 * ifPresent exactly: an advancement without a display does not fire. The {@code rewards.grant}
 * call site (offset 67) is in the same "just completed" branch but runs before the display check;
 * hooking it would fire for advancements without a display, so it is not used.
 *
 * <p>{@code lambda$award$0} is private synthetic, so javac may rename it when Minecraft is
 * refactored. defaultRequire=1 turns that into a hard failure instead of a silently lost hook; in
 * that case move the hook point to the {@code Optional.ifPresent} INVOKE and capture the holder
 * with {@code @ModifyVariable}.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow private ServerPlayer player;

    @Inject(method = "lambda$award$0", at = @At("HEAD"))
    private void nekojs$onAdvancementEarned(AdvancementHolder holder, DisplayInfo display, CallbackInfo ci) {
        FabricPlayerEventBindings.postAdvancement(player, holder);
    }
}
