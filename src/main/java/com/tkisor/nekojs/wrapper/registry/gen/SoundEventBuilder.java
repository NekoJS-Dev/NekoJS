package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
//? if >=26 {
import java.util.Optional;
//?}

/**
 * 声音事件 builder（通用注册表体系的首个内置类型，垂直切片验证
 * {@code registry_types → 糖方法 → 先攒后建 → pass 抽干} 全链路）。
 *
 * <pre>
 * event.soundEvent('mymod:boom', b =&gt; { b.fixedRange = 16 })
 * </pre>
 */
public final class SoundEventBuilder extends RegistryObjectBuilder<SoundEvent> {

    /** 固定可听距离（格）；null 时由声音定义文件决定。 */
    private Float fixedRange;

    public SoundEventBuilder(Identifier id) {
        super(id);
    }

    /** 固定可听距离（null=由声音定义文件决定）。 */
    public Float getFixedRange() {
        return fixedRange;
    }

    public void setFixedRange(Float fixedRange) {
        if (fixedRange != null && fixedRange <= 0) {
            throw new IllegalArgumentException("fixedRange must be > 0 but got " + fixedRange);
        }
        this.fixedRange = fixedRange;
    }

    @Override
    public SoundEvent build() {
//? if >=26 {
        return new SoundEvent(id, Optional.ofNullable(fixedRange));
//?} else {
/*        return fixedRange == null
                ? SoundEvent.createVariableRangeEvent(id)
                : SoundEvent.createFixedRangeEvent(id, fixedRange);
*///?}
    }
}
