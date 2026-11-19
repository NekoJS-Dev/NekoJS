package com.tkisor.nekojs.fabric.mixin;

import com.mojang.brigadier.ParseResults;
import com.tkisor.nekojs.fabric.event.FabricCommandEventBindings;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric mixin hook for {@code CommandEvents.command}: intercepts
 * {@link Commands#performCommand(ParseResults, String)} before Brigadier command execution so
 * scripts can inspect or cancel server commands.
 */
@Mixin(Commands.class)
public abstract class MixinCommandsPerform {

    @Inject(method = "performCommand(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/Profiler;get()Lnet/minecraft/util/profiling/ProfilerFiller;", ordinal = 0),
            cancellable = true, require = 1, allow = 1)
    private void nekojs$onPerformCommand(ParseResults<CommandSourceStack> parseResults, String command,
                                         CallbackInfo ci) {
        if (FabricCommandEventBindings.postCommand(parseResults, command)) {
            ci.cancel();
        }
    }
}
