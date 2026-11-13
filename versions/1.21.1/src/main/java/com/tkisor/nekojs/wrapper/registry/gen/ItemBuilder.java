// 1.21.1 implementation, paired with the same-named 26.x file under src/ in the version tree.
// Its content equals that file evaluated at this node. Keep this file in sync when the 26.x
// side changes behavior.
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.FoodBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builds an item. Assigning a property and calling its explicit setter write through the same
 * path:
 * <pre>
 * event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16; b.rarity = 'epic' })
 * </pre>
 * Composite configuration (food) stays a void method. Glowing and fuel use an anonymous subclass
 * only when a method must be overridden.
 */
public class ItemBuilder extends RegistryObjectBuilder<Item> {

    /** Items assigned to a creative tab: item id to tab id, consumed during BuildCreativeModeTabContents. */
    public static final Map<ResourceLocation, ResourceLocation> GROUP_ASSIGNMENTS = new HashMap<>();

    private int maxStackSize = 64;
    private int maxDamage = 0;
    private boolean fireResistant = false;
    /** Rarity name: common / uncommon / rare / epic. Defaults to common; the setter lowercases it. */
    private String rarity = "common";
    private boolean glowing = false;
    /** Fuel burn time in ticks. Above 0 the item burns in a furnace, blast furnace or smoker. */
    private int burnTime = 0;
    /** Optional creative tab id, such as 'minecraft:building_blocks' or a custom tab id. {@code null} assigns none. */
    private String groupTab = null;

    private FoodBuilderJS foodBuilder = null;
    private final com.tkisor.nekojs.core.registry.ItemDefinitionConfig definition =
            new com.tkisor.nekojs.core.registry.ItemDefinitionConfig();

    public ItemBuilder(ResourceLocation id) {
        super(id);
    }

    // ---- Data properties: explicit setters and JavaBean properties share one write path ----

    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** Maximum stack size (1-99). MC fails on out-of-range values when the registry freezes, so this
     * rejects them on write with an error that names the member. */
    public void setMaxStackSize(int maxStackSize) {
        definition.writable();
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize must be in [1, 99] but got " + maxStackSize);
        }
        this.maxStackSize = maxStackSize;
    }

    public int getMaxDamage() {
        return maxDamage;
    }

    public void setMaxDamage(int maxDamage) {
        definition.writable();
        if (maxDamage < 0) {
            throw new IllegalArgumentException("maxDamage must be >= 0 but got " + maxDamage);
        }
        this.maxDamage = maxDamage;
    }

    public boolean isFireResistant() {
        return fireResistant;
    }

    public void setFireResistant(boolean fireResistant) {
        definition.writable();
        this.fireResistant = fireResistant;
    }

    /** Rarity name, lowercased by the setter. {@code null} is tolerated; the build path falls back to
     * common. */
    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        definition.writable();
        this.rarity = rarity == null ? null : rarity.toLowerCase();
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        definition.writable();
        this.glowing = glowing;
    }

    public int getBurnTime() {
        return burnTime;
    }

    public void setBurnTime(int burnTime) {
        definition.writable();
        if (burnTime < 0) {
            throw new IllegalArgumentException("burnTime must be >= 0 but got " + burnTime);
        }
        this.burnTime = burnTime;
    }

    /** Creative tab id, normalized on write: blank means no assignment, the same branch the build path
     * takes. */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getGroupTab() {
        return groupTab;
    }

    public void setGroupTab(@com.tkisor.nekojs.api.annotation.ScriptNullable String groupTab) {
        definition.writable();
        this.groupTab = groupTab == null || groupTab.isBlank() ? null : groupTab;
    }

    /** Configures the food properties (nutrition, saturation, effects and so on); composite settings
     * stay a method call. */
    public void food(Consumer<FoodBuilderJS> consumer) {
        definition.writable();
        this.foodBuilder = new FoodBuilderJS();
        consumer.accept(this.foodBuilder);
    }

    /** Adds one JSON component; repeated identifiers replace the explicit value. */
    public void component(String id, Object value) { definition.component(id, value); }
    public Map<String, Object> getComponents() { return definition.components(); }
    public void setComponents(Map<String, Object> components) { definition.components(components); }
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getJukeboxPlayable() { return definition.jukeboxPlayable(); }
    public void setJukeboxPlayable(@com.tkisor.nekojs.api.annotation.ScriptNullable String song) { definition.jukeboxPlayable(song); }

    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getParentModel() { return definition.parentModel(); }
    public void setParentModel(@com.tkisor.nekojs.api.annotation.ScriptNullable String parentModel) { definition.parentModel(parentModel); }
    public Map<String, String> getTextures() { return definition.textures(); }
    public void setTextures(Map<String, String> textures) { definition.textures(textures); }
    public void texture(String textureId) { texture("layer0", textureId); }
    public void texture(String slot, String textureId) { definition.texture(slot, textureId); }

    @Override
    void freezeDefinition() {
        if (foodBuilder != null) foodBuilder = foodBuilder.snapshot();
        definition.freeze();
    }

    com.tkisor.nekojs.core.registry.ItemAssetDefinition assetDefinition() {
        return definition.assets(id.toString());
    }

    java.util.function.Supplier<Item> blockItemSupplier(java.util.function.Supplier<Item> factory) {
        freezeDefinition();
        return new ItemRegistrationSupplier(factory, assetDefinition());
    }

    @Override
    public Item build() {
        Item.Properties props = buildProperties();

        if (groupTab != null && !groupTab.isBlank()) {
            GROUP_ASSIGNMENTS.put(id, ResourceLocation.parse(groupTab));
        }

        // Use an anonymous subclass only when a method must be overridden (glowing or fuel); otherwise
        // create a plain Item.
        if (!glowing && burnTime <= 0) {
            return new Item(props);
        }

        final boolean foil = glowing;
        final int burn = burnTime;
        return new Item(props) {
            @Override
            public boolean isFoil(ItemStack stack) {
                return foil;
            }

            @Override
            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type) {
                return burn;
            }
        };
    }

    /**
     * Builds the configured {@link Item.Properties}: id, stack size, durability, fire resistance,
     * rarity and food components. BlockBuilder and other call sites that need the same properties
     * call this.
     */
    public Item.Properties buildProperties() {
        freezeDefinition();
        Item.Properties props = new Item.Properties();

        if (maxDamage > 0) {
            props.durability(maxDamage);
        } else {
            props.stacksTo(maxStackSize);
        }

        if (fireResistant) props.fireResistant();
        Rarity resolved = resolveRarity(rarity);
        if (resolved != Rarity.COMMON) props.rarity(resolved);

        if (foodBuilder != null) {
            props.food(foodBuilder.buildFood());
        }

        ItemComponentSupport.apply(props, id, definition);
        return props;
    }

    private static Rarity resolveRarity(String name) {
        return switch (name == null ? "common" : name.toLowerCase()) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }
}
