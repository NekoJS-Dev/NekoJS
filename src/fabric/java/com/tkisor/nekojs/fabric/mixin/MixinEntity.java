package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.EntityExtension;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Fabric injection of {@code EntityExtension}, equivalent to NeoForge's interface injection. All
 * the default methods live on the interface, including {@code neko$pdata()}, whose persistent
 * reads and writes go through {@code EntityPDataStore} and the {@code NekoEntityPDataMixin}
 * field, so this class only has to attach the interface.
 */
@Mixin(Entity.class)
public interface MixinEntity extends EntityExtension {
}
