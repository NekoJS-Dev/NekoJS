package com.tkisor.nekojs.inject;

import com.tkisor.nekojs.api.annotation.StaticInjector;

/**
 * {@code net.minecraft.world.item.Item} 的静态扩展载体（非 mixin 类）。
 * 带 {@link StaticInjector} 的方法会被 {@code NekoMixinPlugin} 复制到 Item 类。
 *
 * <p>PoC 阶段：不引用 MC API，仅验证「static 方法能被注入到 MC 类并运行时可调」这个机制。
 */
public final class ItemStaticExtensions {
    private ItemStaticExtensions() {}

    /**
     * 注入一个固定返回值的静态方法，验证 static injection 机制在运行时生效。
     * 脚本侧 {@code Item.neko$ping()}（或经 remap {@code Item.ping()}）应返回该字符串。
     */
    @StaticInjector("net.minecraft.world.item.Item")
    public static String neko$ping() {
        return "nekojs-static-inject-ok";
    }
}
