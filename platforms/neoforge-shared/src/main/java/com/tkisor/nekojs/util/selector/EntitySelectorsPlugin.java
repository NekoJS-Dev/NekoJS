package com.tkisor.nekojs.util.selector;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.RegisterNekoJSPlugin;
import com.tkisor.nekojs.api.data.BindingRegistry;

/**
 * 全局绑定 {@code EntitySelectors} 注册插件：向 SERVER 与 TEST 脚本环境暴露
 * 程序化实体选择器入口（{@link EntitySelectorsJS}）。查询需要 {@code ServerLevel}，
 * 客户端 / startup 环境不注册（预构建 selector 的场景建议在 server 脚本内完成）。
 */
@RegisterNekoJSPlugin
public class EntitySelectorsPlugin implements NekoJSPlugin {

    @Override
    public void registerBinding(BindingRegistry registry) {
        if (registry.scriptType() == ScriptType.SERVER || registry.scriptType() == ScriptType.TEST) {
            registry.register("EntitySelectors", new EntitySelectorsJS());
        }
    }
}
