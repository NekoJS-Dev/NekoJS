// 26.x implementation. No version guards belong in this file. The 1.21.1 implementation is the file
// with the same name under versions/1.21.1/src; whenever you change behavior here, change that file too.
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.registry.FoodBuilderJS;
import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Item builder. Data properties are private and written through JavaBean setters, so the script form
 * {@code b.maxStackSize = 16} (through {@link BuilderSurface} putMember) and an explicit
 * {@code b.setMaxStackSize(16)} call reach the same setter and run the same validation,
 * normalization, definition fingerprint and registration collection path. No public field of the
 * same name may bypass the setter.
 * <pre>
 * event.item('mymod:ruby', b =&gt; { b.maxStackSize = 16; b.rarity = 'epic' })
 * </pre>
 * Composite configuration (food) stays as void methods; glowing and fuel use an anonymous subclass
 * only when a method has to be overridden.
 */
public class ItemBuilder extends RegistryObjectBuilder<Item> implements TaggableBuilder<ItemBuilder> {

    /** Items assigned to a creative tab: item id to tab id. Consumed during BuildCreativeModeTabContents. */
    public static final Map<Identifier, Identifier> GROUP_ASSIGNMENTS = new HashMap<>();

    /** burnTime bookkeeping: item id to burn ticks. Fabric reads it and feeds it into
     * FuelValueEvents.BUILD; NeoForge overrides getBurnTime in an anonymous subclass and never reads
     * this map. */
    public static final Map<Identifier, Integer> FUEL_ASSIGNMENTS = new HashMap<>();

    private int maxStackSize = 64;
    private int maxDamage = 0;
    private boolean fireResistant = false;
    /** Rarity name: common / uncommon / rare / epic. Defaults to common; the setter lowercases it. */
    private String rarity = "common";
    private boolean glowing = false;
    /** Burn time in ticks. Above 0 the item can fuel a furnace, blast furnace or smoker. */
    private int burnTime = 0;
    /** Optional creative tab id, such as 'minecraft:building_blocks' or a custom tab id. null assigns
     * no tab. */
    private String groupTab = null;

    private FoodBuilderJS foodBuilder = null;
    private final com.tkisor.nekojs.core.registry.ItemDefinitionConfig definition =
            new com.tkisor.nekojs.core.registry.ItemDefinitionConfig();

    public ItemBuilder(Identifier id) {
        super(id);
    }

    /** {@link TaggableBuilder}: item tags belong to the ITEM registry. */
    @Override
    public net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<?>> getTagRegistry() {
        return Registries.ITEM;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    // ---- Data properties: the explicit setters and the JavaBean properties share one write path ----

    public int getMaxStackSize() {
        return maxStackSize;
    }

    /** Maximum stack size, 1 to 99. Minecraft reports out-of-range values when the registry freezes;
     * this rejects them at write time with the member name in the message. */
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

    /** Rarity name, already lowercased. null is accepted and falls back to common at build time. */
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

    /** Creative tab id, already normalized: a blank value means no tab, which takes the same branch at
     * build time. */
    @com.tkisor.nekojs.api.annotation.ScriptNullable
    public String getGroupTab() {
        return groupTab;
    }

    public void setGroupTab(@com.tkisor.nekojs.api.annotation.ScriptNullable String groupTab) {
        definition.writable();
        this.groupTab = groupTab == null || groupTab.isBlank() ? null : groupTab;
    }

    // ---- Composite configuration ----

    /** Configures the food properties (nutrition, saturation, effects and so on). Composite
     * configuration stays a method. */
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
    public String getTrimMaterial() { return definition.trimMaterial(); }
    public void setTrimMaterial(@com.tkisor.nekojs.api.annotation.ScriptNullable String material) { definition.trimMaterial(material); }
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

        if (groupTab != null) {
            GROUP_ASSIGNMENTS.put(id, Identifier.parse(groupTab));
        }
        if (burnTime > 0) {
            FUEL_ASSIGNMENTS.put(id, burnTime);
        }

        // Use an anonymous subclass only when a method has to be overridden (glowing, fuel); otherwise
        // construct Item directly
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

//? if neoforge {
// getBurnTime is a patch method that NeoForge adds to Item, so this override does not exist
// on fabric; fabric fuel goes through FabricFuelRegistry instead (see docs/fabric-port-status.md)
            @Override
            public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type,
                                    net.minecraft.world.level.block.entity.FuelValues fuelValues) {
                return burn;
            }
//?}
        };
    }

    /**
     * Builds the configured {@link Item.Properties}: id, stack size, durability, fire resistance,
     * rarity and food components. BlockBuilder and other callers reuse these properties.
     */
    public Item.Properties buildProperties() {
        freezeDefinition();
        net.minecraft.resources.ResourceKey<Item> key =
                net.minecraft.resources.ResourceKey.create(Registries.ITEM, id);
        Item.Properties props = new Item.Properties().setId(key);

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
            props.component(net.minecraft.core.component.DataComponents.CONSUMABLE, foodBuilder.buildConsumable());
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
