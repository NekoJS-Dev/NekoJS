package com.tkisor.nekojs.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为 NekoJS 插件。<p>
 * 插件类必须实现 {NekoJSPlugin} 接口。<p>
 * 插件类会在 NekoJS 初始化时被自动注册。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterNekoJSPlugin {
}
