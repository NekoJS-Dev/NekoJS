package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * 声音事件注册器（{@code StartupEvents.registry('soundEvent')}）。
 *
 * <p>脚本可选 {@code fixedRange} 设固定可听距离；省略时由声音定义文件决定。
 */
public class SoundEventBuilderJS {
    @Getter
    private final ResourceLocation location;

    private Float fixedRange;

    public SoundEventBuilderJS(ResourceLocation location) {
        this.location = location;
    }

    /** 设固定可听距离（格）。 */
    public SoundEventBuilderJS fixedRange(float range) {
        this.fixedRange = range;
        return this;
    }

    public SoundEvent create() {
        // 1.21.1: 公开静态工厂。fixedRange 未设置时用可变范围（让 sounds.json 决定）。
        return fixedRange == null
                ? SoundEvent.createVariableRangeEvent(location)
                : SoundEvent.createFixedRangeEvent(location, fixedRange);
    }
}
