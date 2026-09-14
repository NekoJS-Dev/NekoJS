package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code LevelEvents.saved} 的 fabric 挂点：{@code ServerLevel#save} 的 TAIL。
 *
 * <p>为什么用 mixin：fabric-api 的 {@code ServerLevelEvents} 只有 LOAD/UNLOAD
 * （v1 生命周期，26.x 无 SAVE 回调），NeoForge 的 {@code LevelEvent.Save} 无
 * fabric 等价物。
 *
 * <p>javap 实证（minecraft-merged-deobf-26.1.2.jar）：
 * {@code public void save(net.minecraft.util.ProgressListener, boolean, boolean)}
 * —— ServerLevel 上的**具体**方法（含方法体；入口先按第二个 boolean 短路跳过），
 * TAIL 落在实际保存完成后；仅服务端调用（MinecraftServer#saveAllChunks 等），
 * 无需客户端过滤。两个 boolean 的语义未实证读通，载荷 {@code LevelSavedEventJS}
 * 只带 level（对齐 NeoForge {@code LevelEvent.Save} 的 getLevel()）。
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelSave {

    @Inject(method = "save(Lnet/minecraft/util/ProgressListener;ZZ)V", at = @At("TAIL"))
    private void nekojs$onSave(ProgressListener progressListener, boolean flush, boolean skipSave,
                               CallbackInfo ci) {
        FabricLevelEventBindingsV2.postSaved((ServerLevel) (Object) this);
    }
}
