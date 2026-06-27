package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.PlayerExtension;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author ZZZank
 */
@Mixin(Player.class)
public abstract class MixinPlayer implements PlayerExtension {
}
