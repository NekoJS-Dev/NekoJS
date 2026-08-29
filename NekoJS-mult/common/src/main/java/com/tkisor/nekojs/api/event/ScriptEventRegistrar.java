package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;

/**
 * 脚本事件注册器：支撑 {@code ScriptEvents.startup} 注册 API 的平台回调。
 * 启动阶段 {@link ScriptEvents#post} 会携带本接口实例向 SERVER/CLIENT 脚本广播
 * {@link ScriptEventRegistrationEvent}，脚本调用 {@code event.register(...)} 后最终
 * 落到 {@link #register}，由平台实现把原生平台事件桥接为脚本可监听的 {@link EventBusJS}
 * 并登记到 {@link ScriptEventRegistry} 以支持 reload 清理。
 */
public interface ScriptEventRegistrar {
    /**
     * 注册一个脚本事件。
     *
     * @param targetType       事件面向的脚本类型（SERVER 或 CLIENT）
     * @param groupName        事件组名（脚本侧访问前缀，如 {@code "ServerEvents"}）
     * @param eventName        事件名（组内访问名）
     * @param eventClass       平台事件类；通常为 Graal {@code Value}（Java 类或类名字符串），
     *                         由实现解析为具体 {@link Class}
     * @param priority         监听优先级名（如 {@code "normal"}；默认值由调用方给定）
     * @param receiveCancelled 是否接收已被取消的平台事件
     */
    void register(ScriptType targetType, String groupName, String eventName, Object eventClass, String priority, boolean receiveCancelled);
}
