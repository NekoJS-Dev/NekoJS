// 1.21.1 实现，与版本树 src/ 下的同名 26.x 文件成对。内容等于那份文件在本节点求值后的形态
//（可用 tools/extract_evaluated.py 重新提取核对）；26.x 侧行为变更时须同步本文件。
package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import com.tkisor.nekojs.wrapper.entity.NekoScriptMob;
import com.tkisor.nekojs.wrapper.registry.EntityAttributeBuilderJS;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
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
public class EntityTypeBuilder extends RegistryObjectBuilder<EntityType<?>> {

    /** build 期记账的实体（持久：客户端渲染器 / goal 校验 / 属性事件查询用）。 */
    private static final Map<ResourceLocation, EntityTypeBuilder> REGISTERED = new HashMap<>();
    /** 已注册的 spawn egg item id（跨 reload 保留，客户端生成模型用）。 */
    private static final java.util.Set<ResourceLocation> SPAWN_EGGS = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
    private BiFunction<EntityType<?>, Level, ? extends LivingEntity> factory = EntityTypeBuilder::createDefaultMob;
    private Class<? extends LivingEntity> nativeEntityClass;
    private boolean customFactory;
    private boolean attributesConfigured;
    private AttributeSupplier attributeBase;
    private AttributeSupplier builtAttributes;
    private String renderer = "humanoid";
    private ResourceLocation texture = ResourceLocation.parse("minecraft:textures/entity/zombie/zombie.png");
    private float shadowRadius = 0.5F;
    private RenderConfiguration builtRenderConfiguration;

    public record RenderConfiguration(String renderer, ResourceLocation texture, float shadowRadius,
            Class<? extends LivingEntity> entityClass) {}

