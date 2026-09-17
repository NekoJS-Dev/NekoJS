package com.tkisor.nekojs.core.dynamic.facade;

import com.tkisor.nekojs.api.event.EventBusJS;
import com.tkisor.nekojs.api.event.EventGroup;

/**
 * 服务器运行期动态注册的事件 facade（ticket 16，AC1/AC2）：与启动期
 * {@code RegistryEvents.register} 的生命周期和语义完全分离——本总线在
 * 「初次 server registry ready」与每次事务式 script/data reload 的候选阶段触发，
 * 脚本回调只提交 <b>inert 候选计划</b>，不在脚本线程即时执行 registry mutation。
 *
 * <h2>JS API（SERVER 脚本）</h2>
 * <pre>
 * DynamicRegistryEvents.dynamicRegistry(event => {
 *     event.item('mymod:ruby', b => { b.maxStackSize = 16; b.rarity = 'epic' })
 *     event.soundEvent('mymod:boom', b => { b.fixedRange(16) })
 *     event.mobEffect('mymod:wither_touch', b => { b.category('harmful').color(0x8B0000) })
 * })
 * </pre>
 *
 * <p><b>命名决策</b>（票面 AC2 的记录义务）：spec 08 给的工作名是
 * {@code ServerEvents.dynamicRegistry}（「工作名，非最终 API」）。实施采用独立组
 * {@code DynamicRegistryEvents}：ServerEvents 住在 NeoForge 专属树且 fabric 侧是
 * 另一份同 FQCN 文件，把运行期 facade 挂进去会把独立生命周期焊死在 loader 专属组上，
 * 也令 common 层无法承载/测试该面；独立组与启动期 {@code RegistryEvents}（同样独立
 * 组）对称表达「两个生命周期」。类型直达入口名保持票面冻结的
 * {@code event.item/soundEvent/mobEffect}。该决策进入本组 declaration/contract 派生
 * 与迁移表（baseline MIGRATION.md）。
 */
public final class DynamicRegistryEvents {

    /** 组名（独立于 ServerEvents，见类注释命名决策）。 */
    public static final EventGroup GROUP = EventGroup.of("DynamicRegistryEvents");

    /** 唯一收集总线（SERVER 脚本监听；载荷是 ProxyObject payload，成员目录冻结为三个类型直达入口）。 */
    public static final EventBusJS<DynamicRegistryEventJS, Void> DYNAMIC_REGISTRY =
            GROUP.server("dynamicRegistry", DynamicRegistryEventJS.class);

    private DynamicRegistryEvents() {}
}
