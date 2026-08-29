package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.core.plugin.AttachedDataHooks;
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

    @Override
    public AttachedData<Player> neko$data() {
        if (nekojs$attachedData == null) {
            nekojs$attachedData = new AttachedData<>((Player) (Object) this);
            AttachedDataHooks.fireAttachPlayer(nekojs$attachedData);
        }
        return nekojs$attachedData;
    }
}
