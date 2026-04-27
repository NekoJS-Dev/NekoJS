package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.LevelExtension;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author ZZZank
 */
@Mixin(Level.class)
public abstract class MixinLevel implements LevelExtension {
}
