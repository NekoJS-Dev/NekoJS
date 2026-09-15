package com.tkisor.nekojs.wrapper.registry.gen;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 状态效果 builder：类别决定 HUD 图标底色，颜色为 ARGB int。
 * <pre>
 * event.mobEffect('mymod:wither_touch', b =&gt; { b.category = 'harmful'; b.color = 0x8B0000 })
 * </pre>
 */
public class MobEffectBuilder extends RegistryObjectBuilder<MobEffect> {

    /** 类别名：beneficial / harmful / neutral（默认 neutral）。 */
    private String category = "neutral";
    /** ARGB 颜色（如 0x8B0000）。 */
    private int color = 0xFFFFFF;

    public MobEffectBuilder(Identifier id) {
        super(id);
    }

    /** 类别名（已归一化小写）。 */
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
        // MobEffect 的构造器为 protected，用匿名子类实例化（不做任何方法覆盖）。
        return new MobEffect(resolved, color) {};
    }
}
