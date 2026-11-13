package com.tkisor.nekojs.core.dynamic.plan;

import java.util.Locale;
import java.util.Set;

/**
 * Builder for a dynamic MobEffect definition ({@code event.mobEffect(id, cb)}): HUD category and
 * ARGB color. The category is a closed set of strings matching vanilla MobEffectCategory
 * (beneficial/harmful/neutral); the Adapter resolves it to the MC enum at the apply point.
 */
public final class DynamicMobEffectBuilder extends DynamicDefinitionBuilder {

    public static final Set<String> CATEGORIES = Set.of("beneficial", "harmful", "neutral");

    private String category = "neutral";
    private int color = 0xFFFFFF;

    /** Category: neutral (default), beneficial or harmful. Case-insensitive; normalized to lower case. */
    public DynamicMobEffectBuilder setCategory(String category) {
        String normalized = DynamicDefinitionType.normalizeToken(category, "mob effect category");
        if (!CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown mob effect category '" + category.trim()
                    + "': expected 'beneficial', 'harmful' or 'neutral'");
        }
        this.category = normalized;
        return this;
    }

    /** Effect color as an ARGB int, for example {@code 0x8B0000}. */
    public DynamicMobEffectBuilder setColor(int color) {
        this.color = color;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public int getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "DynamicMobEffectBuilder[category=" + category + ", color=0x"
                + Integer.toHexString(color).toUpperCase(Locale.ROOT) + ", mode=" + getMode() + "]";
    }
}
