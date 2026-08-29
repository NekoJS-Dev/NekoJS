package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;

import java.util.Map;

/**
 * 内置扩展点 {@code nekojs:client_events}（ADR-0001 自包含 Point 文件）——
 * <b>V2 依赖语义（ADR-0002）首个内置用例</b>：
 *
 * <ul>
 *   <li>时序依赖：{@code dependsOn(EventsPoint.POINT)}——仅需 events 先完成；</li>
 *   <li>数据依赖：initializer 里 {@code context.result(EventsPoint.POINT)} 读 events 产物
 *       作为累积器初始状态（客户端事件组与服务器事件组合并）——免声明，拓扑序保证安全；</li>
 *   <li>仅客户端收集；专用服务器进程整个点跳过，事件组产物回退为 events 产物
 *       （见 {@link EventsPoint#mergedEventGroups}）。</li>
 * </ul>
 */
public final class ClientEventsPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:client_events";

    private ClientEventsPoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:client_events} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册客户端事件组（仅客户端运行时可见，如 {@code ClientEvents.*}）。 */
        default void registerClientEvents(EventGroupRegistry registry) {
        }
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, EventGroupRegistry, Map<String, EventGroup>>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .clientOnly()
                    .dependsOn(EventsPoint.POINT)
                    .initializer(context -> {
                        EventGroupRegistry merged = new EventGroupRegistry.Impl();
                        Map<String, EventGroup> earlier = context.result(EventsPoint.POINT);
                        if (earlier != null) {
                            earlier.values().forEach(merged::register);
                        }
                        return merged;
                    })
                    .collector(NekoJSPlugin::registerClientEvents)
                    .finish(EventsPoint::freezeEventGroups)
                    .build();
}
