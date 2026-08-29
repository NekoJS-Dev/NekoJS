//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.villager.VillagerTradeManager;
import graal.graalvm.polyglot.Value;
//? if >=26 {
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
//?}
import net.minecraft.resources.Identifier;
//? if >=26 {
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.trading.TradeSet;
//?}
import net.neoforged.neoforge.server.ServerLifecycleHooks;
//? if <26 {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?}

/**
 * Static binding {@code VillagerTrades}: stages villager / wandering trader trade additions
 * from server scripts. Changes are staged immediately and flushed into the live
 * {@code minecraft:villager_trade} / {@code minecraft:trade_set} registries at the end of
 * the reload cycle, so calling this anywhere during script load is safe.
 */
//? if >=26 {
@Doc("Static binding 'VillagerTrades': append custom trades to vanilla villager and wandering trader trade sets.")
//?} else {
/*@Doc("Static binding 'VillagerTrades': append custom trades to vanilla villager and wandering trader trade pools.")
*///?}
public class VillagerTradesJS {

    /**
     * Appends a trade to an existing trade set.
     *
     * <p>Example: {@code VillagerTrades.add('minecraft:farmer/level_1', {
     * cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 })}</p>
     */
//? if >=26 {
    @Doc("Appends a trade to an existing trade set registry entry (e.g. 'minecraft:farmer/level_1', 'minecraft:wandering_trader/buying').")
    @Doc("The change is staged and applied when the reload cycle finishes; returns false when the trade set id is unknown or the config is invalid.")
    @Param(name = "tradeSet", value = "trade set registry id, '<namespace>:<profession>/level_<n>' or a wandering trader set id")
//?} else {
/*    @Doc("Appends a trade to a villager trade pool ('<namespace>:<profession>/level_<n>') or a wandering trader pool ('minecraft:wandering_trader/level_1' or 'level_2').")
    @Doc("The change is staged and applied when the reload cycle finishes; villagers offer the trade on their next restock. Returns false for unknown pools or invalid configs.")
    @Param(name = "tradeSet", value = "trade pool id, e.g. 'minecraft:farmer/level_1' or 'minecraft:wandering_trader/buying'")
*///?}
    @Param(name = "config", value = "{ cost: '<count>x <item id>', costB: '<count>x <item id>' (optional), result: '<count>x <item id>', maxUses: 12, xp: 2, priceMultiplier: 0.05 }")
    @Return("true when the trade was staged for the next flush")
    public boolean add(String tradeSet, Object config) {
//? if >=26 {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
//?} else {
/*        if (ServerLifecycleHooks.getCurrentServer() == null) {
*///?}
            NekoJS.LOGGER.warn("VillagerTrades.add: no server is running; trade for '{}' ignored", tradeSet);
            return false;
        }
//? if >=26 {
        Identifier setId = Identifier.tryParse(tradeSet);
        if (setId == null) {
            NekoJS.LOGGER.warn("VillagerTrades.add: invalid trade set id '{}'", tradeSet);
            return false;
        }
        ResourceKey<TradeSet> setKey = ResourceKey.create(Registries.TRADE_SET, setId);
        Registry<TradeSet> registry = tradeSetRegistry(server);
        if (registry == null) {
            NekoJS.LOGGER.warn("VillagerTrades.add: TRADE_SET registry is not available");
            return false;
        }
        if (!registry.containsKey(setKey)) {
            NekoJS.LOGGER.warn("VillagerTrades.add: trade set '{}' is not registered", setId);
//?} else {
/*        Target target = parseTarget(tradeSet);
        if (target == null) {
*///?}
            return false;
        }

        Value cfg = config == null ? null : Value.asValue(config);
        ItemSpec cost = readItem(cfg, "cost", true);
        ItemSpec costB = readItem(cfg, "costB", false);
        ItemSpec result = readItem(cfg, "result", true);
        if (cost == null || result == null) {
            return false;
        }
        int maxUses = readInt(cfg, "maxUses", 12);
        int xp = readInt(cfg, "xp", 2);
        float priceMultiplier = readFloat(cfg, "priceMultiplier", 0.05f);
        if (maxUses <= 0 || xp < 0 || priceMultiplier < 0.0f || priceMultiplier > 1.0f) {
            NekoJS.LOGGER.warn("VillagerTrades.add({}): maxUses must be > 0, xp >= 0 and priceMultiplier within 0..1 (got {}/{}/{})",
//? if >=26 {
                    setId, maxUses, xp, priceMultiplier);
//?} else {
/*                    tradeSet, maxUses, xp, priceMultiplier);
*///?}
            return false;
        }

        VillagerTradeManager.stageAdd(new VillagerTradeManager.PendingTrade(
//? if >=26 {
                setKey,
//?} else {
/*                target.professionId, target.level,
*///?}
                cost.id, cost.count,
                costB != null ? costB.id : null, costB != null ? costB.count : 1,
                result.id, result.count,
                maxUses, xp, priceMultiplier));
        return true;
    }

    /** Number of trades staged since the last flush (diagnostics for scripts). */
    @Doc("Number of trades staged since the last flush.")
    @Return("pending trade count")
    public int pendingCount() {
        return VillagerTradeManager.pendingCount();
    }

