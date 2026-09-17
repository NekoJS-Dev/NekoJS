package com.tkisor.nekojs.wrapper.event.server;

import net.minecraft.world.item.Rarity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutable property view handed to {@code ItemEvents.modification} callbacks
 * (ticket 39 起：纯声明缓冲——candidate 收集期只记录规范化属性，live Item/默认组件
 * 由 commit 点的平台 Adapter 应用，见 1.21.1 侧 {@code ModificationDomainOwner}).
 *
 * <h2>JS API（setter 与 property 写入经 {@link ModificationViewSurface} 同一 setter）</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;          // 与 item.setMaxStackSize(16) 等价（同一 setter/校验/规范化）
 *     item.rarity = 'epic';
 *     item.fireResistant = true;
 *   });
 * });
 * </pre>
 *
 * <p>Unset properties keep the item's baseline values. Writing a property records it
 * in the declaration ({@link #normalizedProperties()}); 组件不变量与目标解析由 Adapter
 * 在联合预检（STATE_PLAN）完成。1.21.1 面只有四个基础属性（无 26.x 的
 * food/tool/attack 面——成员差异见 ticket 39 REPORT 五节点差异表）。
 */
public class ItemModificationJS implements graal.graalvm.polyglot.proxy.ProxyObject {

    /** 脚本面成员目录/值装配引擎（putMember 与显式 setter 同一 Method seam，AC8）。 */
    private final ModificationViewSurface surface = ModificationViewSurface.of(this);

    // ---- ProxyObject：property 写与显式 setter 分发到同一 Java setter ----

    @Override
    public Object getMember(String name) {
        return surface.getMember(name);
    }

    @Override
    public boolean hasMember(String name) {
        return surface.hasMember(name);
    }

    @Override
    public Object getMemberKeys() {
        return surface.getMemberKeys();
    }

    @Override
    public void putMember(String name, graal.graalvm.polyglot.Value value) {
        surface.putMember(name, value);
    }

    @Override
    public boolean removeMember(String name) {
        return surface.removeMember(name);
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
     * 本视图的规范化声明值（ticket 39 候选计划载体）：只含被显式设置的属性。
     * 组件不变量（stack/damage）与目标解析由 Adapter 在联合预检（STATE_PLAN）完成。
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
