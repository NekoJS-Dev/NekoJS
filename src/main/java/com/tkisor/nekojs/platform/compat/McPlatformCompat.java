//? if neoforge {
// NeoForge 侧门面:签名引用 NeoForge 专属类型(PayloadRegistrar),fabric 节点不编译本文件
// (fabric 的网络通道走 FabricPlayNetwork 自有实现)。
package com.tkisor.nekojs.platform.compat;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ServiceLoader;

/**
 * 双端（client+server）通用符号差异的版本门面（从 NekoJSNetwork 的
 * 内联守卫收拢而来）。覆盖三类差异：
 * <ul>
 *   <li>dist 检查：26.x 是 {@code FMLEnvironment.getDist()}、1.21.1 是字段
 *       {@code FMLEnvironment.dist}；</li>
 *   <li>OP 权限检查：26.x 用 {@code Commands.LEVEL_GAMEMASTERS.check(permissions())}、
 *       1.21.1 是 {@code Player#hasPermissions(2)}；</li>
 *   <li>NekoScriptPayload 的双向注册：26.x 的 {@code playBidirectional} 是 4 参（分别指定
 *       两端 handler），1.21.1 只有 3 参（handler 收 flow 判别）——整个注册操作下沉到实现。</li>
 * </ul>
 * 实现在各版本（{@code Nf1211PlatformCompat} / {@code Nf261PlatformCompat} /
 * {@code Nf262PlatformCompat}，类名刻意不同——drift 比较对不允许同名不同体），经
 * {@code META-INF/services} 由 ServiceLoader 在首次取用时解析。
 *
 * <p>签名只引用两端都存在且双端可加载的类型；客户端专属符号走 {@link McClientCompat}。
 */
public final class McPlatformCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McPlatformCompat.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No McPlatformCompat.Impl provider on classpath: expected a per-version impl "
                            + "(META-INF/services)"));

    private McPlatformCompat() {}

    public static Impl get() {
        return IMPL;
    }

    public interface Impl {
        /** 当前进程是否客户端侧（26.x：{@code FMLEnvironment.getDist()}；1.21.1：{@code FMLEnvironment.dist}）。 */
        boolean isClientDist();

        /** 是否游戏管理员（26.x：{@code Commands.LEVEL_GAMEMASTERS.check}；1.21.1：{@code hasPermissions(2)}）。 */
        boolean isGameMaster(Player player);

        /** NekoScriptPayload 的双向注册（注册形状整操作下沉到各版本实现）。 */
        void registerScriptPayload(PayloadRegistrar registrar);

        /**
         * Dynamic Registry 批事务同步 payload（{@code nekojs:dynamic_registry_sync}）的双向注册
         * （票 21 平台接线；注册形状与 NekoScriptPayload 相同的 playBidirectional）。默认空实现＝
         * 该节点没有动态注册面（1.21.1 整包 {@code >=26} 守卫缺席、fabric 显式子集），不是故障
         * 屏蔽——{@code NekoJSNetwork} 对所有 NeoForge 节点统一调用本方法，注册只发生在 26.x
         * 实现（Nf261/Nf262），其余节点保持票 17 冻结的显式 payload 子集。
         */
        default void registerDynamicSyncPayload(PayloadRegistrar registrar) {
        }
    }
}
//?}
