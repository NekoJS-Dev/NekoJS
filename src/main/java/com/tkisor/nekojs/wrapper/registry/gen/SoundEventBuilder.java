package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
//? if >=26 {
import java.util.Optional;
//?}

/**
 * Builder for sound events. It is the first built-in type of the general registry system and
 * exercises the whole chain: {@code registry_types -> sugar methods -> collect first, build
 * later -> pass draining}.
 *
 * <pre>
 * event.soundEvent('mymod:boom', b =&gt; { b.fixedRange = 16 })
 * </pre>
 */
public final class SoundEventBuilder extends RegistryObjectBuilder<SoundEvent> {

    /** Fixed audible range in blocks. When null, the sound definition file decides. */
    private Float fixedRange;

    public SoundEventBuilder(Identifier id) {
        super(id);
    }

    /** Fixed audible range; null means the sound definition file decides it. */
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
        /*return fixedRange == null
                ? SoundEvent.createVariableRangeEvent(id)
                : SoundEvent.createFixedRangeEvent(id, fixedRange);
*///?}
    }
}
