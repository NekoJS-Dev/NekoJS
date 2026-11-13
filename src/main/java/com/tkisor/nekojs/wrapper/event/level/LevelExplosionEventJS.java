package com.tkisor.nekojs.wrapper.event.level;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

/**
 * Loader-neutral payload for explosion events ({@code LevelEvents.explosionStart} /
 * {@code explosionDetonate}, plus the aliases {@code beforeExplosion} / {@code afterExplosion}).
 *
 * <p>The member names follow NeoForge 26.x {@code ExplosionEvent}: {@code event.level} /
 * {@code event.explosion} (NeoForge's {@code getLevel()} / {@code getExplosion()}, where
 * {@link Explosion} is a 26.x vanilla <b>interface</b> and therefore loader-independent). The
 * convenience getters {@code center/radius/sourceEntity/player/indirectSourceEntity} read the
 * public interface members of {@code explosion} directly ({@code center()} /
 * {@code radius()} / {@code getDirectSourceEntity()} / {@code getIndirectSourceEntity()}).
 *
 * <p>Cancellable: only {@code explosionStart} can be cancelled (the NeoForge
 * {@code ExplosionEvent.Start} semantics: cancelled, the whole explosion is not applied;
 * fabric intercepts it at the {@code ServerExplosion#explode()} entry in
 * {@code MixinServerExplosion}). {@code explosionDetonate} is notification only.
 *
 * <p>fabric implementation: {@code MixinServerExplosion} posts before and after the
 * {@code ServerExplosion#explode()} that {@code ServerLevel#explode} constructs, and only on
 * the server (in 26.x the client explosion logic uses the separate
 * {@code ClientLevel#explode} implementation and {@code ClientboundExplodePacket}, which do
 * not go through this class).
 *
 * <p><b>Deliberately no affectedBlocks / affectedEntities</b>: NeoForge 26.x
 * {@code ExplosionEvent.Detonate} exposes getAffectedBlocks()/getAffectedEntities(), but the
 * 26.x vanilla {@code ServerExplosion} does not keep either list (javap-verified:
 * it only has the affected block count returned by {@code explode()}, getHitPlayers() and
 * isSmall(), and the explosion positions are computed temporarily on the method stack), so the
 * payload has no data source and does not invent one.
 */
@Doc("Fired around an explosion (LevelEvents.explosionStart / explosionDetonate).")
@Getter
public class LevelExplosionEventJS extends LevelEventJS {

    @Doc("The explosion object (26.x vanilla Explosion interface; level, center, radius, source entity).")
    private final Explosion explosion;

    public LevelExplosionEventJS(ServerLevel level, Explosion explosion) {
        super(level);
        this.explosion = explosion;
    }

    @Doc("Explosion center position.")
    public Vec3 getCenter() {
        return explosion.center();
    }

    @Doc("Explosion radius.")
    public float getRadius() {
        return explosion.radius();
    }

    @Doc("The explosion's direct source entity (may be null).")
    public Entity getSourceEntity() {
        return explosion.getDirectSourceEntity();
    }

    @Doc("The player who caused the explosion, or null if not a player-source explosion.")
    public Player getPlayer() {
        return explosion.getDirectSourceEntity() instanceof Player player ? player : null;
    }

    @Doc("The entity the explosion damage is attributed to for credit/knockback (may be null).")
    public Entity getIndirectSourceEntity() {
        return explosion.getIndirectSourceEntity();
    }
}