    public EntityTypeBuilder(ResourceLocation id) {
        super(id);
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
        if (value <= 0 || !Float.isFinite(value)) {
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

    public String getRenderer() {
        return renderer;
    }

    public void setRenderer(String renderer) {
        if (renderer == null || (!renderer.equals("humanoid")
                && !renderer.matches("[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)+"))) {
            throw new IllegalArgumentException("[NEKO-4025] Entity renderer must be humanoid or a Java renderer class name");
        }
        this.renderer = renderer;
    }

    public String getTexture() {
        return texture.toString();
    }

    public void setTexture(String texture) {
        ResourceLocation parsed = texture == null ? null : ResourceLocation.tryParse(texture);
        if (parsed == null || !parsed.getPath().startsWith("textures/")
                || !parsed.getPath().endsWith(".png") || parsed.getPath().contains("..")) {
            throw new IllegalArgumentException("[NEKO-4025] Entity texture must be a resource id under textures/ ending in .png");
        }
        this.texture = parsed;
    }

    public float getShadowRadius() {
        return shadowRadius;
    }

    public void setShadowRadius(float shadowRadius) {
        if (!Float.isFinite(shadowRadius) || shadowRadius < 0F) {
            throw new IllegalArgumentException("[NEKO-4025] Entity shadow radius must be finite and non-negative");
        }
        this.shadowRadius = shadowRadius;
    }

    public EntityTypeBuilder attributeBase(EntityType<? extends LivingEntity> type) {
        AttributeSupplier supplier = DefaultAttributes.getSupplier(type);
        if (supplier == null) {
            throw new IllegalArgumentException("[NEKO-4008] Entity attribute base has no registered supplier: " + type);
        }
        this.attributeBase = supplier;
        return this;
    }

    public EntityTypeBuilder attributeSupplier(AttributeSupplier supplier) {
        if (supplier == null) {
            throw new IllegalArgumentException("[NEKO-4008] Entity attribute supplier must not be null");
        }
        this.attributeBase = supplier;
        return this;
    }

    public void attributes(Consumer<EntityAttributeBuilderJS> consumer) {
        consumer.accept(attributes);
        attributesConfigured = true;
    }

    /** Configures the entity factory, which receives (EntityType, Level). */
    public EntityTypeBuilder factory(BiFunction<EntityType<?>, Level, ? extends LivingEntity> factory) {
        if (factory == null) {
            throw new IllegalArgumentException("[NEKO-4008] entity factory must not be null");
        }
        if (Proxy.isProxyClass(factory.getClass()) || factory.getClass().getName().startsWith("graal.")
                || factory.getClass().getName().startsWith("com.oracle.truffle.")
                || factory.getClass().getName().startsWith("org.graalvm.polyglot.")) {
            throw new IllegalArgumentException("[NEKO-4008] Entity factory must be a native Java implementation; guest callbacks cannot run on both entity owner threads");
        }
        this.factory = factory;
        this.nativeEntityClass = null;
        this.customFactory = true;
        return this;
    }

    /** Uses the standard (EntityType, Level) constructor of a native entity class. */
    public EntityTypeBuilder entityClass(Class<? extends LivingEntity> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("[NEKO-4008] entity class must not be null");
        }
        Constructor<? extends LivingEntity> constructor = nativeConstructor(entityClass);
        this.factory = (type, level) -> instantiate(constructor, type, level);
        this.nativeEntityClass = entityClass;
        this.customFactory = true;
        return this;
    }

    /** 配置 AI 目标。 */
    public void goals(Consumer<GoalRegistry.GoalBuilderJS> consumer) {
        consumer.accept(goals);
    }

    private static LivingEntity createDefaultMob(EntityType<?> type, Level level) {
        @SuppressWarnings("unchecked")
        EntityType<? extends PathfinderMob> mobType = (EntityType<? extends PathfinderMob>) (EntityType<?>) type;
        return new NekoScriptMob(mobType, level);
    }

    private static Constructor<? extends LivingEntity> nativeConstructor(Class<? extends LivingEntity> entityClass) {
        if (!LivingEntity.class.isAssignableFrom(entityClass) || Modifier.isAbstract(entityClass.getModifiers())
                || !Modifier.isPublic(entityClass.getModifiers())) {
            throw new IllegalArgumentException("[NEKO-4008] Entity class must be a public concrete LivingEntity: " + entityClass.getName());
        }
        try {
            return entityClass.getConstructor(EntityType.class, Level.class);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("[NEKO-4008] Entity class must have a public constructor(EntityType, Level): "
                    + entityClass.getName(), exception);
        }
    }

    private static LivingEntity instantiate(Constructor<? extends LivingEntity> constructor,
            EntityType<?> type, Level level) {
        try {
            return constructor.newInstance(type, level);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("[NEKO-4008] Entity construction failed: " + constructor.getDeclaringClass().getName(), exception);
        }
    }

    private AttributeSupplier resolveAttributes() {
        AttributeSupplier base = attributeBase;
        if (base == null && nativeEntityClass != null && nativeEntityClass != NekoScriptMob.class) {
            try {
                var method = nativeEntityClass.getMethod("createAttributes");
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException("[NEKO-4008] Native createAttributes must be static");
                }
                Object result = method.invoke(null);
                if (result instanceof AttributeSupplier.Builder attributeBuilder) {
                    base = attributeBuilder.build();
                } else if (result instanceof AttributeSupplier supplier) {
                    base = supplier;
                } else {
                    throw new IllegalArgumentException("[NEKO-4008] Native createAttributes must return AttributeSupplier or its Builder");
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("[NEKO-4008] Native entity class requires attributeBase or attributeSupplier: "
                        + nativeEntityClass.getName(), exception);
            }
        }
        AttributeSupplier configured = attributes.buildOverrides();
        if (base == null) {
            base = attributes.build();
        }
        Class<? extends LivingEntity> entityClass = customFactory ? nativeEntityClass : NekoScriptMob.class;
        AttributeSupplier required = entityClass == null || Mob.class.isAssignableFrom(entityClass)
                ? Mob.createMobAttributes().build() : LivingEntity.createLivingAttributes().build();
        AttributeSupplier.Builder result = AttributeSupplier.builder();
        for (var holder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeSupplier source = attributesConfigured && configured.hasAttribute(holder) ? configured : base;
            if (required.hasAttribute(holder) && !source.hasAttribute(holder)) {
                throw new IllegalArgumentException("[NEKO-4008] Entity attribute supplier is missing " + holder.getRegisteredName());
            }
            if (source.hasAttribute(holder)) {
                double value = source.getBaseValue(holder);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("[NEKO-4008] Entity attribute value must be finite: " + holder.getRegisteredName());
                }
                result.add(holder, value);
            }
        }
        return result.build();
    }

