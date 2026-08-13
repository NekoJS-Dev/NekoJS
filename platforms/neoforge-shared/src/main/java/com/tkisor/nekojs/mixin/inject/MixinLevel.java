package com.tkisor.nekojs.mixin.inject;

import com.tkisor.nekojs.api.data.AttachedData;
import com.tkisor.nekojs.api.inject.LevelExtension;
import com.tkisor.nekojs.core.plugin.AttachedDataHooks;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * @author ZZZank
 */
@Mixin(Level.class)
public abstract class MixinLevel implements LevelExtension {
    @Unique
    private AttachedData<Level> nekojs$attachedData;

    @Override
    public AttachedData<Level> neko$data() {
        if (nekojs$attachedData == null) {
            nekojs$attachedData = new AttachedData<>((Level) (Object) this);
            AttachedDataHooks.fireAttachLevel(nekojs$attachedData);
        }
        return nekojs$attachedData;
    }
}
