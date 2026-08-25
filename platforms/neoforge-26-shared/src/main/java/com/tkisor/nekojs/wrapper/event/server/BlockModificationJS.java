package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Mutable property view handed to {@code BlockEvents.modification} callbacks.
 *
 * <h2>JS API</h2>
 * <pre>
 * BlockEvents.modification(event {@code ->} {
 *   event.modify('minecraft:stone', block {@code ->} {
 *     block.hardness = 2;
 *     block.resistance = 6;
 *     block.lightLevel = 15;
 *     block.requiresTool = false;
 *     block.friction = 0.8;
 *     block.jumpFactor = 1.2;
 *   });
 * });
 * </pre>
 *
 * <p>Unset properties keep the block's current (pre-modification) values. Getters
 * return the block's <b>current</b> effective value (base plus pending changes),
 * so scripts can read a value, then decide whether to overwrite it.
 *
 * <h2>Internal (26.x)</h2>
 * Runtime block properties live in three places, and a modification must keep all
 * of them in sync:
 * <ul>
 *   <li>{@code BlockBehaviour.Properties} private fields — the declared source of
 *       truth ({@code destroyTime}/{@code explosionResistance}/{@code lightEmission}
 *       (a {@code ToIntFunction<BlockState>})/{@code requiresCorrectToolForDrops}/
 *       {@code friction}/{@code jumpFactor});</li>
 *   <li>{@code BlockBehaviour} protected final fields — copied into each
 *       {@link Block} at construction ({@code explosionResistance}/{@code friction}/
 *       {@code jumpFactor}); the public {@code getExplosionResistance()}/
 *       {@code getFriction()}/{@code getJumpFactor()} read these;</li>
 *   <li>{@code BlockBehaviour.BlockStateBase} private final fields — copied into
 *       <b>every</b> {@link BlockState} at state construction ({@code destroySpeed}/
 *       {@code lightEmission}/{@code requiresCorrectToolForDrops}); the public
 *       {@code getDestroySpeed}/{@code getLightEmission()}/
 *       {@code requiresCorrectToolForDrops()} read these.</li>
 * </ul>
 *
 * <p>The fields are private/protected (some {@code final}), so writes go through
 * core reflection ({@link Field#setAccessible(boolean)} allows writing instance
 * {@code final} fields of ordinary classes; MC classes sit on the classpath).
 * Cleaner alternative would be AccessTransformer entries in the 26-shared
 * {@code accesstransformer.cfg} ({@code public-f} on these fields); not used here
 * because that file is outside the touched file set.
 */
public class BlockModificationJS {

    // BlockBehaviour$Properties（声明源）：六个属性字段
    private static final Field PROPS_DESTROY_TIME = field(BlockBehaviour.Properties.class, "destroyTime");
    private static final Field PROPS_EXPLOSION_RESISTANCE = field(BlockBehaviour.Properties.class, "explosionResistance");
    private static final Field PROPS_LIGHT_EMISSION = field(BlockBehaviour.Properties.class, "lightEmission");
    private static final Field PROPS_REQUIRES_TOOL = field(BlockBehaviour.Properties.class, "requiresCorrectToolForDrops");
    private static final Field PROPS_FRICTION = field(BlockBehaviour.Properties.class, "friction");
    private static final Field PROPS_JUMP_FACTOR = field(BlockBehaviour.Properties.class, "jumpFactor");
    // BlockBehaviour（Block 实例）：构造时从 Properties 拷出的只读副本，公开 getter 的实际数据源
    private static final Field BLOCK_EXPLOSION_RESISTANCE = field(BlockBehaviour.class, "explosionResistance");
    private static final Field BLOCK_FRICTION = field(BlockBehaviour.class, "friction");
    private static final Field BLOCK_JUMP_FACTOR = field(BlockBehaviour.class, "jumpFactor");
    // BlockStateBase（每个 BlockState）：构造时从 Properties 拷出的只读副本，state 公开 getter 的实际数据源
    private static final Field STATE_DESTROY_SPEED = field(BlockBehaviour.BlockStateBase.class, "destroySpeed");
    private static final Field STATE_LIGHT_EMISSION = field(BlockBehaviour.BlockStateBase.class, "lightEmission");
    private static final Field STATE_REQUIRES_TOOL = field(BlockBehaviour.BlockStateBase.class, "requiresCorrectToolForDrops");

    private final Block block;

    private Float hardness;
    private Float resistance;
    private Integer lightLevel;
    private Boolean requiresTool;
    private Float friction;
    private Float jumpFactor;

    BlockModificationJS(Block block) {
        this.block = block;
    }

    /**
     * Current hardness (destroy time, {@code >= 0}): the pending change if one was
     * set on this view, otherwise the block's live value.
     */
    @Doc("Current destroy-time hardness of the block.")
    @Doc("Reads the live value until you assign a new one; -1-style unbreakable is not allowed here (set a very high value instead).")
    @Return("the current hardness: the pending change if set, otherwise the block's live value")
    public float getHardness() {
        return hardness != null ? hardness : block.defaultBlockState().getDestroySpeed(null, null);
    }

    @Doc("Sets the destroy-time hardness (>= 0; higher = slower to dig).")
    @Param(name = "hardness", value = "new hardness, >= 0 (e.g. 2)")
    public void setHardness(float hardness) {
        this.hardness = hardness;
    }

    /**
     * Current blast resistance ({@code >= 0}): the pending change if one was set on
     * this view, otherwise the block's live value.
     */
    @Doc("Current blast resistance of the block.")
    @Return("the current resistance: the pending change if set, otherwise the block's live value")
    // NeoForge 标记了带爆炸上下文的重载（更灵敏），读“当前值”无需上下文，保守保留无参版
    @SuppressWarnings("deprecation")
    public float getResistance() {
        return resistance != null ? resistance : block.getExplosionResistance();
    }

    @Doc("Sets the blast resistance (>= 0; 3600000 behaves like bedrock).")
    @Param(name = "resistance", value = "new resistance, >= 0")
    public void setResistance(float resistance) {
        this.resistance = resistance;
    }

    /**
     * Current light emission ({@code 0..15}): the pending change if one was set on
     * this view, otherwise the default state's live value.
     */
    @Doc("Current light emission of the block (0..15).")
    @Return("the current light level: the pending change if set, otherwise the block's live value")
    // NeoForge 标记了带 (BlockGetter, BlockPos) 的重载，读“当前值”无需光照上下文，保守保留无参版
    @SuppressWarnings("deprecation")
    public int getLightLevel() {
        return lightLevel != null ? lightLevel : block.defaultBlockState().getLightEmission();
    }

    @Doc("Sets the light emission (0..15, e.g. glowstone is 15).")
    @Param(name = "lightLevel", value = "new light level, 0..15")
    public void setLightLevel(int lightLevel) {
        this.lightLevel = lightLevel;
    }

    /**
     * Whether the block needs the correct tool to drop loot: the pending change if
     * one was set on this view, otherwise the default state's live value.
     */
    @Doc("Whether the block requires the correct tool tier to drop loot.")
    @Return("the current flag: the pending change if set, otherwise the block's live value")
    public boolean getRequiresTool() {
        return requiresTool != null ? requiresTool : block.defaultBlockState().requiresCorrectToolForDrops();
    }

    @Doc("Sets whether the correct tool is required for drops (like pickaxe on iron ore).")
    @Param(name = "requiresTool", value = "true to require the correct tool, false to always drop")
    public void setRequiresTool(boolean requiresTool) {
        this.requiresTool = requiresTool;
    }

    /**
     * Current surface friction ({@code 0..1}, ice {@code 0.98}): the pending change
     * if one was set on this view, otherwise the block's live value.
     */
    @Doc("Current surface friction of the block (0..1; ice is 0.98, default 0.6).")
    @Return("the current friction: the pending change if set, otherwise the block's live value")
    public float getFriction() {
        return friction != null ? friction : block.getFriction();
    }

    @Doc("Sets the surface friction (0..1; higher = more slippery, ice is 0.98).")
    @Param(name = "friction", value = "new friction, 0..1")
    public void setFriction(float friction) {
        this.friction = friction;
    }

    /**
     * Current jump factor (vanilla {@code 0.5..1}): the pending change if one was
     * set on this view, otherwise the block's live value.
     */
    @Doc("Current jump factor of the block (vanilla blocks use 0.5..1).")
    @Return("the current jump factor: the pending change if set, otherwise the block's live value")
    public float getJumpFactor() {
        return jumpFactor != null ? jumpFactor : block.getJumpFactor();
    }

    @Doc("Sets the jump factor (multiplies jump height on top of this block).")
    @Param(name = "jumpFactor", value = "new jump factor (vanilla blocks use 0.5..1)")
    public void setJumpFactor(float jumpFactor) {
        this.jumpFactor = jumpFactor;
    }

    /**
     * Writes the requested properties into the block (Properties fields + the
     * copies held by the {@link Block} and every {@link BlockState}), validating
     * ranges first. Unset properties are left untouched.
     */
    void applyTo(Block block) {
        validate();
        if (hardness != null) {
            setFloat(PROPS_DESTROY_TIME, block.properties(), hardness);
            forEachState(block, state -> setFloat(STATE_DESTROY_SPEED, state, hardness));
        }
        if (resistance != null) {
            setFloat(PROPS_EXPLOSION_RESISTANCE, block.properties(), resistance);
            setFloat(BLOCK_EXPLOSION_RESISTANCE, block, resistance);
        }
        if (lightLevel != null) {
            setLightFunction(block.properties(), lightLevel);
            forEachState(block, state -> setInt(STATE_LIGHT_EMISSION, state, lightLevel));
        }
        if (requiresTool != null) {
            setBoolean(PROPS_REQUIRES_TOOL, block.properties(), requiresTool);
            forEachState(block, state -> setBoolean(STATE_REQUIRES_TOOL, state, requiresTool));
        }
        if (friction != null) {
            setFloat(PROPS_FRICTION, block.properties(), friction);
            setFloat(BLOCK_FRICTION, block, friction);
        }
        if (jumpFactor != null) {
            setFloat(PROPS_JUMP_FACTOR, block.properties(), jumpFactor);
            setFloat(BLOCK_JUMP_FACTOR, block, jumpFactor);
        }
    }

    /**
     * 校验取值范围：hardness/resistance {@code >= 0}、lightLevel {@code 0..15}、
     * friction {@code 0..1}。不合法值在应用期（而非赋值期）抛出，与物品版一致。
     */
    private void validate() {
        if (hardness != null && hardness < 0) {
            throw new IllegalArgumentException("Invalid hardness " + hardness + ": must be >= 0");
        }
        if (resistance != null && resistance < 0) {
            throw new IllegalArgumentException("Invalid resistance " + resistance + ": must be >= 0");
        }
        if (lightLevel != null && (lightLevel < 0 || lightLevel > 15)) {
            throw new IllegalArgumentException("Invalid lightLevel " + lightLevel + ": must be between 0 and 15");
        }
        if (friction != null && (friction < 0 || friction > 1)) {
            throw new IllegalArgumentException("Invalid friction " + friction + ": must be between 0 and 1");
        }
    }

    /**
     * 六个属性的原始值快照（restore 路径依据）。光照保存原始
     * {@code ToIntFunction<BlockState>}（个别方块按 state 給不同光照），其余为标量。
     */
    record PropertySnapshot(
            float destroyTime,
            float explosionResistance,
            ToIntFunction<BlockState> lightEmission,
            boolean requiresCorrectToolForDrops,
            float friction,
            float jumpFactor) {

        static PropertySnapshot capture(Block block) {
            BlockBehaviour.Properties props = block.properties();
            return new PropertySnapshot(
                    getFloat(PROPS_DESTROY_TIME, props),
                    getFloat(PROPS_EXPLOSION_RESISTANCE, props),
                    lightFunctionOf(props),
                    getBoolean(PROPS_REQUIRES_TOOL, props),
                    getFloat(PROPS_FRICTION, props),
                    getFloat(PROPS_JUMP_FACTOR, props));
        }

        /** 把快照值完整写回 Properties 副本、Block 副本与全部 BlockState 副本。 */
        void applyTo(Block block) {
            BlockBehaviour.Properties props = block.properties();
            setFloat(PROPS_DESTROY_TIME, props, destroyTime);
            forEachState(block, state -> setFloat(STATE_DESTROY_SPEED, state, destroyTime));
            setFloat(PROPS_EXPLOSION_RESISTANCE, props, explosionResistance);
            setFloat(BLOCK_EXPLOSION_RESISTANCE, block, explosionResistance);
            setLightFunctionRaw(props, lightEmission);
            forEachState(block, state -> setInt(STATE_LIGHT_EMISSION, state, lightEmission == null ? 0 : lightEmission.applyAsInt(state)));
            setBoolean(PROPS_REQUIRES_TOOL, props, requiresCorrectToolForDrops);
            forEachState(block, state -> setBoolean(STATE_REQUIRES_TOOL, state, requiresCorrectToolForDrops));
            setFloat(PROPS_FRICTION, props, friction);
            setFloat(BLOCK_FRICTION, block, friction);
            setFloat(PROPS_JUMP_FACTOR, props, jumpFactor);
            setFloat(BLOCK_JUMP_FACTOR, block, jumpFactor);
        }
    }

    // ---- 反射读写（26.x 字段名在 26.1.2 与 26.2.0 已核对一致；改名时 field() 会显式失败）----

    private static Field field(Class<?> type, String name) {
        try {
            Field found = type.getDeclaredField(name);
            found.setAccessible(true);
            return found;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BlockBehaviour field '" + type.getSimpleName() + '.' + name
                    + "' not found; MC 26.x mappings changed?", e);
        }
    }

    private static float getFloat(Field field, Object target) {
        try {
            return field.getFloat(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read " + field, e);
        }
    }

    private static boolean getBoolean(Field field, Object target) {
        try {
            return field.getBoolean(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read " + field, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ToIntFunction<BlockState> lightFunctionOf(BlockBehaviour.Properties props) {
        try {
            return (ToIntFunction<BlockState>) PROPS_LIGHT_EMISSION.get(props);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read lightEmission", e);
        }
    }

    /** 用常量光照函数覆盖 Properties.lightEmission（与 vanilla {@code lightLevel(int)} 同构）。 */
    private static void setLightFunction(BlockBehaviour.Properties props, int level) {
        setLightFunctionRaw(props, state -> level);
    }

    private static void setLightFunctionRaw(BlockBehaviour.Properties props, ToIntFunction<BlockState> function) {
        try {
            PROPS_LIGHT_EMISSION.set(props, function);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write lightEmission", e);
        }
    }

    private static void setFloat(Field field, Object target, float value) {
        try {
            field.setFloat(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write " + field, e);
        }
    }

    private static void setInt(Field field, Object target, int value) {
        try {
            field.setInt(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write " + field, e);
        }
    }

    private static void setBoolean(Field field, Object target, boolean value) {
        try {
            field.setBoolean(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to write " + field, e);
        }
    }

    private static void forEachState(Block block, Consumer<BlockState> action) {
        block.getStateDefinition().getPossibleStates().forEach(action);
    }
}
