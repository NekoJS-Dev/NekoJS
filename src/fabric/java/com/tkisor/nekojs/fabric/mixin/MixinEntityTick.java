package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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
@Mixin(ServerLevel.class)
public abstract class MixinEntityTick {

    // Lazy registration (idempotent): the first server-side entity tick always comes before any
    // leaveLevel case
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void nekojs$tickEntity(Entity entity, Operation<Void> original) {
        FabricEntityEventBindingsV2.postTickPre(entity);
        original.call(entity);
        FabricEntityEventBindingsV2.postTickPost(entity);
    }

    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void nekojs$tickPassenger(Entity entity, Operation<Void> original) {
        FabricEntityEventBindingsV2.wrapVersionedPassengerTick(entity, original);
    }
}
