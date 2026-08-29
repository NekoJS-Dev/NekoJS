package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.api.inject.EntityExtension;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code EntityExtension} 的 fabric 注入（等价 NeoForge 的 interface injection）：
 * default 方法全在接口上（含 {@code neko$pdata()}，持久化读写经 {@code EntityPDataStore}
 * → {@code NekoEntityPDataMixin} 的字段），本类只需挂上接口。
 */
@Mixin(Entity.class)
public interface MixinEntity extends EntityExtension {
}
