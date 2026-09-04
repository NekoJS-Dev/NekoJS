package com.tkisor.nekojs.wrapper.event.level;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

/**
 * 爆炸事件（{@code LevelEvents.explosionStart} / {@code explosionDetonate}，及
 * {@code beforeExplosion} / {@code afterExplosion} 历史别名）的**加载器中立**载荷。
 *
 * <p>成员名对齐 NeoForge 26.x {@code ExplosionEvent}：{@code event.level} /
 * {@code event.explosion}（NeoForge 的 {@code getLevel()} / {@code getExplosion()}，
 * 其中 {@link Explosion} 是 26.x 原版**接口**，加载器无关）。便利 getter
 * {@code center/radius/sourceEntity/player/indirectSourceEntity} 直接读
 * {@code explosion} 的公共接口成员（{@code center()}/{@code radius()}/
 * {@code getDirectSourceEntity()}/{@code getIndirectSourceEntity()}）。
 *
 * <p>可取消：仅 {@code explosionStart} 可取消（NeoForge {@code ExplosionEvent.Start}
 * 语义——取消则整场爆炸不结算；fabric 侧由 {@code MixinServerExplosion} 在
 * {@code ServerExplosion#explode()} 入口拦截）。{@code explosionDetonate} 通知型。
 *
 * <p>fabric 实现说明：{@code MixinServerExplosion} 在 {@code ServerLevel#explode}
 * 构造的 {@code ServerExplosion#explode()} 前后投递，仅服务端（26.x 客户端爆炸逻辑
 * 走 {@code ClientLevel#explode} 的独立实现与 {@code ClientboundExplodePacket}，
 * 不经本类）。
 *
 * <p>**刻意不含 affectedBlocks / affectedEntities**：NeoForge 26.x 的
 * {@code ExplosionEvent.Detonate} 暴露 getAffectedBlocks()/getAffectedEntities()，
 * 但 26.x 原版 {@code ServerExplosion} 不保存这两份列表（javap 实证：只有
 * {@code explode()} 返回受影响方块数、getHitPlayers()、isSmall()，爆炸位置列表
 * 只在方法栈上临时计算）——中立载荷找不到数据源，不硬造。
 */
@Doc("Fired around an explosion (LevelEvents.explosionStart / explosionDetonate).")
@Getter
public class LevelExplosionEventJS extends LevelEventJS {

    @Doc("The explosion object (26.x vanilla Explosion interface; level, center, radius, source entity).")
    private final Explosion explosion;

    public LevelExplosionEventJS(ServerLevel level, Explosion explosion) {
        super(level);
        this.explosion = explosion;
    }

    @Doc("Explosion center position.")
    public Vec3 getCenter() {
        return explosion.center();
    }

    @Doc("Explosion radius.")
    public float getRadius() {
        return explosion.radius();
    }

    @Doc("The explosion's direct source entity (may be null).")
    public Entity getSourceEntity() {
        return explosion.getDirectSourceEntity();
    }

    @Doc("The player who caused the explosion, or null if not a player-source explosion.")
    public Player getPlayer() {
        return explosion.getDirectSourceEntity() instanceof Player player ? player : null;
    }

    @Doc("The entity the explosion damage is attributed to for credit/knockback (may be null).")
    public Entity getIndirectSourceEntity() {
        return explosion.getIndirectSourceEntity();
    }
}
