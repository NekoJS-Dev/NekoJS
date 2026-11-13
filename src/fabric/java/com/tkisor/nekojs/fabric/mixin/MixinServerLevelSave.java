package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric hook for {@code LevelEvents.saved}: the TAIL of {@code ServerLevel#save}.
 *
 * <p>Why a mixin: fabric-api's {@code ServerLevelEvents} only has LOAD and UNLOAD in the v1
 * lifecycle and 26.x has no SAVE callback, so NeoForge {@code LevelEvent.Save} has no fabric
 * equivalent.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * {@code public void save(net.minecraft.util.ProgressListener, boolean, boolean)} is a
 * <b>concrete</b> method on ServerLevel (its entry short-circuits on the second boolean), so TAIL
 * runs after the actual save. Only the server calls it (MinecraftServer#saveAllChunks and
 * similar), so no client filter is needed. The meaning of the two booleans is not established, so
 * the {@code LevelSavedEventJS} payload only carries the level (thereby matching the getLevel()
 * value of NeoForge {@code LevelEvent.Save}).
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelSave {

    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("TAIL"))
    private void nekojs$onSave(ProgressListener progressListener, boolean flush, boolean skipSave,
                               CallbackInfo ci) {
        FabricLevelEventBindingsV2.postSaved((ServerLevel) (Object) this);
    }
}
