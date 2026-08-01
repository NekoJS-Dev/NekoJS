package com.tkisor.nekojs.wrapper.event.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@code ServerEvents.lootTables} 事件对象：loot table 的 JSON 层管理。
 *
 * <p>服务器数据 reload 时（{@code AddServerReloadListenersEvent}，先于 loot 解析）
 * post；脚本在此声明修改，随后 reload 流程中 NeoForge 为每个非空 table 触发
 * {@link LootTableLoadEvent}，本类把 pending 修改应用到对应 table：
 * <ul>
 *   <li>{@code setJson / create}：解析后替换（id 可为原本不存在的）</li>
 *   <li>{@code remove}：替换为 {@link LootTable#EMPTY}（加载器对空表不缓存，等价删除）</li>
 * </ul>
 * pending 修改跨 reload 保留（每次 reload 脚本重新声明）；{@code getJson / getIds}
 * 读取最近一次 reload 加载后（含修改）的数据。
 */
public class LootTableEventJS {
    /** 待应用的替换（id → JSON）。 */
    private static final Map<Identifier, JsonObject> PENDING_SET = new ConcurrentHashMap<>();
    /** 待删除的 id。 */
    private static final Set<Identifier> PENDING_REMOVE = ConcurrentHashMap.newKeySet();
    /** 最近一次 reload 中应用修改后的 table，{@code getJson} 的序列化源。 */
    private static final Map<Identifier, LootTable> LOADED_TABLES = new ConcurrentHashMap<>();
    /** 最近一次 reload 的 registry lookup（序列化 / 解析用）。 */
    private static volatile HolderLookup.Provider REGISTRIES;

    /** 所有已知 loot table id（含脚本创建的）。 */
    public List<String> getIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LOADED_TABLES.keySet().forEach(id -> ids.add(id.toString()));
        PENDING_SET.keySet().forEach(id -> ids.add(id.toString()));
        return List.copyOf(ids);
    }

    /** 指定 id 的当前 JSON（含脚本修改）；未知 id 返回 null。 */
    public JsonElement getJson(String id) {
        Identifier location = parseId(id);
        JsonObject pending = PENDING_SET.get(location);
        if (pending != null) {
            return pending;
        }
        LootTable table = LOADED_TABLES.get(location);
        HolderLookup.Provider registries = REGISTRIES;
        if (table == null || registries == null) {
            return null;
        }
        return LootTable.DIRECT_CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), table)
                .getOrThrow();
    }

    /** 覆盖指定 id 的 loot table（下次 reload 生效）。 */
    public void setJson(String id, Object json) {
        Identifier location = parseId(id);
        PENDING_SET.put(location, toJsonObject(json));
        PENDING_REMOVE.remove(location);
    }

    /** 创建/覆盖 loot table（{@code setJson} 的别名，语义上允许不存在的 id）。 */
    public void create(String id, Object json) {
        setJson(id, json);
    }

    /**
     * 便利修改：读取当前 JSON（含脚本先前修改）→ builder 回调 → 写回。
     * 不存在的 id 从空对象开始（记得 {@code table.setType('minecraft:chest')}）。
     */
    public void modify(String id, Consumer<LootTableJS> consumer) {
        JsonElement current = getJson(id);
        JsonObject json = current != null && current.isJsonObject()
                ? current.getAsJsonObject()
                : new JsonObject();
        consumer.accept(new LootTableJS(json));
        setJson(id, json);
    }

    /**
     * 批量修改方块战利品表：{@code blockId} 为方块 id、{@code '#tag'}（展开标签下所有方块）
     * 或 {@code '*'}（所有已知方块表）。
     */
    public void modifyBlockLoot(String blockId, Consumer<LootTableJS> consumer) {
        if (blockId.startsWith("#")) {
            for (String tableId : blockTableIdsForTag(blockId.substring(1))) {
                modify(tableId, consumer);
            }
        } else if ("*".equals(blockId)) {
            for (String tableId : knownTableIds("blocks/")) {
                modify(tableId, consumer);
            }
        } else {
            modify(blockTableId(blockId), consumer);
        }
    }

    /** 批量修改方块战利品表：{@code filter} 接收方块 id（如 {@code 'mymod:ore'}）返回是否处理。 */
    public void modifyBlockLoot(Predicate<String> filter, Consumer<LootTableJS> consumer) {
        for (String tableId : knownTableIds("blocks/")) {
            if (filter.test(tableId.replace("/blocks/", ":"))) {
                modify(tableId, consumer);
            }
        }
    }

    /** 批量修改实体战利品表：{@code entityId} 为实体 id 或 {@code '*'}（所有已知实体表）。 */
    public void modifyEntityLoot(String entityId, Consumer<LootTableJS> consumer) {
        if ("*".equals(entityId)) {
            for (String tableId : knownTableIds("entities/")) {
                modify(tableId, consumer);
            }
        } else {
            modify(entityTableId(entityId), consumer);
        }
    }

    /** 批量修改实体战利品表：{@code filter} 接收实体 id（如 {@code 'minecraft:zombie'}）返回是否处理。 */
    public void modifyEntityLoot(Predicate<String> filter, Consumer<LootTableJS> consumer) {
        for (String tableId : knownTableIds("entities/")) {
            if (filter.test(tableId.replace("/entities/", ":"))) {
                modify(tableId, consumer);
            }
        }
    }

    private static List<String> knownTableIds(String prefix) {
        List<String> ids = new ArrayList<>();
        for (String id : new LootTableEventJS().getIds()) {
            int slash = id.indexOf('/');
            if (slash >= 0 && id.substring(slash + 1).startsWith(prefix)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String blockTableId(String blockId) {
        Identifier id = parseId(blockId);
        return id.getNamespace() + ":blocks/" + id.getPath();
    }

    private static String entityTableId(String entityId) {
        Identifier id = parseId(entityId);
        return id.getNamespace() + ":entities/" + id.getPath();
    }

    /** 标签展开：该标签下所有方块对应的 {@code blocks/<path>} 表 id。 */
    private static List<String> blockTableIdsForTag(String tag) {
        List<String> ids = new ArrayList<>();
        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(tag));
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey)) {
            holder.unwrapKey().ifPresent(key -> {
                Identifier location = key.identifier();
                ids.add(location.getNamespace() + ":blocks/" + location.getPath());
            });
        }
        return ids;
    }

    /** 删除指定 loot table（下次 reload 生效，读取时得到空表）。 */
    public void remove(String id) {
        Identifier location = parseId(id);
        PENDING_REMOVE.add(location);
        PENDING_SET.remove(location);
    }

    /**
     * NeoForge 总线回调：每个非空 table 加载时应用 pending 修改并收集结果。
     * 在 reload 流程的 loot 解析阶段触发，早于任何对 loot 的消费。
     */
    public static void onLootTableLoad(LootTableLoadEvent event) {
        Identifier id = event.getName();
        REGISTRIES = event.getRegistries();
        if (PENDING_REMOVE.contains(id)) {
            event.setTable(LootTable.EMPTY);
        } else {
            JsonObject json = PENDING_SET.get(id);
            if (json != null) {
                event.setTable(parse(json, event.getRegistries()));
            }
        }
        LOADED_TABLES.put(id, event.getTable());
    }

    private static LootTable parse(JsonObject json, HolderLookup.Provider registries) {
        return LootTable.DIRECT_CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow();
    }

    private static JsonObject toJsonObject(Object json) {
        if (json instanceof JsonObject object) {
            return object;
        }
        return LootTableJS.toJsonObject(json);
    }

    private static Identifier parseId(String id) {
        return id.contains(":") ? Identifier.parse(id) : Identifier.fromNamespaceAndPath("nekojs", id);
    }
}
