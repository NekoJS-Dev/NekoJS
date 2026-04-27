package com.tkisor.nekojs.api.recipe;

import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;

/**
 * 配方命名空间处理器的抽象基类。
 * <p>
 * 所有配方命名空间处理器（如 {@code MinecraftRecipeHandler}）应当继承此类，
 * 并提供一个以 {@link RecipeEventJS} 为参数的公开构造器。
 * </p>
 * <p>
 * 这样一来，插件注册时只需传处理器的 {@code .class}，
 * NekoJS 内部即可通过反射自动构造实例，同时保留类型信息供外部 mod 查询。
 * </p>
 *
 * <pre>{@code
 * public class MyHandler extends RecipeNamespaceHandler {
 *     public MyHandler(RecipeEventJS event) {
 *         super(event);
 *     }
 *     // ... custom methods ...
 * }
 * }</pre>
 *
 * @author Tki_sor
 * @since 1.1
 */
public abstract class RecipeNamespaceHandler {
    protected final RecipeEventJS event;

    public RecipeNamespaceHandler(RecipeEventJS event) {
        this.event = event;
    }
}