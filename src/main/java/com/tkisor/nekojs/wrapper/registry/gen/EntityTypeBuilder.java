// 26.x 实现，本文件不应再出现版本守卫。1.21.1 的实现是 versions/1.21.1/src 下的同名文件，
// 改本文件行为时须同步它。
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.entity.NekoScriptMob;
import com.tkisor.nekojs.wrapper.registry.EntityAttributeBuilderJS;
import com.tkisor.nekojs.wrapper.registry.TaggableBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 实体类型 builder：spawn egg 经 {@link #handleAdditionalObjects} 连带到 ITEM pass
 * （{@code <id>_spawn_egg}，ENTITY_TYPE 先于 ITEM 触发）；属性在 build 期记账，
 * 由平台 {@code EntityAttributeCreationEvent} 监听器经 {@link #drainPendingAttributes()} 消费。
 *
 * <pre>
 * event.entityType('mymod:robo_cat', b =&gt; {
 *     b.category = 'creature'; b.size(0.6, 0.5); b.spawnEgg(0x22B14C, 0xED1C24)
 * })
 * </pre>
 */
public class EntityTypeBuilder extends RegistryObjectBuilder<EntityType<?>>
        implements TaggableBuilder<EntityTypeBuilder> {

    /** build 期记账的实体（持久：客户端渲染器 / goal 校验 / 属性事件查询用）。 */
    private static final Map<Identifier, EntityTypeBuilder> REGISTERED = new HashMap<>();
    /** 已注册的 spawn egg item id（跨 reload 保留，客户端生成模型用）。 */
    private static final java.util.Set<Identifier> SPAWN_EGGS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 生物类别名：monster/ambient/water_creature/water_ambient/underground_water_creature/axolotls/misc（默认 creature）。 */
    private String category = "creature";
    private float width = 0.6F;
    private float height = 1.8F;
    private int trackingRange = 8;
    private int updateInterval = 3;
    private boolean receiveVelocityUpdates = true;
    private boolean fireImmune = false;
    private boolean noSave = false;
    private boolean noSummon = false;

    private Integer spawnEggBackgroundColor = null;
    private Integer spawnEggHighlightColor = null;
    private final EntityAttributeBuilderJS attributes = new EntityAttributeBuilderJS();
    private final GoalRegistry.GoalBuilderJS goals = GoalRegistry.builder();

    public EntityTypeBuilder(Identifier id) {
        super(id);
    }

    /** {@link TaggableBuilder}：实体 tag（如 {@code minecraft:raiders}）归属 ENTITY_TYPE 注册表。 */
    @Override
    public ResourceKey<? extends Registry<?>> getTagRegistry() {
        return Registries.ENTITY_TYPE;
    }

    @Override
    public Identifier getLocation() {
        return id;
    }

    // ---- 数据属性：显式 setter 与 JavaBean property 同一写入点（ticket 15） ----

    /** 生物类别名（已归一化小写）。 */
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category == null ? "creature" : category.toLowerCase();
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        requirePositive("width", width);
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        requirePositive("height", height);
        this.height = height;
    }

    public int getTrackingRange() {
        return trackingRange;
    }

    public void setTrackingRange(int trackingRange) {
        if (trackingRange < 0) {
            throw new IllegalArgumentException("trackingRange must be >= 0 but got " + trackingRange);
        }
        this.trackingRange = trackingRange;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(int updateInterval) {
        if (updateInterval < 1) {
            throw new IllegalArgumentException("updateInterval must be >= 1 but got " + updateInterval);
        }
        this.updateInterval = updateInterval;
    }

    public boolean isReceiveVelocityUpdates() {
        return receiveVelocityUpdates;
    }

    public void setReceiveVelocityUpdates(boolean receiveVelocityUpdates) {
        this.receiveVelocityUpdates = receiveVelocityUpdates;
    }

    public boolean isFireImmune() {
        return fireImmune;
    }

    public void setFireImmune(boolean fireImmune) {
        this.fireImmune = fireImmune;
    }

    public boolean isNoSave() {
        return noSave;
    }

    public void setNoSave(boolean noSave) {
        this.noSave = noSave;
    }

    public boolean isNoSummon() {
        return noSummon;
    }

    public void setNoSummon(boolean noSummon) {
        this.noSummon = noSummon;
    }

    private static void requirePositive(String what, float value) {
        if (value <= 0 || Float.isNaN(value)) {
            throw new IllegalArgumentException(what + " must be > 0 but got " + value);
        }
    }

    /** 设置碰撞箱宽高。 */
    public void size(double width, double height) {
        setWidth((float) width);
        setHeight((float) height);
    }

    /**
     * 请求自动注册 {@code <id>_spawn_egg} 物品。26.x 无运行时染色，颜色仅作声明（纹理数据驱动）。
     */
    public void spawnEgg(int backgroundColor, int highlightColor) {
        this.spawnEggBackgroundColor = backgroundColor;
        this.spawnEggHighlightColor = highlightColor;
    }

    /** 配置属性表（血量/速度等）。 */
    public void attributes(Consumer<EntityAttributeBuilderJS> consumer) {
        consumer.accept(attributes);
    }

    /** 配置 AI 目标。 */
    public void goals(Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        consumer.accept(goals);
    }

    @Override
    public EntityType<?> build() {
        EntityType.Builder<NekoScriptMob> builder = EntityType.Builder.of(NekoScriptMob::new, resolveCategory(category))
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval);
//? if neoforge {
        // setShouldReceiveVelocityUpdates 是 NeoForge patch 方法，fabric 无对应设置点
        builder.setShouldReceiveVelocityUpdates(receiveVelocityUpdates);
//?}

        if (fireImmune) {
            builder.fireImmune();
        }
        if (noSave) {
            builder.noSave();
        }
        if (noSummon) {
            builder.noSummon();
        }

        EntityType<NekoScriptMob> type = builder.build(ResourceKey.create(Registries.ENTITY_TYPE, id));
        goals.forType(type).register();
        REGISTERED.put(id, this);
        if (spawnEggBackgroundColor != null) {
            SPAWN_EGGS.add(getSpawnEggId());
        }
        return type;
    }

    /** spawn egg 物品 id：{@code <path>_spawn_egg}（同 namespace）。 */
    public Identifier getSpawnEggId() {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
    }

    /** 连带注册：请求过 spawn egg 时注册 {@code <id>_spawn_egg}（ITEM pass 在 ENTITY_TYPE 之后）。 */
    @Override
    public void handleAdditionalObjects(RegistryObjectBuilder.AdditionalObjectRegistry registry) {
        if (spawnEggBackgroundColor == null) {
            return;
        }
        Identifier eggId = getSpawnEggId();
        registry.additional(Registries.ITEM, eggId, () -> new SpawnEggItem(
                new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, eggId))
                        .spawnEgg(this.get())));
    }

    /**
     * 抽干 build 期记账的实体 → 属性表成品对。
     * 平台 {@code EntityAttributeCreationEvent} 监听器调用（此时实体均已 build）。
     * 不清账（REGISTERED 持久供渲染器 / goal 查询），重复调用幂等。
     */
    public static Map<EntityType<? extends LivingEntity>, AttributeSupplier> drainPendingAttributes() {
        Map<EntityType<? extends LivingEntity>, AttributeSupplier> drained = new HashMap<>();
        for (EntityTypeBuilder builder : REGISTERED.values()) {
            drained.put(builder.entityType(), builder.attributes.build());
        }
        return drained;
    }

    /** build 好的脚本实体类型（未注册 id 返回 null）。 */
    public static EntityType<? extends LivingEntity> getEntityType(Identifier entityId) {
        EntityTypeBuilder builder = REGISTERED.get(entityId);
        return builder == null ? null : builder.entityType();
    }

    /** 全部已 build 的脚本实体类型（客户端渲染器注册用）。 */
    public static Iterable<EntityType<? extends LivingEntity>> registeredEntityTypes() {
        return REGISTERED.values().stream().map(EntityTypeBuilder::entityType).toList();
    }

    /** 已注册的 spawn egg item id（客户端模型生成用，跨 reload 保留）。 */
    public static java.util.Set<Identifier> registeredSpawnEggs() {
        return java.util.Collections.unmodifiableSet(SPAWN_EGGS);
    }

    /** build 好的实体类型（属性事件监听器解析实体实例用）。 */
    @SuppressWarnings("unchecked")
    public EntityType<? extends LivingEntity> entityType() {
        return (EntityType<? extends LivingEntity>) get();
    }

    private static MobCategory resolveCategory(String name) {
        return switch (name == null ? "creature" : name.toLowerCase()) {
            case "monster", "hostile" -> MobCategory.MONSTER;
            case "ambient" -> MobCategory.AMBIENT;
            case "water_creature", "watercreature" -> MobCategory.WATER_CREATURE;
            case "water_ambient", "waterambient" -> MobCategory.WATER_AMBIENT;
            case "underground_water_creature", "undergroundwatercreature" -> MobCategory.UNDERGROUND_WATER_CREATURE;
            case "axolotls", "axolotl" -> MobCategory.AXOLOTLS;
            case "misc" -> MobCategory.MISC;
            default -> MobCategory.CREATURE;
        };
    }
}
