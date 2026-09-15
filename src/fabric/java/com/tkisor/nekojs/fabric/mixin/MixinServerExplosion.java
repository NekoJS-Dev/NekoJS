package com.tkisor.nekojs.fabric.mixin;

import com.tkisor.nekojs.fabric.event.FabricLevelEventBindingsV2;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code LevelEvents.explosionStart / explosionDetonate}（及 beforeExplosion /
 * afterExplosion 别名）的 fabric 挂点：{@code ServerExplosion#explode()} 的
 * HEAD / TAIL。
 *
 * <p>26.x 爆炸形态（javap 实证）：
 * <ul>
 *   <li>{@code Explosion} 是**接口**，实现 {@code ServerExplosion}——构造只发生在
 *       {@code ServerLevel.explode(...)}（全 jar 常量池扫描仅此一处
 *       {@code new ServerExplosion}），因此 {@code explode()} 的 HEAD/TAIL 等价于
 *       NeoForge 的 ExplosionEvent.Start/Detonate 时点（Start=结算前，
 *       Detonate=结算后，同 {@code ServerLevel.explode} 的爆炸包发出前）。</li>
 *   <li>{@code public int explode()}：{@code gameEvent(EXPLODE)} →
 *       {@code calculateExplodedPositions()} → {@code hurtEntities()} →
 *       {@code interactWithBlocks()}（若 interactsWithBlocks）→
 *       {@code createFire()}，返回受影响方块数；之后 {@code ServerLevel.explode}
 *       才向玩家发 {@code ClientboundExplodePacket}。26.x 无独立
 *       {@code finalizeExplosion()} 阶段——两个事件点安全分开。</li>
 *   <li>仅服务端：客户端爆炸走 {@code ClientLevel#explode} 的独立实现
 *       （javap 实证，ClientLevel 类在 client/multiplayer 包）与爆炸包渲染，
 *       不触达 {@code ServerExplosion}。</li>
 *   <li>载荷数据读 {@link Explosion} 接口公共成员（level/center/radius/
 *       getDirectSourceEntity/getIndirectSourceEntity），无需 AW。</li>
 * </ul>
 *
 * <p><b>取消语义说明（known caveat）</b>：HEAD 取消采用
 * {@code cir.setReturnValue(0)}（必填，int 返回）——爆炸本身（伤害/方块/火焰）
 * 被跳过，但 {@code ServerLevel.explode} 后续仍会对半径 64 内的玩家发出一个
 * {@code blockCount=0}、无受伤者的 {@code ClientboundExplodePacket}（视觉/音效
 * 仍播，无后果）。NeoForge 的 Start 取消是整方法打断（含不发包）。如需完全对齐，
 * 可换挂点：注入 {@code ServerLevel.explode} 的 HEAD 整体 {@code ci.cancel()}
 * （见接线清单给出的备选代码，代价是载荷拿不到 {@code Explosion} 对象，只能从
 * 方法参数组）。
 */
@Mixin(ServerExplosion.class)
public abstract class MixinServerExplosion {

    @Inject(method = "explode()I", at = @At("HEAD"))
    private void nekojs$onExplosionStart(CallbackInfoReturnable<Integer> cir) {
        if (FabricLevelEventBindingsV2.postExplosionStart((Explosion) (Object) this)) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "explode()I", at = @At("TAIL"))
    private void nekojs$onExplosionDetonate(CallbackInfoReturnable<Integer> cir) {
        FabricLevelEventBindingsV2.postExplosionDetonate((Explosion) (Object) this);
    }
}
