package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在普通载体类（<b>非</b> mixin 类）的 {@code public static} 方法上，由 NekoJS mixin plugin
 * 在 {@code postApply} 阶段把该方法（javac 已编译的字节码，含正确的 maxStack/locals 与类引用 mapping）
 * 整体复制到 {@link #value()} 指定的目标类，作为该目标类的 public static 方法。
 *
 * <p>用途：给 Minecraft 原生类（如 {@code net.minecraft.world.item.Item}）注入静态工厂方法，
 * 绕过 SpongePowered Mixin「禁止注入 public static 方法到 target」的硬限制（Mixin 在
 * {@code MixinApplicatorStandard.checkMethodVisibility} 直接抛 {@code InvalidMixinException}）。
 *
 * <p>方法体里的 MC 类引用由 javac 编译 + 普通 reobf 流程处理，不手搓 ASM，避开 refmap / maxStack 坑。
 * 复制后的方法在运行时属于 target 类，{@code invokestatic} target 自身的静态成员会正常分派到自身。
 *
 * <p>注意：载体类本身不会被 Mixin 扫描（它不在 mixin 包），它只是方法的「字节码来源」。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StaticInjector {
    /** 目标类全限定名（{@code com.example.Foo} 形式），如 {@code "net.minecraft.world.item.Item"}。 */
    String value();
}
