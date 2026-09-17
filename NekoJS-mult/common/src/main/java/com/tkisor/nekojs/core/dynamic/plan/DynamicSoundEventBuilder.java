package com.tkisor.nekojs.core.dynamic.plan;

/**
 * SoundEvent 动态定义 builder（{@code event.soundEvent(id, cb)}）。
 * fixedRange 缺省为 null（由声音定义决定）；显式 {@code b.fixedRange = null} 与
 * 从未写入是同一规范化状态（票 15「null property ≡ 抑制」语义同款处理）。
 */
public final class DynamicSoundEventBuilder extends DynamicDefinitionBuilder {

    private Float fixedRange;

    /** 固定可闻范围（方块数）；null/未写入 = 由声音定义决定。写入点拒绝非有限值。 */
    public DynamicSoundEventBuilder setFixedRange(Float range) {
        if (range != null && (range.isNaN() || range.isInfinite())) {
            throw new IllegalArgumentException("Invalid fixedRange " + range + ": must be a finite number or null");
        }
        this.fixedRange = range;
        return this;
    }

    public Float getFixedRange() {
        return fixedRange;
    }

    @Override
    public String toString() {
        return "DynamicSoundEventBuilder[fixedRange=" + fixedRange + ", mode=" + getMode() + "]";
    }
}
