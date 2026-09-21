package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/**
 * 平台层在资源 reload 时对全部插件触发 generateData / generateAssets / generateLang
 * （直调型钩子：default 空实现，覆写即生效，无需任何注册/实现声明）。
 *
 * <p>插件 hook 先于脚本事件触发，与脚本共享同一 generator 实例（KubeJS 对齐）。
 * 单个插件异常被 try/catch 隔离，不中断其他插件。
 */
public final class PluginGenerationHooks {
    private PluginGenerationHooks() {}

    public static void fireGenerateData(DataGeneratorJS generator) {
        fire("generateData", ScriptType.SERVER, generator, NekoJSPlugin::generateData);
    }

    public static void fireGenerateAssets(DataGeneratorJS generator) {
        fire("generateAssets", ScriptType.CLIENT, generator, NekoJSPlugin::generateAssets);
    }

    public static void fireGenerateLang(LangGeneratorJS generator) {
        fire("generateLang", ScriptType.CLIENT, generator, NekoJSPlugin::generateLang);
    }

    /**
     * 解析本轮要生成的语言集合：插件 {@link NekoJSPlugin#generatedLangs()} 声明与脚本
     * keyed listener 的语言取并集，按字典序返回。
     *
     * <p>脚本语言由调用方经 {@code scriptRegisteredLangs} 传入（{@code ClientEvents.LANG
     * .registeredKeys()}）：事件总线是 MC-facing 类型，本类所在的 common 不能引用它——
     * 平台 Adapter 负责取键，本类只做合并、校验与排序。{@code null} 明确定义为「本节点没有
     * 脚本语言监听器」这一合法状态（非缺失），按空集参与并集；插件侧的 {@code null} 声明则相反，
     * 是违规（见下）。
     *
     * <p>确定性顺序是契约的一部分：{@code registeredKeys()} 来自 {@code Set.copyOf}，顺序不保证，
     * 而逐语言写入的先后可被观察（诊断顺序、同 key 覆盖的最终值），故用 {@link TreeSet} 归一。
     *
     * <p>默认 {@code en_us} 来自 {@link NekoJSPlugin#generatedLangs()} 的 default 实现，
     * 因此没有任何脚本 listener 时插件回调照常触发，不会被隐式门控跳过。
     *
     * <p>判据来自 {@link LangGeneratorJS#isValidLangCode(String)}——与写入阶段的检查同源，
     * 故预检通过的语言不会被 {@code writeTo} 以另一套规则拒绝。
     *
     * <p>校验在返回<b>之前</b>完整完成：任一非法语言代码使整批拒绝
     * （{@link IllegalStateException}，消息含 owner 与非法 code），调用方不得写入任何语言文件。
     * 单个插件的 {@code generatedLangs()} 抛异常只作废该插件的声明（记 error 日志）并继续，
     * 不污染其他插件与其他语言。
     */
    public static List<String> resolveGeneratedLangs(Collection<String> scriptRegisteredLangs) {
        TreeSet<String> langs = new TreeSet<>();
        List<String> violations = new ArrayList<>();

        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            Collection<String> declared;
            try {
                declared = plugin.generatedLangs();
            } catch (Exception e) {
                ScriptTypeEnv.logger(ScriptType.CLIENT)
                        .error("generatedLangs hook failed for " + plugin.getClass().getName()
                                + "; its language declarations are skipped", e);
                continue;
            }
            if (declared == null) {
                violations.add(plugin.getClass().getName() + ": null");
                continue;
            }
            for (String lang : declared) {
                if (!LangGeneratorJS.isValidLangCode(lang)) {
                    violations.add(plugin.getClass().getName() + ": " + lang);
                } else {
                    langs.add(lang);
                }
            }
        }

        if (scriptRegisteredLangs != null) {
            for (String lang : scriptRegisteredLangs) {
                if (!LangGeneratorJS.isValidLangCode(lang)) {
                    violations.add("script lang listener: " + lang);
                } else {
                    langs.add(lang);
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid generated language code(s); rejecting the whole language batch before any"
                            + " lang file is written: " + String.join(", ", violations));
        }
        return List.copyOf(langs);
    }

    /** 全员触发 + 异常隔离的公共形状：钩子名进日志、env 定 logger、generator 类型参数化。 */
    private static <G> void fire(String hook, ScriptType env, G generator,
            BiConsumer<NekoJSPlugin, G> call) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                call.accept(plugin, generator);
            } catch (Exception e) {
                ScriptTypeEnv.logger(env).error(hook + " hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }
}
