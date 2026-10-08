//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic.mixin;

import com.tkisor.nekojs.dynamic.DynamicRegistryConfigurationTask;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.neoforge.network.ConfigurationInitialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Ordinary configuration events run after NeoForge queues frozen-registry sync. */
@Mixin(value = ConfigurationInitialization.class, remap = false)
public abstract class ConfigurationInitializationMixin {
    @Inject(method = "configureEarlyTasks", at = @At("HEAD"), remap = false)
    private static void nekojs$syncDynamicRegistriesFirst(ServerConfigurationPacketListener listener,
            Consumer<ConfigurationTask> tasks, CallbackInfo ci) {
        DynamicRegistryConfigurationTask.registerEarly(listener, tasks);
    }
}
//?}
//?}
