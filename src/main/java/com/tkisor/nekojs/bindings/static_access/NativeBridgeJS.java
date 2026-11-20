//~ mc_legacy_api
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.entity.StableEntity;
import com.tkisor.nekojs.api.entity.StableLivingEntity;
import com.tkisor.nekojs.api.entity.StablePlayer;
import com.tkisor.nekojs.api.entity.StableServerPlayer;
import com.tkisor.nekojs.core.lifecycle.ScriptLifecycleGate;
import com.tkisor.nekojs.platform.NekoIdCompat;
import com.tkisor.nekojs.wrapper.entity.StableEntities;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Explicit native interoperation; native members remain profile-dependent. */
@com.tkisor.nekojs.api.annotation.ScriptNativeBridge
public final class NativeBridgeJS {
    private final Ids ids = new Ids();
    private final Entities entities = new Entities();

    public Ids getIds() { return ids; }
    public Entities getEntities() { return entities; }

    /** Converts only real Minecraft entities and their stable views within a game callback. */
    public static final class Entities {
        public StableEntity fromNative(Entity entity) {
            requireCallback();
            if (entity == null) throw new IllegalArgumentException("[NEKO-4067] Expected a native Minecraft entity");
            return StableEntities.wrap(entity);
        }

        public Entity toNative(StableEntity entity) {
            requireCallback();
            return StableEntities.unwrap(entity);
        }

        public LivingEntity toNativeLivingEntity(StableLivingEntity entity) {
            requireCallback();
            return StableEntities.unwrap(entity);
        }

        public Player toNativePlayer(StablePlayer player) {
            requireCallback();
            return StableEntities.unwrap(player);
        }

        public ServerPlayer toNativeServerPlayer(StableServerPlayer player) {
            requireCallback();
            return StableEntities.unwrap(player);
        }

        private static void requireCallback() {
            if (!ScriptLifecycleGate.isInCallback()) {
                throw new IllegalStateException("[NEKO-4067] Native entity conversion requires a synchronous script callback");
            }
        }
    }

    public static final class Ids {
        public Identifier toNative(NekoId id) {
            if (id == null) throw new IllegalArgumentException("[NEKO-4056] Resource ID cannot be null");
            return Identifier.class.cast(NekoIdCompat.toPlatformId(id));
        }

        public NekoId fromNative(Identifier id) {
            return NekoIdCompat.fromPlatformId(id);
        }
    }
}
