//~ mc_legacy_api
package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.api.entity.StableEntity;
import com.tkisor.nekojs.api.entity.StableLivingEntity;
import com.tkisor.nekojs.api.entity.StablePlayer;
import com.tkisor.nekojs.api.entity.StableServerPlayer;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.api.stage.StageHandle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Adapts live Minecraft entities without copying or looking them up by UUID. Use on the entity's owner thread. */
public final class StableEntities {
    private StableEntities() {}

    public static @Nullable StableEntity wrap(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living) return wrap(living);
        return entity == null ? null : new EntityView<>(entity);
    }

    public static @Nullable StableLivingEntity wrap(@Nullable LivingEntity entity) {
        if (entity instanceof Player player) return wrap(player);
        return entity == null ? null : new LivingEntityView<>(entity);
    }

    public static @Nullable StablePlayer wrap(@Nullable Player player) {
        if (player instanceof ServerPlayer serverPlayer) return wrap(serverPlayer);
        return player == null ? null : new PlayerView<>(player);
    }

    public static @Nullable StableServerPlayer wrap(@Nullable ServerPlayer player) {
        return player == null ? null : new ServerPlayerView(player);
    }

    /** Returns the exact native instance originally supplied to {@code wrap}. */
    public static Entity unwrap(StableEntity entity) {
        if (entity instanceof EntityView<?> view) return view.entity;
        throw new IllegalArgumentException("[NEKO-4067] Expected a native-backed entity view");
    }

    public static LivingEntity unwrap(StableLivingEntity entity) {
        return (LivingEntity) unwrap((StableEntity) entity);
    }

    public static Player unwrap(StablePlayer player) {
        return (Player) unwrap((StableEntity) player);
    }

    public static ServerPlayer unwrap(StableServerPlayer player) {
        return (ServerPlayer) unwrap((StableEntity) player);
    }

    private static class EntityView<E extends Entity> implements StableEntity {
        final E entity;

        private EntityView(E entity) {
            this.entity = entity;
        }

        @Override public String getName() { return entity.getScoreboardName(); }
        @Override public String getUuid() { return entity.getStringUUID(); }
        @Override public String getTypeId() { return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(); }
        @Override public String getDimension() { return entity.level().dimension().identifier().toString(); }
        @Override public double getX() { return entity.getX(); }
        @Override public double getY() { return entity.getY(); }
        @Override public double getZ() { return entity.getZ(); }
        @Override public boolean isAlive() { return entity.isAlive(); }
        @Override public boolean isClientSide() { return entity.level().isClientSide(); }
    }

    private static class LivingEntityView<E extends LivingEntity> extends EntityView<E> implements StableLivingEntity {
        private LivingEntityView(E entity) {
            super(entity);
        }

        @Override public float getHealth() { return entity.getHealth(); }
        @Override public float getMaxHealth() { return entity.getMaxHealth(); }
    }

    private static class PlayerView<P extends Player> extends LivingEntityView<P> implements StablePlayer {
        private PlayerView(P player) {
            super(player);
        }

        @Override public boolean isCreative() { return entity.isCreative(); }
        @Override public boolean isSpectator() { return entity.isSpectator(); }

        @Override
        public StageHandle getStages() {
            return entity instanceof PlayerExtension extension ? extension.neko$getStages() : StageHandle.EMPTY;
        }
    }

    private static final class ServerPlayerView extends PlayerView<ServerPlayer> implements StableServerPlayer {
        private ServerPlayerView(ServerPlayer player) {
            super(player);
        }

        @Override public int getLatency() { return entity.connection == null ? 0 : entity.connection.latency(); }
    }
}
