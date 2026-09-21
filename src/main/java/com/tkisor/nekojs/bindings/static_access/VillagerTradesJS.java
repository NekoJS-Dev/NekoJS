// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
//? if neoforge {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.villager.VillagerTradeQuerySurface;
import com.tkisor.nekojs.core.villager.VillagerTradesFacade;
import com.tkisor.nekojs.villager.VillagerTradeManager;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.trading.TradeSet;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Static binding {@code VillagerTrades}.
 *
 * <h2>ticket 22: two paths coexist, on purpose</h2>
 * <ul>
 *   <li><b>CURRENT (event + adapter path)</b>: {@link #query()} returns a read-only,
 *       generation-bound snapshot of what the last committed generation declared. Trades are
 *       declared through the existing {@code ServerEvents.tradeDeclaration} data sub-event and
 *       applied by the platform/version adapter at the legal commit point.</li>
 *   <li><b>LEGACY (staged static path, unchanged)</b>: {@link #add(String, Object)} stages a
 *       trade into the process-level queue that {@code VillagerTradeManager} flushes at the end
 *       of the reload cycle. It is kept verbatim so existing scripts keep working; its removal is
 *       a maintainer sign-off item (ticket 22 AC10, see the baseline MIGRATION.md).</li>
 * </ul>
 * The two paths must not be mixed for the same trade set: the legacy path rewrites a trade set
 * from its own snapshot while the adapter path rewrites it from the NekoJS baseline, so using
 * both for one set is last-writer-wins with no merge policy (documented, not merged).
 */
@Doc("Static binding 'VillagerTrades': declare villager / wandering trader trades with ServerEvents.tradeDeclaration and read the committed result with query().")
public class VillagerTradesJS {

    /**
     * Read-only, generation-bound snapshot of the committed villager trade declarations.
     *
     * <p>Example: {@code const result = VillagerTrades.query();
     * if (result.status === 'ACTIVE') console.info(result.total, result.tradeSetIds)}</p>
     */
    @Doc("Returns a read-only, generation-bound snapshot: status ('ACTIVE' / 'STALE'), generation, adapterId, total, tradeSetIds, unrestoredListingKeys and retiredListingKeys. A stale token answers with empty values instead of another generation's data.")
    @Return("read-only query result (never a live registry view)")
    public VillagerTradeQuerySurface query() {
        return VillagerTradesFacade.query(Context.getCurrent());
    }

    /** Developer-facing summary of {@link #query()} (diagnostics; no structured members needed). */
    @Doc("Developer-facing one-line summary of query(), for diagnostics and logs.")
    @Return("human-readable summary line")
    public String describe() {
        return query().describe();
    }

    /**
     * LEGACY path (kept for existing scripts; removal is a maintainer sign-off item).
     *
     * <p>Example: {@code VillagerTrades.add('minecraft:farmer/level_1', {
     * cost: '1x minecraft:emerald', result: '5x minecraft:apple', maxUses: 12, xp: 2 })}</p>
     */
    @Doc("LEGACY: appends a trade to an existing trade set registry entry (e.g. 'minecraft:farmer/level_1', 'minecraft:wandering_trader/buying'). The change is staged and applied when the reload cycle finishes; returns false when the trade set id is unknown or the config is invalid. Prefer ServerEvents.tradeDeclaration (ticket 22).")
    @Param(name = "tradeSet", value = "trade set registry id, '<namespace>:<profession>/level_<n>' or a wandering trader set id")
    @Param(name = "config", value = "{ cost: '<count>x <item id>', costB: '<count>x <item id>' (optional), result: '<count>x <item id>', maxUses: 12, xp: 2, priceMultiplier: 0.05 }")
    @Return("true when the trade was staged for the next flush")
    public boolean add(String tradeSet, Object config) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            NekoJS.LOGGER.warn("VillagerTrades.add: no server is running; trade for '{}' ignored", tradeSet);
            return false;
        }
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
                    setId, maxUses, xp, priceMultiplier);
            return false;
        }

        VillagerTradeManager.stageAdd(new VillagerTradeManager.PendingTrade(
                setKey,
                cost.id, cost.count,
                costB != null ? costB.id : null, costB != null ? costB.count : 1,
                result.id, result.count,
                maxUses, xp, priceMultiplier));
        return true;
    }

    /** LEGACY: number of trades staged since the last flush (diagnostics for scripts). */
    @Doc("LEGACY: number of trades staged since the last flush.")
    @Return("pending trade count")
    public int pendingCount() {
        return VillagerTradeManager.pendingCount();
    }

    private static Registry<TradeSet> tradeSetRegistry(MinecraftServer server) {
        if (server.reloadableRegistries().lookup() instanceof RegistryAccess access) {
            return access.lookup(Registries.TRADE_SET).orElse(null);
        }
        return null;
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
