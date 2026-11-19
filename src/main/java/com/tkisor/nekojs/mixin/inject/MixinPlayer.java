package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageHandleResolver;
import com.tkisor.nekojs.api.stage.StagePlayerRef;
import com.tkisor.nekojs.core.plugin.AttachedDataHooks;
import com.tkisor.nekojs.core.stage.ClientStageMirror;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * @author ZZZank
 */
@Mixin(Player.class)
public abstract class MixinPlayer implements PlayerExtension {
    @Unique
    private AttachedData<Player> nekojs$attachedData;

    @Unique
    private transient StageHandle nekojs$stageHandle;

    @Override
    public AttachedData<Player> neko$data() {
        if (nekojs$attachedData == null) {
            nekojs$attachedData = new AttachedData<>((Player) (Object) this);
            AttachedDataHooks.fireAttachPlayer(nekojs$attachedData);
        }
        return nekojs$attachedData;
    }

    @Override
    public StageHandle neko$getStages() {
        Player player = (Player) (Object) this;
        if (nekojs$isFakePlayer(player)) {
            return StageHandle.EMPTY;
        }
        if (player.level() != null && player.level().isClientSide()) {
            return StageHandle.clientReadOnly(ClientStageMirror.SHARED.playerView());
        }
        StageHandle cached = nekojs$stageHandle;
        if (cached != null) {
            return cached;
        }
        StageHandle resolved = StageHandleResolver.resolvePlayer(
                StagePlayerRef.of(player.getStringUUID(), player.getScoreboardName()));
        if (resolved != null && resolved != StageHandle.EMPTY) {
            nekojs$stageHandle = resolved;
        }
        return resolved != null ? resolved : StageHandle.EMPTY;
    }

    @Unique
    private boolean nekojs$isFakePlayer(Player player) {
//? if neoforge {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return true;
        }
//?}
        for (Class<?> cls = player.getClass();
                cls != null && cls != Player.class && cls != Object.class;
                cls = cls.getSuperclass()) {
            if (cls.getName().contains("FakePlayer")) {
                return true;
            }
        }
        return false;
    }
}
