package com.tkisor.nekojs.dynamic.mixin;

import com.tkisor.nekojs.dynamic.DynamicRegistries;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Port of Katton's {@code RegistryDataCollectorMixin} for dynamically
 * registered items (26.1/26.2).
 *
 * <p>During {@code collectGameRegistries} the client runs
 * {@code DataComponentInitializers.build(...)} and re-binds every holder's
 * default components from the registered initializers. For dynamic items that
 * rebuild loses the tag-backed {@code DAMAGE_RESISTANT} holder set bound to the
 * fresh registry access, so after the method returns we rebuild each dynamic
 * item's map from its recorded spec against the new frozen registries.
 *
 * <p>Client-only mixin (target class is {@code @OnlyIn(CLIENT)}); in
 * multiplayer without server-side script sync the dynamic item set is empty and
 * this is a no-op.
 */
@Mixin(RegistryDataCollector.class)
public abstract class RegistryDataCollectorMixin {

    @Inject(method = "collectGameRegistries", at = @At("RETURN"))
    private void nekojs$reapplyDynamicItemComponents(
            ResourceProvider resourceProvider,
            RegistryAccess.Frozen originalRegistries,
            boolean tagsAndComponentsForSynchronizedRegistriesOnly,
            CallbackInfoReturnable<RegistryAccess.Frozen> cir
    ) {
        DynamicRegistries.reapplyDynamicItemComponents(cir.getReturnValue());
    }
}
