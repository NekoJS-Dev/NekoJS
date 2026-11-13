package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable property view handed to {@code ItemEvents.modification} callbacks. It is a pure
 * declaration buffer: collection only records normalized properties, and the platform Adapter
 * at the commit point applies them to the live Item and its default components (see
 * {@code ModificationDomainOwner} on the 1.21.1 side).
 *
 * <h2>JS API ({@link ModificationViewSurface} routes setters and property writes to one setter)</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;          // same as item.setMaxStackSize(16) (same setter/validation/normalization)
 *     item.rarity = 'epic';
 *     item.fireResistant = true;
 *   });
 * });
 * </pre>
 *
 * <p>Unset properties keep the item's baseline values. Writing a property records it in the
 * declaration ({@link #normalizedProperties()}). The Adapter checks the component rules and
 * resolves the target during the preflight (STATE_PLAN). The 1.21.1 side only has these four
 * basic properties; the 26.x food/tool/attack properties do not exist here.
 */
public class ItemModificationJS implements graal.graalvm.polyglot.proxy.ProxyObject {

    private ModificationViewSurface surface;

    /** Creates the script surface on first use, after normal subclass construction. */
    private ModificationViewSurface surface() {
        if (surface == null) surface = ModificationViewSurface.of(this);
        return surface;
    }

    // ---- ProxyObject: property writes and explicit setters dispatch to the same Java setter ----

    @Override
    public Object getMember(String name) {
        return surface().getMember(name);
    }

    @Override
    public boolean hasMember(String name) {
        return surface().hasMember(name);
    }

    @Override
    public Object getMemberKeys() {
        return surface().getMemberKeys();
    }

    @Override
    public void putMember(String name, graal.graalvm.polyglot.Value value) {
        surface().putMember(name, value);
    }

    @Override
    public boolean removeMember(String name) {
        return surface().removeMember(name);
    }

    private Integer maxStackSize;
    private Integer maxDamage;
    private Rarity rarity;
    private Boolean fireResistant;

    /** Maximum stack size (1..99). {@code null} = keep current value. */
    public Integer getMaxStackSize() {
        return maxStackSize;
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;
    }

    /** Maximum damage / durability ({@code >= 0}). {@code null} = keep current value. */
    public Integer getMaxDamage() {
        return maxDamage;
    }

    public void setMaxDamage(int maxDamage) {
        this.maxDamage = maxDamage;
    }

    /**
     * Current rarity as its serialized name ({@code 'common'/'uncommon'/'rare'/'epic'}),
     * or {@code null} when not set.
     */
    public String getRarity() {
        return rarity == null ? null : rarity.getSerializedName();
    }

    /**
     * Sets the rarity, either by serialized name ({@code 'epic'}) or by a
     * {@link Rarity} instance.
     */
    public void setRarity(Object value) {
        if (value == null) {
            this.rarity = null;
        } else if (value instanceof Rarity rarityValue) {
            this.rarity = rarityValue;
        } else if (value instanceof String name) {
            this.rarity = parseRarity(name);
        } else {
            throw new IllegalArgumentException("Invalid rarity '" + value + "': expected a string like 'epic' or a Rarity");
        }
    }

    /** Whether the item should resist fire/lava damage. {@code null} = keep current value. */
    public Boolean getFireResistant() {
        return fireResistant;
    }

    public void setFireResistant(boolean fireResistant) {
        this.fireResistant = fireResistant;
    }

    /**
     * The normalized declarations recorded by this view: only the properties that were set
     * explicitly. The Adapter checks the component rules (stack/damage) and resolves the target
     * during the preflight (STATE_PLAN).
     */
    Map<String, Object> normalizedProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (maxStackSize != null) properties.put("maxStackSize", maxStackSize);
        if (maxDamage != null) properties.put("maxDamage", maxDamage);
        if (rarity != null) properties.put("rarity", rarity.getSerializedName());
        if (fireResistant != null) properties.put("fireResistant", fireResistant);
        return properties;
    }

    private static Rarity parseRarity(String name) {
        return switch (name.toLowerCase()) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> throw new IllegalArgumentException("Unknown rarity '" + name + "': expected one of common, uncommon, rare, epic");
        };
    }
}