//? if >=26 {
    private static Registry<TradeSet> tradeSetRegistry(MinecraftServer server) {
        if (server.reloadableRegistries().lookup() instanceof RegistryAccess access) {
            return access.lookup(Registries.TRADE_SET).orElse(null);
//?} else {
/*    private record Target(Identifier professionId, int level) {}

    /^* Parses 'minecraft:farmer/level_1' / 'minecraft:wandering_trader/<pool>' into a profession+level target. ^/
    private static Target parseTarget(String tradeSet) {
        int slash = tradeSet == null ? -1 : tradeSet.lastIndexOf('/');
        if (slash <= 0 || slash == tradeSet.length() - 1) {
            NekoJS.LOGGER.warn("VillagerTrades.add: trade set id must look like '<profession>/level_<n>', got '{}'", tradeSet);
            return null;
*///?}
        }
//? if >=26 {
        return null;
//?} else {
/*        String professionText = tradeSet.substring(0, slash);
        String levelText = tradeSet.substring(slash + 1).toLowerCase();
        int level;
        if (levelText.startsWith("level_")) {
            try {
                level = Integer.parseInt(levelText.substring("level_".length()));
            } catch (NumberFormatException e) {
                level = -1;
            }
        } else {
            level = switch (levelText) {
                case "buying", "common" -> 1;
                case "uncommon", "rare" -> 2;
                default -> -1;
            };
        }
        if (level < 1) {
            NekoJS.LOGGER.warn("VillagerTrades.add: cannot parse a level from trade set id '{}'", tradeSet);
            return null;
        }
        Identifier professionId = Identifier.tryParse(professionText);
        if (professionId == null) {
            NekoJS.LOGGER.warn("VillagerTrades.add: invalid profession id in '{}'", tradeSet);
            return null;
        }
        boolean wandering = professionId.getPath().equals("wandering_trader");
        if (wandering) {
            if (!VillagerTradeManager.wanderingTraderLevels().contains(level)) {
                NekoJS.LOGGER.warn("VillagerTrades.add: wandering trader has no level {} pool", level);
                return null;
            }
            return new Target(null, level);
        }
        if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(professionId)) {
            NekoJS.LOGGER.warn("VillagerTrades.add: profession '{}' is not registered", professionId);
            return null;
        }
        return new Target(professionId, level);
*///?}
    }

    private record ItemSpec(Identifier id, int count) {}

    /** Reads an item spec: '3x minecraft:apple', 'minecraft:apple', or { item: 'minecraft:apple', count: 3 }. */
    private static ItemSpec readItem(Value cfg, String key, boolean required) {
        Value raw = member(cfg, key);
        if (raw == null || raw.isNull()) {
            if (!required) {
                return null;
            }
            NekoJS.LOGGER.warn("VillagerTrades.add: missing required '{}' entry", key);
            return null;
        }
        int count = 1;
        String idText;
        if (raw.isString()) {
            String text = raw.asString().trim();
            java.util.regex.Matcher matcher = ITEM_SPEC.matcher(text);
            if (matcher.matches()) {
                count = Integer.parseInt(matcher.group(1));
                idText = matcher.group(2).trim();
            } else {
                idText = text;
            }
        } else if (raw.hasMembers()) {
            Value item = member(raw, "item");
            Value itemCount = member(raw, "count");
            if (item == null || !item.isString()) {
                NekoJS.LOGGER.warn("VillagerTrades.add: '{}' object form needs {{ item: '<id>', count: n }}", key);
                return null;
            }
            idText = item.asString().trim();
            if (itemCount != null && itemCount.isNumber()) {
                count = (int) itemCount.asDouble();
            }
        } else {
            NekoJS.LOGGER.warn("VillagerTrades.add: '{}' must be an item id string or {{ item, count }}", key);
            return null;
        }
        if (count <= 0) {
            NekoJS.LOGGER.warn("VillagerTrades.add: '{}' count must be > 0 (got {})", key, count);
            return null;
        }
        Identifier id = Identifier.tryParse(idText);
        if (id == null) {
            NekoJS.LOGGER.warn("VillagerTrades.add: '{}' is not a valid item id: '{}'", key, idText);
            return null;
        }
        return new ItemSpec(id, count);
    }

    private static final java.util.regex.Pattern ITEM_SPEC =
            java.util.regex.Pattern.compile("(?i)^(\\d+)\\s*x\\s+(\\S+)$");

    private static int readInt(Value cfg, String key, int fallback) {
        Value v = member(cfg, key);
        return v != null && v.isNumber() ? (int) v.asDouble() : fallback;
    }

    private static float readFloat(Value cfg, String key, float fallback) {
        Value v = member(cfg, key);
        return v != null && v.isNumber() ? (float) v.asDouble() : fallback;
    }

    private static Value member(Value cfg, String key) {
        return cfg != null && cfg.hasMember(key) ? cfg.getMember(key) : null;
    }
}
//?}
