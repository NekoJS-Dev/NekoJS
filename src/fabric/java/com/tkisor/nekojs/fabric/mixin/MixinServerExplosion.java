package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for {@code LevelEvents.explosionStart} and {@code LevelEvents.explosionDetonate}
 * (with the beforeExplosion and afterExplosion aliases): the HEAD and TAIL of
 * {@code ServerExplosion#explode()}.
 *
 * <p>26.x explosion shape (checked with javap):
 * <ul>
 *   <li>{@code Explosion} is an <b>interface</b> and {@code ServerExplosion} implements it.
 *       Construction happens only in {@code ServerLevel.explode(...)} (a constant-pool scan finds
 *       this as the only {@code new ServerExplosion} in the jar), so HEAD and TAIL of
 *       {@code explode()} match the timing of NeoForge ExplosionEvent.Start and
 *       ExplosionEvent.Detonate: Start before resolution, Detonate after it, both before
 *       {@code ServerLevel.explode} sends the explosion packet.</li>
 *   <li>{@code public int explode()} runs {@code gameEvent(EXPLODE)},
 *       {@code calculateExplodedPositions()}, {@code hurtEntities()},
 *       {@code interactWithBlocks()} (when it interacts with blocks) and {@code createFire()},
 *       and returns the number of affected blocks; only afterwards does {@code ServerLevel.explode}
 *       send {@code ClientboundExplodePacket} to players. 26.x has no separate
 *       {@code finalizeExplosion()} stage, so the two event points stay separate.</li>
 *   <li>Server only: a client explosion uses its own {@code ClientLevel#explode} implementation
 *       (javap; the ClientLevel class is in the client/multiplayer package) and packet rendering,
 *       and never reaches {@code ServerExplosion}.</li>
 *   <li>The payload reads public members of the {@link Explosion} interface (level/center/radius/
 *       getDirectSourceEntity/getIndirectSourceEntity) and needs no AW.</li>
 * </ul>
 *
 * <p><b>Cancelling (known caveat)</b>: HEAD cancelling calls {@code cir.setReturnValue(0)}, which
 * the int return type requires. The explosion itself (damage, blocks, fire) is skipped, but
 * {@code ServerLevel.explode} still sends players within 64 blocks a {@code ClientboundExplodePacket}
 * with {@code blockCount=0} and no hurt entities, so the visuals and sound still play with no
 * effect. NeoForge Start cancelling interrupts the whole method and sends no packet. To match that
 * exactly, hook the HEAD of {@code ServerLevel.explode} and call {@code ci.cancel()} there, at the
 * cost of building the payload from the method parameters instead of an {@code Explosion} object.
 */
@Mixin(ServerExplosion.class)
public abstract class MixinServerExplosion {

    @Inject(method = "explode()I", at = @At("HEAD"))
    private void nekojs$onExplosionStart(CallbackInfoReturnable<Integer> cir) {
        if (FabricLevelEventBindingsV2.postExplosionStart((Explosion) (Object) this)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "explode()I", at = @At("TAIL"))
    private void nekojs$onExplosionDetonate(CallbackInfoReturnable<Integer> cir) {
        FabricLevelEventBindingsV2.postExplosionDetonate((Explosion) (Object) this);
    }
}
