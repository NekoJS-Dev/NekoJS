package com.tkisor.nekojs.mixin;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.List;

/**
 * NekoJS Minecraft mixin - hooks into game initialization.
 * 1.12.2: inject into init() instead of createDisplay().
 *
 * <p>{@code init()} RETURN 时把 {@code <gameDir>/nekojs/assets} 注册为
 * {@link FolderResourcePack}（{@code defaultResourcePacks} 在构造器中已初始化，
 * init 结束时必非 null）。该 pack 随每次资源 reload（F3+T）持久生效，使
 * {@code ClientEvents.generateAssets} 写入的资产 JSON 可被游戏读取。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Shadow
    @Final
    private List<IResourcePack> defaultResourcePacks;

    @Inject(method = "init", at = @At("RETURN"))
    public void nekojs$onInitComplete(CallbackInfo ci) {
        NekoJS.LOGGER.info("NekoJS Mixin loaded - Minecraft init complete!");
        try {
            File assets = NekoJSPaths.get().assets().toFile();
            defaultResourcePacks.add(new FolderResourcePack(assets));
        } catch (Exception e) {
            NekoJS.LOGGER.warn("Failed to register NekoJS assets resource pack", e);
        }
    }
}
