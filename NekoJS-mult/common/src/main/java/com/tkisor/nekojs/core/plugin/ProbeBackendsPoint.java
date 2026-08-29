package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.probe.ProbeBackendRegistry;

/**
 * 内置扩展点 {@code nekojs:probe_backends}（ADR-0001 自包含 Point 文件）。
 *
 * <p>累积器即 {@link ProbeBackendRegistry} 本身（registry-as-accumulator 形态）；
 * finisher 负责装配收尾：{@code lock()}（冲突 fail-fast + 密封）与全局单例注入——
 * 完整 reload 的二次 bootstrap 会整体替换单例而非崩溃。merge 策略声明为
 * {@link MergePolicy#failFast}（同一 {@code (languageId, name)} 在 lock 时报冲突，
 * 与 registry 内建语义一致）。
 */
public final class ProbeBackendsPoint {

    /** 扩展点 id（observable 契约）。 */
    public static final String ID = "nekojs:probe_backends";

    private ProbeBackendsPoint() {
    }

    /** 显式声明形态（与直接覆写 NekoJSPlugin 对应钩子等价收集）：插件被 {@code nekojs:probe_backends} 扩展点收集。 */
    public interface Contributor extends NekoJSPlugin {

        /**
         * 注册 probe backend（按 {@code (languageId, name)} 二维登记）。
         *
         * <p>内置 backend（TypeScript {@code .d.ts} 与 Python {@code .pyi}）由
         * {@code NekoProbeBuiltinPlugin} 经本方法注册——与第三方走同一路径。
         * 第三方可注册其他语言的 backend，或为已有语言提供替代 backend（不同 {@code name}）。
         * 同一 {@code (语言, 名字)} 在 bootstrap 结束（lock）时报冲突。
         */
        void registerProbeBackends(ProbeBackendRegistry registry);
    }

    /** 扩展点定义（由 {@link NekoBuiltinPointsPlugin} 清单注册）。 */
    public static final NekoPluginExtensionPoint<NekoJSPlugin, ProbeBackendRegistry, ProbeBackendRegistry> POINT =
            NekoPluginExtensionPoint.<NekoJSPlugin, ProbeBackendRegistry, ProbeBackendRegistry>builder(ID, NekoJSPlugin.class)
                    .merge(MergePolicy.failFast())
                    .initializer(context -> new ProbeBackendRegistry())
                    .collector(NekoJSPlugin::registerProbeBackends)
                    .finish(registry -> {
                        registry.lock();
                        ProbeBackendRegistry.setInstance(registry);
                        return registry;
                    })
                    .build();
}
