package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import java.util.function.Function;

/**
 * 配方命名空间注册条目的统一封装。
 *
 * @param namespace 命名空间 (例如 "minecraft")
 * @param factory   处理器的构造工厂 (例如 {@code Handler::new})
 * @param handlerClass 处理器 Java 类型 (例如 {@code Handler.class})，供外部 mod 反射查询
 */
public record RecipeNamespaceEntry(
    String namespace,
    Function<RecipeEventJS, Object> factory,
    Class<?> handlerClass
) {}
