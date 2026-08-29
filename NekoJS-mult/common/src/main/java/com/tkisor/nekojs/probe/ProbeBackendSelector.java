package com.tkisor.nekojs.probe;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /nekojs probe} 子命令的 backend 解析（公共实现，各平台命令层共用——此前同一段
 * 选择逻辑在 3 个平台的 NekoJSCommands 里逐字重复）。{@link ProbeBackendRegistry} 只负责
 * 登记/查找/冲突检查；语言默认、per-language 配置覆盖、跨语言全选、补全候选在此收敛。
 *
 * <p>语义（与既有各平台实现一致）：
 * <ul>
 *   <li>无参默认：只跑 {@code typescript:builtin}（{@code "builtin"} 是各平台命令层的字符串契约）</li>
 *   <li>{@code <语言>}：{@code probe.toml [languages.<lang>].backend} 指定的名字优先
 *       （按 (语言, 名字) 精确选取，找不到回退注册表默认）；无配置取该语言 priority 最高者</li>
 *   <li>{@code <语言> <名字>}：精确 (语言, 名字)</li>
 * </ul>
 */
public final class ProbeBackendSelector {

    private ProbeBackendSelector() {
    }

    /** {@code /nekojs probe} 无参：默认只跑 TS builtin。 */
    public static List<ProbeBackend> defaultTypescript() {
        return ProbeBackendRegistry.get().backend("typescript", "builtin")
                .map(List::of).orElse(List.of());
    }

    /** {@code /nekojs probe all}：所有已注册 backend（跨语言）。 */
    public static List<ProbeBackend> all() {
        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        List<ProbeBackend> result = new ArrayList<>();
        for (String lang : registry.languages()) {
            result.addAll(registry.backendsFor(lang));
        }
        return result;
    }

    /** {@code /nekojs probe <语言>}：per-language 配置优先，回退该语言 priority 最高的默认。 */
    public static List<ProbeBackend> forLanguage(String languageId) {
        ProbeBackendRegistry registry = ProbeBackendRegistry.get();
        // per-language 配置（probe.toml [languages.<lang>].backend）优先：指定了 backend 名时按 (语言, 名字) 精确选取，
        // 找不到再回退该语言的注册表默认（priority 最高者）；无配置则维持现状（defaultBackend）。
        var langCfg = ProbeCoordinator.config().language(languageId);
        if (langCfg.isPresent()) {
            String configuredName = langCfg.get().backend();
            if (configuredName != null && !configuredName.isBlank()) {
                var configured = registry.backend(languageId, configuredName);
                if (configured.isPresent()) return List.of(configured.get());
            }
        }
        return registry.defaultBackend(languageId)
                .map(List::of).orElse(List.of());
    }

    /** {@code /nekojs probe <语言> <名字>}：精确 (语言, 名字)。 */
    public static List<ProbeBackend> named(String languageId, String name) {
        return ProbeBackendRegistry.get().backend(languageId, name)
                .map(List::of).orElse(List.of());
    }

    /** 语言补全候选（已注册语言，registry 内已按字典序）。 */
    public static List<String> languageSuggestions() {
        return List.copyOf(ProbeBackendRegistry.get().languages());
    }

    /** 该语言下的名字补全候选（按 priority 降序，与展示顺序一致）。 */
    public static List<ProbeBackend> nameSuggestions(String languageId) {
        return ProbeBackendRegistry.get().backendsFor(languageId);
    }
}