    @Override
    public EntityType<?> build() {
        builtAttributes = resolveAttributes();
        builtRenderConfiguration = new RenderConfiguration(renderer, texture, shadowRadius,
                customFactory ? nativeEntityClass : NekoScriptMob.class);
        BiFunction<EntityType<?>, Level, ? extends LivingEntity> creationFactory = factory;
        Class<? extends LivingEntity> expectedClass = nativeEntityClass;
        EntityType.EntityFactory<LivingEntity> entityFactory = (type, level) -> {
            Object result = creationFactory.apply(type, level);
            if (!(result instanceof LivingEntity entity) || entity.getType() != type || (expectedClass != null && !expectedClass.isInstance(entity))) {
                throw new IllegalStateException("[NEKO-4008] Entity factory returned an incompatible entity for '" + id + "'");
            }
            return entity;
        };
        EntityType.Builder<LivingEntity> builder = EntityType.Builder.of(entityFactory, resolveCategory(category))
                .sized(width, height)
                .clientTrackingRange(trackingRange)
                .updateInterval(updateInterval)
                .setShouldReceiveVelocityUpdates(receiveVelocityUpdates);

        if (fireImmune) {
            builder.fireImmune();
        }
        if (noSave) {
            builder.noSave();
        }
        if (noSummon) {
            builder.noSummon();
        }

        EntityType<LivingEntity> type = builder.build(id.toString());
        goals.forType(type).register();
        REGISTERED.put(id, this);
        if (spawnEggBackgroundColor != null) {
            SPAWN_EGGS.add(getSpawnEggId());
        }
        return type;
    }

    /** spawn egg 物品 id：{@code <path>_spawn_egg}（同 namespace）。 */
    public ResourceLocation getSpawnEggId() {
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg");
    }

    /** 连带注册：请求过 spawn egg 时注册 {@code <id>_spawn_egg}（ITEM pass 在 ENTITY_TYPE 之后）。 */
    @Override
    public void handleAdditionalObjects(RegistryObjectBuilder.AdditionalObjectRegistry registry) {
        if (spawnEggBackgroundColor == null) {
            return;
        }
        ResourceLocation eggId = getSpawnEggId();
        registry.additional(Registries.ITEM, eggId, () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                () -> (EntityType<? extends net.minecraft.world.entity.Mob>) (EntityType<?>) this.get(),
                spawnEggBackgroundColor, spawnEggHighlightColor, new Item.Properties()));
    }

    /**
     * 抽干 build 期记账的实体 → 属性表成品对。
     * 平台 {@code EntityAttributeCreationEvent} 监听器调用（此时实体均已 build）。
     * 不清账（REGISTERED 持久供渲染器 / goal 查询），重复调用幂等。
     */
    public static Map<EntityType<? extends LivingEntity>, AttributeSupplier> drainPendingAttributes() {
        Map<EntityType<? extends LivingEntity>, AttributeSupplier> drained = new HashMap<>();
        for (EntityTypeBuilder builder : REGISTERED.values()) {
            drained.put(builder.entityType(), builder.builtAttributes);
        }
        return drained;
    }

    public static RenderConfiguration renderConfiguration(EntityType<?> type) {
        return REGISTERED.values().stream().filter(builder -> builder.entityType() == type)
                .map(builder -> builder.builtRenderConfiguration).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("[NEKO-4025] No script entity renderer configuration for " + type));
    }

    /** Returns the configured runtime class when an entity id can be resolved safely. */
    public static Class<? extends LivingEntity> registeredEntityClass(ResourceLocation entityId) {
        EntityTypeBuilder builder = REGISTERED.get(entityId);
        return builder == null ? null : builder.builtRenderConfiguration.entityClass();
    }

    /** build 好的脚本实体类型（未注册 id 返回 null）。 */
    public static EntityType<? extends LivingEntity> getEntityType(ResourceLocation entityId) {
        EntityTypeBuilder builder = REGISTERED.get(entityId);
        return builder == null ? null : builder.entityType();
    }

    /** 全部已 build 的脚本实体类型（客户端渲染器注册用）。 */
    public static Iterable<EntityType<? extends LivingEntity>> registeredEntityTypes() {
        return REGISTERED.values().stream().map(EntityTypeBuilder::entityType).toList();
    }

    /** 已注册的 spawn egg item id（客户端模型生成用，跨 reload 保留）。 */
    public static java.util.Set<ResourceLocation> registeredSpawnEggs() {
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
