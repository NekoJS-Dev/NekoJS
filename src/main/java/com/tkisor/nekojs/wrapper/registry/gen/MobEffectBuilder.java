package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Status effect builder. The category sets the background color of the HUD icon; the color is an
 * ARGB int.
 * <pre>
 * event.mobEffect('mymod:wither_touch', b =&gt; { b.category = 'harmful'; b.color = 0x8B0000 })
 * </pre>
 */
public class MobEffectBuilder extends RegistryObjectBuilder<MobEffect> {

    /** Category name: beneficial / harmful / neutral. Defaults to neutral. */
    private String category = "neutral";
    /** ARGB color, such as 0x8B0000. */
    private int color = 0xFFFFFF;

    public MobEffectBuilder(Identifier id) {
        super(id);
    }

    /** Category name, already lowercased. */
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? "neutral" : category.toLowerCase();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    @Override
    public MobEffect build() {
        MobEffectCategory resolved = switch (category == null ? "neutral" : category.toLowerCase()) {
            case "beneficial" -> MobEffectCategory.BENEFICIAL;
            case "harmful" -> MobEffectCategory.HARMFUL;
            default -> MobEffectCategory.NEUTRAL;
        };
        // The MobEffect constructor is protected, so this instantiates an anonymous subclass and overrides
        // nothing.
        return new MobEffect(resolved, color) {};
    }
}
