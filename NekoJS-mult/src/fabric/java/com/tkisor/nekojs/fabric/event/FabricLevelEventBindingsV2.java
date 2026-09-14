package com.tkisor.nekojs.fabric.event;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.bindings.event.LevelEvents;
import com.tkisor.nekojs.wrapper.event.level.LevelExplosionEventJS;
import com.tkisor.nekojs.wrapper.event.level.LevelSavedEventJS;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;

/**
 * 维度事件面的 fabric 桥 v2：saved / explosionStart / explosionDetonate
 * （+ {@code beforeExplosion} / {@code afterExplosion} 历史别名）。
 *
 * <p>总线声明在**本类**、注册进节点孪生 {@link LevelEvents} 的 {@code GROUP}
 * （同名组、加载时合并）——与 v1 的 loaded/unloaded/tickPre/tickPost 同命名空间，
 * 脚本看到的是一个完整的 {@code LevelEvents}。按 {@code NeoForgeBlockEvents}/
 * 共享树 {@code LevelEvents} 的命名与别名关系对齐：
 * <ul>
 *   <li>{@code saved} —— NeoForge {@code LevelEvent.Save}（不可取消）；</li>
 *   <li>{@code explosionStart} / {@code beforeExplosion}(Deprecated) ——
 *       NeoForge {@code ExplosionEvent.Start}（可取消）；</li>
 *   <li>{@code explosionDetonate} / {@code afterExplosion}(Deprecated) ——
 *       NeoForge {@code ExplosionEvent.Detonate}（通知型）。</li>
 * </ul>
 *
 * <p>**关键初始化纪律**：{@code EventGroup} 会在插件引导完成后冻结
 * （见 {@code EventsPoint#freezeEventGroups}）——本类总线字段的 {@code GROUP.add}
 * 必须发生在冻结之前。时序：{@code NekoJSFabricMod.onInitialize} 先跑各
 * {@code register()}（早于 {@code initializeScripts()} 的插件引导/冻结），
 * 因此 {@link #bootstrap()} 在 {@code FabricLevelEventBindings.register()} 内
 * 追加一行即可（接线清单给出），切勿等到首个 mixin post（届时已冻结、
 * {@code GROUP.add} 抛 IllegalStateException）。
 */
public final class FabricLevelEventBindingsV2 {

    private FabricLevelEventBindingsV2() {}

    /** 维度保存完成（server 脚本，不可取消）。载荷 {@link LevelSavedEventJS}。 */
    public static final EventBusJS<LevelSavedEventJS, Void> SAVED =
            LevelEvents.GROUP.server("saved", LevelSavedEventJS.class);

    /** 爆炸开始前（server 脚本；可取消——取消则整场爆炸不结算）。 */
    public static final EventBusJS<LevelExplosionEventJS, Void> EXPLOSION_START =
            LevelEvents.GROUP.add("explosionStart", ScriptType.SERVER,
                    EventBusJS.of(LevelExplosionEventJS.class, true));

    /** beforeExplosion：explosionStart 的历史别名（NeoForge 侧同款 Deprecated）。 */
    @Deprecated
    public static final EventBusJS<LevelExplosionEventJS, Void> BEFORE_EXPLOSION =
            LevelEvents.GROUP.add("beforeExplosion", ScriptType.SERVER,
                    EventBusJS.of(LevelExplosionEventJS.class, true));

    /** 爆炸结算完成（server 脚本，通知型）。 */
    public static final EventBusJS<LevelExplosionEventJS, Void> EXPLOSION_DETONATE =
            LevelEvents.GROUP.server("explosionDetonate", LevelExplosionEventJS.class);

    /** afterExplosion：explosionDetonate 的历史别名（NeoForge 侧同款 Deprecated）。 */
    @Deprecated
    public static final EventBusJS<LevelExplosionEventJS, Void> AFTER_EXPLOSION =
            LevelEvents.GROUP.server("afterExplosion", LevelExplosionEventJS.class);

    /**
     * 触发本类初始化，把上面的总线注册进 {@code LevelEvents.GROUP}。
     * 必须在事件组冻结（插件引导）前调用：唯一安全时机是
     * {@code NekoJSFabricMod.onInitialize} 早期（{@code FabricLevelEventBindings.register()}
     * 内追加一行本调用）。
     */
    public static void bootstrap() {
        // 空方法体：访问任一总线常量即完成类初始化
    }

    /**
     * 维度保存完成：{@code MixinServerLevelSave} 在
     * {@code ServerLevel#save(ProgressListener, boolean, boolean)} 的 TAIL 调用。
     */
    public static void postSaved(ServerLevel level) {
        if (!SAVED.hasListeners()) {
            return;
        }
        SAVED.post(new LevelSavedEventJS(level));
    }

    /**
     * 爆炸开始前：{@code MixinServerExplosion} 在 {@code ServerExplosion#explode()}
     * 的 HEAD 调用。
     *
     * @return true 表示监听器取消了爆炸（mixin 应跳过 {@code explode()} 执行）
     */
    public static boolean postExplosionStart(Explosion explosion) {
        if (!EXPLOSION_START.hasListeners() && !BEFORE_EXPLOSION.hasListeners()) {
            return false;
        }
        LevelExplosionEventJS payload = new LevelExplosionEventJS(explosion.level(), explosion);
        boolean cancelled = EXPLOSION_START.post(payload);
        cancelled |= BEFORE_EXPLOSION.post(payload);
        return cancelled;
    }

    /**
     * 爆炸结算完成：{@code MixinServerExplosion} 在 {@code ServerExplosion#explode()}
     * 的 TAIL 调用（爆炸伤害/方块破坏/火焰已结算完，客户端爆炸包已由
     * {@code ServerLevel.explode} 后续发出——见接线清单的取消语义说明）。
     */
    public static void postExplosionDetonate(Explosion explosion) {
        if (!EXPLOSION_DETONATE.hasListeners() && !AFTER_EXPLOSION.hasListeners()) {
            return;
        }
        LevelExplosionEventJS payload = new LevelExplosionEventJS(explosion.level(), explosion);
        EXPLOSION_DETONATE.post(payload);
        AFTER_EXPLOSION.post(payload);
    }
}
