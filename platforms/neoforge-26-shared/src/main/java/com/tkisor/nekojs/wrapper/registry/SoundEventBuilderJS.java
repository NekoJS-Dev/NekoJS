package com.tkisor.nekojs.wrapper.registry;

import lombok.Getter;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.Optional;

/**
 * 声音事件注册器（{@code StartupEvents.registry('soundEvent')}）。
 *
 * <p>脚本可选 {@code fixedRange} 设固定可听距离；省略时由声音定义文件决定。
 */
public class SoundEventBuilderJS {
    @Getter
    private final Identifier location;

    private Float fixedRange;

    public SoundEventBuilderJS(Identifier location) {
        this.location = location;
    }

    /** 设固定可听距离（格）。 */
    public SoundEventBuilderJS fixedRange(float range) {
        this.fixedRange = range;
        return this;
    }

    public SoundEvent create() {
        return new SoundEvent(location, Optional.ofNullable(fixedRange));
    }
}
