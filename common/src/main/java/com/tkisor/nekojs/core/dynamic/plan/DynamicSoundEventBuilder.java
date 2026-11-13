package com.tkisor.nekojs.core.dynamic.plan;

/**
 * Builder for a dynamic SoundEvent definition ({@code event.soundEvent(id, cb)}).
 * fixedRange defaults to null, which means the sound definition decides it. Writing
 * {@code b.fixedRange = null} explicitly reaches the same normalized state as never writing it,
 * the same way a null property suppresses a value.
 */
public final class DynamicSoundEventBuilder extends DynamicDefinitionBuilder {

    private Float fixedRange;

    /** Fixed audible range in blocks; null or unwritten means the sound definition decides. The write
     * site rejects non-finite values. */
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
