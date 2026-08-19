package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;

/**
 * 客户端渲染器插件：CLIENT 脚本每次加载（含完整 reload）前清空
 * {@link ClientRenderRegistry} 的全部 HUD / 世界渲染器，防止旧脚本的
 * Graal Value 回调残留。渲染事件订阅由 {@link ClientRenderEvents}
 * （{@code @EventBusSubscriber}）完成，本类只负责 reload 清理。
 *
 * <p>注册入口总线（{@code ClientEvents.hudRender}/{@code worldRender}）由
 * {@code ClientEvents} 接口字段静态初始化加入事件组，无需在此注册。
 */
@RegisterNekoJSPlugin(clientOnly = true)
public class ClientRenderPlugin implements NekoJSPlugin {

    @Override
    public void beforeScriptsLoaded(ScriptType type) {
        if (type == ScriptType.CLIENT) {
            ClientRenderRegistry.clearAll();
        }
    }
}
