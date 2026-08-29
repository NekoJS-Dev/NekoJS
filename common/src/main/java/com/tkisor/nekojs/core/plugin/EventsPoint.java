package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.event.EventGroup;
import com.tkisor.nekojs.api.event.EventGroupRegistry;

import java.util.Map;

/**
 * 内置扩展点 {@code nekojs:events}（ADR-0001 自包含 Point 文件）。
 * merge 策略 {@link MergePolicy#failFast}（同名事件组冲突由 registry 注册时报错）。
 *
 * <p>孪生点 {@code nekojs:client_events}（{@link ClientEventsPoint}）依赖本点：
 * V2 依赖语义（ADR-0002）的内置首个用例。
 */
public final class EventsPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:events";

    private EventsPoint() {
    }

    /** 贡献面：实现本接口的插件被 {@code nekojs:events} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /** 注册服务端事件组（{@code ServerEvents.*}/{@code PlayerEvents.*} 等）。 */
        default void registerEvents(EventGroupRegistry registry) {
        }
    }

    static Map<String, EventGroup> freezeEventGroups(EventGroupRegistry registry) {
        Map<String, EventGroup> view = registry.view();
        view.values().forEach(EventGroup::freeze);
        return Map.copyOf(view);
    }

    /**
     * 事件组产物的合并视图：客户端进程用 client_events 产物（已并入 events 产物），
     */
    @SuppressWarnings("unchecked")
    public static Map<String, EventGroup> mergedEventGroups(Map<String, Object> products) {
        Map<String, EventGroup> clientGroups = (Map<String, EventGroup>) products.get(ClientEventsPoint.ID);
        if (clientGroups != null) {
            return clientGroups;
        }
        Map<String, EventGroup> groups = (Map<String, EventGroup>) products.get(ID);
        if (groups == null) {
            throw new IllegalStateException("Extension point '" + ID + "' produced no product for this bootstrap");
        }
        return groups;
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<Contributor, EventGroupRegistry, Map<String, EventGroup>> POINT =
            NekoPluginExtensionPoint.<Contributor, EventGroupRegistry, Map<String, EventGroup>>builder(ID, Contributor.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new EventGroupRegistry.Impl())
                    .collector(Contributor::registerEvents)
                    .finish(EventsPoint::freezeEventGroups)
                    .build();
}
