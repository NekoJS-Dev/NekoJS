package com.tkisor.nekojs.stage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

public final class StageDamageActors {
    private StageDamageActors() {
    }

    public static @Nullable Player fromDamageSource(DamageSource source) {
        Player actor = fromEntity(source.getEntity());
        return actor != null ? actor : fromEntity(source.getDirectEntity());
    }

    public static @Nullable Player fromEntity(@Nullable Entity entity) {
        for (int depth = 0; entity != null && depth < 8; depth++) {
            if (entity instanceof Player player) {
                return player;
            }
            if (entity instanceof Projectile projectile) {
                entity = projectile.getOwner();
            } else if (entity instanceof PrimedTnt tnt) {
                entity = tnt.getOwner();
            } else if (entity instanceof AreaEffectCloud cloud) {
                entity = cloud.getOwner();
            } else if (entity instanceof OwnableEntity ownable) {
                entity = ownable.getOwner();
            } else {
                break;
            }
        }
        return null;
    }
}
