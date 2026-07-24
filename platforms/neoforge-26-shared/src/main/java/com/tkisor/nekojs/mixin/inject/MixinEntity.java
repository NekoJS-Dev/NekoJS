package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.inject.EntityExtension;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author ZZZank
 */
@Mixin(Entity.class)
public abstract class MixinEntity implements EntityExtension {
}
