package com.tkisor.nekojs.core.module;

/**
 * Registers plugin-provided JavaScript modules. A plugin ships CommonJS-style modules that scripts
 * load with {@code require('moduleId')} or that other modules depend on.
 *
 * <p>Usage inside the {@code registerNodeModules} extension point:
 * <pre>{@code
 * registry.register("mymod:hello", "module.exports = { greet: (name) => `hi ${name}` }");
 * }</pre>
 *
 * <p>During sandbox initialization {@code NekoNodeModuleInstaller} evaluates the module with a
 * CommonJS wrapper (injecting {@code module}/{@code exports}/{@code require}) and registers it in
 * the built-in module table through {@code __nekoNodeDefine}, so {@code require('mymod:hello')}
 * resolves to {@code module.exports}.
 *
 * <p>Completion declarations are registered separately by the plugin through
 * {@link TypeDocsRegister#registerManualDeclaration} as
 * {@code declare module 'mymod:hello' {...}}; the probe writes them to
 * {@code @manual/index.d.ts}.
 */
public interface NodeModuleRegister {
    /**
     * @param moduleId specifier used for require/import, such as {@code "mymod:utils"} or
     *                 {@code "mymod/utils"}
     * @param source   CommonJS module source (uses {@code module.exports} / {@code exports})
     */
    void register(String moduleId, String source);
}
