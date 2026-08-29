package com.tkisor.nekojs.core.plugin;

import org.slf4j.Logger;

/**
 * 扩展点合并策略——四档引擎标准件（ADR-0001）：{@code append}（列表式无冲突收集）、
 * {@code firstWin}（同键首胜 + warn）、{@code overrideWarn}（同键覆盖 + warn）、
 * {@code failFast}（同键冲突立即抛错）。
 *
 * <p>策略在扩展点定义时经 builder {@code merge(...)} 显式声明（不提供默认值）。
 * 键控累积器（bucket）在遇到同键贡献时调用 {@link #resolveDuplicate}，由策略决定
 * 保留旧值还是新值；列表式（append）累积器不调用本方法。
 */
public final class MergePolicy {

    /** 四档策略标识。 */
    public enum Tier {
        APPEND, FIRST_WIN, OVERRIDE_WARN, FAIL_FAST
    }

    private final Tier tier;

    private MergePolicy(Tier tier) {
        this.tier = tier;
    }

    /** 列表式无冲突收集：每条贡献都保留，不存在键控冲突。 */
    public static MergePolicy append() {
        return new MergePolicy(Tier.APPEND);
    }

    /** 同键首胜：后到贡献忽略 + warn（如 node_modules）。 */
    public static MergePolicy firstWin() {
        return new MergePolicy(Tier.FIRST_WIN);
    }

    /** 同键覆盖：新值替换旧值 + warn（声明式"后者胜"）。 */
    public static MergePolicy overrideWarn() {
        return new MergePolicy(Tier.OVERRIDE_WARN);
    }

    /** 同键冲突立即抛错（如 recipe_namespaces）。 */
    public static MergePolicy failFast() {
        return new MergePolicy(Tier.FAIL_FAST);
    }

    public Tier tier() {
        return tier;
    }

    /**
     * 键控 bucket 的冲突裁决点：同键第二条贡献到达时调用。
     *
     * @param pointId     扩展点 id（诊断用）
     * @param contributor 贡献方标识（通常为插件类名，诊断用）
     * @param key         冲突键
     * @param logger      告警输出
     * @return {@code true} 保留新值（覆盖），{@code false} 保留旧值（首胜）
     * @throws IllegalArgumentException {@code failFast} 档抛出
     * @throws IllegalStateException    {@code append} 档调用本方法属用法错误
     */
    public boolean resolveDuplicate(Object pointId, String contributor, String key, Logger logger) {
        return switch (tier) {
            case APPEND -> throw new IllegalStateException(
                    "Plugin extension point '" + pointId + "' declares append; keyed conflict on '"
                            + key + "' should not happen");
            case FIRST_WIN -> {
                logger.warn("Plugin extension point '{}' key '{}' already registered by a higher-priority "
                        + "plugin; ignoring duplicate (firstWin)", pointId, key);
                yield false;
            }
            case OVERRIDE_WARN -> {
                logger.warn("Plugin extension point '{}' key '{}' overridden by {} (overrideWarn)",
                        pointId, key, contributor);
                yield true;
            }
            case FAIL_FAST -> throw new IllegalArgumentException(
                    "Plugin extension point '" + pointId + "' conflict on key '" + key
                            + "' (failFast). Possible plugin conflict.");
        };
    }

    @Override
    public String toString() {
        return switch (tier) {
            case APPEND -> "append";
            case FIRST_WIN -> "firstWin";
            case OVERRIDE_WARN -> "overrideWarn";
            case FAIL_FAST -> "failFast";
        };
    }
}
