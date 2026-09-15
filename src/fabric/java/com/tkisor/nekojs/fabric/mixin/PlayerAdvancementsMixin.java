package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricPlayerEventBindings;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家进度事件（{@code PlayerEvents.advancement}）的 fabric 钩子。
 *
 * <p>NeoForge 26.1.2 触发点（userdev 补丁实证）：{@code PlayerAdvancements#award} 内
 * {@code if (!wasDone && progress.isDone()) { rewards.grant(...);
 * display.ifPresent(display -> { ...announce...; onAdvancementEarnedEvent(player, holder); }) }}
 * ——「刚完成」且<b>带 display</b> 的进度才触发（与 announce 的 gamerule 无关）。
 *
 * <p>注入点（javap -c minecraft-merged-deobf-26.1.2 实证）：{@code award} 的
 * {@code holder.value().display().ifPresent(...)}（偏移 70-84，InvokeDynamic #334）
 * 生成私有合成方法 {@code lambda$award$0(AdvancementHolder, DisplayInfo)}——其 HEAD 注入
 * 得到 holder + display（player 经 @Shadow 字段取），语义与 NeoForge 的 ifPresent 内触发
 * 完全一致（无 display 的进度不触发）。{@code rewards.grant} 调用点（偏移 67）虽同属
 * 「刚完成」分支，但它先于 display 检查——挂它会为无 display 的进度多发事件，未采用。
 *
 * <p>注意：{@code lambda$award$0} 是 private synthetic 方法，javac 在 MC 重构时可能改名——
 * defaultRequire=1 会在改名时硬失败（不静默丢钩子），届时照此注释改挂点为
 * {@code Optional.ifPresent} INVOKE + {@code @ModifyVariable} 捕获 holder。
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow private ServerPlayer player;

    @Inject(method = "lambda$award$0", at = @At("HEAD"))
    private void nekojs$onAdvancementEarned(AdvancementHolder holder, DisplayInfo display, CallbackInfo ci) {
        FabricPlayerEventBindings.postAdvancement(player, holder);
    }
}
