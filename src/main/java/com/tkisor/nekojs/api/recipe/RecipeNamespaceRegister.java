package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import java.util.function.Function;

@FunctionalInterface
public interface RecipeNamespaceRegister {
    /**
     * 注册一个配方命名空间处理器
     * @param namespace 命名空间 (例如 "minecraft", "botania")
     * @param factory 处理器的构造工厂 (通常传入 Handler::new)
     */
    void register(String namespace, Function<RecipeEventJS, Object> factory);
}