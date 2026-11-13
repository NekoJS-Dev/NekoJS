package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code EntityEvents.tickPre} and {@code EntityEvents.tickPost}: the HEAD and TAIL
 * of {@code Entity#tick}. This mixin also triggers the lazy registration of the fabric-api
 * {@code ServerEntityEvents.ENTITY_UNLOAD} callback for leaveLevel through
 * {@code FabricEntityEventBindingsV2.ensureRegistered()}.
 *
 * <p>Why not fabric-api: the javap dump of entity-events-v1 5.0.2 has no per-entity tick
 * callback; it only converts Elytra/Sleep/Combat/Living damage and death.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code public void tick()} is a <b>concrete</b> method on the {@code Entity} abstract
 *       class. Subclasses may override it, and an entity that overrides it without calling
 *       {@code super.tick()} never reaches this hook.</li>
 *   <li>{@code level()} is a public concrete method, and clients tick too, so both injection
 *       points filter on {@code level().isClientSide()}; the SERVER bus only receives
 *       server-side instances.</li>
 * </ul>
 *
 * <p><b>Cost</b>: every entity reaches the injection points every tick, even with no listeners.
 * The injected code only performs two short-circuit checks, {@code isClientSide} and
 * {@code hasListeners} ({@code hasListeners()} runs inside
 * {@code FabricEntityEventBindingsV2} before the payload is built), so no event object is
 * created without listeners. This is still a high-frequency path: keep script listeners light
 * and register them only while needed.
 */
@Mixin(Entity.class)
public abstract class MixinEntityTick {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void nekojs$onTickPre(CallbackInfo ci) {
        if (((Entity) (Object) this).level().isClientSide()) {
            return;
        }
        // Lazy registration (idempotent): the first server-side entity tick always comes before any
        // leaveLevel case
        FabricEntityEventBindingsV2.ensureRegistered();
        FabricEntityEventBindingsV2.postTickPre((Entity) (Object) this);
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void nekojs$onTickPost(CallbackInfo ci) {
        if (((Entity) (Object) this).level().isClientSide()) {
            return;
        }
        FabricEntityEventBindingsV2.postTickPost((Entity) (Object) this);
    }
}
