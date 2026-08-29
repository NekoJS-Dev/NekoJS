package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.api.ScriptType;

/**
 * 脚本事件注册器：支撑 {@code ScriptEvents.server/client} 注册 API 的平台回调。
 * 启动阶段 {@link ScriptEvents#post} 会携带本接口实例向 SERVER/CLIENT 脚本广播
 * {@link ScriptEventRegistrationEvent}，脚本调用 {@code event.register(...)} 后最终落到
 * {@link #register}，实现登记到 {@link ScriptEventRegistry} 以支持 reload 清理。
 *
 * <p>声明出来的事件由脚本自己触发（{@code 组.名.post(payload)}），不绑定任何平台原生事件——
 * "按类名挂原生事件"是加载器专属能力，由 {@code NativeEvents} 承担。
 */
public interface ScriptEventRegistrar {
    /**
     * 注册一个自定义脚本事件。
     *
     * @param targetType     事件面向的脚本类型（SERVER 或 CLIENT）
     * @param groupName      事件组名（脚本侧访问前缀，如 {@code "MyEvents"}）
     * @param eventName      事件名（组内访问名）
     * @param sourceScriptId 注册来源脚本 id（reload 时按来源清理）
     */
    void register(ScriptType targetType, String groupName, String eventName, String sourceScriptId);
}
