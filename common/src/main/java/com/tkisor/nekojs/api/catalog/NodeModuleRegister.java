package com.tkisor.nekojs.api.catalog;

/**
 * 插件 JS 模块注册器：让插件提供自己的 CommonJS 风格 JS 模块，脚本可通过
 * {@code require('moduleId')}（或被其他模块依赖）加载。
 *
 * <p>用法（在 {@code registerNodeModules} 扩展点）：
 * <pre>{@code
 * registry.register("mymod:hello", "module.exports = { greet: (name) => `hi ${name}` }");
 * }</pre>
 *
 * <p>模块在沙盒初始化时由 {@code NekoNodeModuleInstaller} 用 CommonJS wrapper 求值
 * （注入 {@code module}/{@code exports}/{@code require}），再通过 {@code __nekoNodeDefine}
 * 注册到内置模块表，使 {@code require('mymod:hello')} 解析到 {@code module.exports}。
 *
 * <p>补全声明需插件另行通过 {@link TypeDocsRegister#registerManualDeclaration} 注册
 * {@code declare module 'mymod:hello' {...}}（probe 会输出到 {@code @manual/index.d.ts}）。
 */
public interface NodeModuleRegister {
    /**
     * @param moduleId require/import 时用的说明符（如 {@code "mymod:utils"}、{@code "mymod/utils"}）
     * @param source   CommonJS 模块源码（用 {@code module.exports} / {@code exports}）
     */
    void register(String moduleId, String source);
}
