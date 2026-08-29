package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.core.fs.JSConfigModel;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

/**
 * 数据生成贡献面（ADR-0001 Point 文件形态；直发模式——平台层直接对实现者触发，
 * 与 {@code registerApiSurface} 同类，不做收集式扩展点）。
 *
 * <p>四个钩子原属 {@code NekoJSPlugin}，P1 收官时外迁至此：使 {@code NekoJSPlugin}
 * 归零引擎包签名类型（契约迁移 common-api 的前置，ADR-0007）。插件实现
 * {@link Contributor} 即被 {@link PluginGenerationHooks} 与 {@code WorkspaceGenerator} 触发。
 */
public final class GenerationPoint {

    private GenerationPoint() {
    }

    /** 贡献面：实现本接口的插件参与数据/资源/语言生成与工作区配置修改。 */
    public interface Contributor extends NekoJSPlugin {

        /** 数据生成（data pack）。插件 hook 先于脚本事件，共享同一 generator。 */
        default void generateData(DataGeneratorJS generator) {
        }

        /** 资源生成（resource pack）。 */
        default void generateAssets(DataGeneratorJS generator) {
        }

        /** 语言文件生成。 */
        default void generateLang(LangGeneratorJS generator) {
        }

        /** 修改工作区配置模型（engine.toml / probe.toml 等）。 */
        default void modifyWorkspaceConfig(JSConfigModel model, String env) {
        }
    }
}
