package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.dynamic.DynamicRegisterMode;
import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import graal.graalvm.polyglot.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.function.Consumer;

/**
 * Script-facing {@code DynamicRegistry} binding: runtime (post-server-start)
 * registration of content into vanilla registries, from SERVER scripts.
 *
 * <h2>JS API</h2>
 * <pre>
 * ServerEvents.started(event =&gt; {
 *     DynamicRegistry.item('mymod:ruby', builder =&gt; builder
 *             .maxStackSize(64).rarity('epic').fireResistant())
 *     DynamicRegistry.soundEvent('mymod:boom', builder =&gt; builder.fixedRange(16))
 *     DynamicRegistry.mobEffect('mymod:wither_touch', builder =&gt; builder
 *             .category('harmful').color(0x8B0000))
 * })
 * </pre>
 *
 * <p>The builder callback mirrors {@code RegistryEvents.*.create(id, builder =&gt; ...)}
 * so both registration paths read the same and stay fully typed in generated
 * declarations. Call these methods from {@code ServerEvents.started}: top-level
 * script evaluation can happen before the server registry access exists.
 * Permanent startup content belongs to {@code RegistryEvents}, not here.
 *
 * <p>Registration is only legal while a server is running AND the
 * {@code [dynamicRegistry]} gate in {@code engine.toml} is enabled; anything
 * else fails with an error telling the user exactly what to do.
 *
 * <p>Reload semantics (v1, both modes): entries are never unregistered
 * mid-session. On {@code /nekojs reload server} claims become stale; a script
 * that re-runs re-claims its ids. See {@link DynamicRegistries}.
 */
@Doc("Runtime (post-server-start) registry registration for SERVER scripts: soundEvent, mobEffect, item.")
@Doc("Requires a running server and engine.toml [dynamicRegistry] enabled=true (default false).")
@Doc("Entries survive /nekojs reload; scripts re-claim ids by registering them again.")
public final class DynamicRegistryJS {

    /** Process-wide singleton; registration state lives in {@link DynamicRegistries}. */
    public static final DynamicRegistryJS INSTANCE = new DynamicRegistryJS();

    private DynamicRegistryJS() {
    }

    @Doc("Registers (or re-claims) a sound event with default options (mode 'world').")
    @Param(name = "id", value = "entry id like 'mymod:boom'")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle soundEvent(String id) {
        return soundEvent(id, builder -> {
        });
    }

    @Doc("Registers (or re-claims) a sound event configured by the builder callback.")
    @Param(name = "id", value = "entry id like 'mymod:boom'")
    @Param(name = "config", value = "builder callback: mode(...) / fixedRange(...)")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle soundEvent(String id, Consumer<SoundEventBuilder> config) {
        Entry entry = Entry.of(id);
        SoundEventBuilder builder = configure(new SoundEventBuilder(), config);
        DynamicRegisterMode mode = builder.resolveMode();
        var soundEvent = DynamicRegistries.soundEvent(entry.id(), mode, entry.owner(), builder.fixedRange);
        NekoJS.LOGGER.info("DynamicRegistry: sound event '{}' registered by {} (mode {})",
                entry.id(), entry.owner(), mode);
        return new DynamicEntryHandle(entry.id().toString(), mode, entry.owner(), soundEvent);
    }

    @Doc("Registers (or re-claims) a mob effect with default options (mode 'world').")
    @Param(name = "id", value = "entry id like 'mymod:wither_touch'")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle mobEffect(String id) {
        return mobEffect(id, builder -> {
        });
    }

    @Doc("Registers (or re-claims) a mob effect configured by the builder callback.")
    @Param(name = "id", value = "entry id like 'mymod:wither_touch'")
    @Param(name = "config", value = "builder callback: mode(...) / category(...) / color(...)")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle mobEffect(String id, Consumer<MobEffectBuilder> config) {
        Entry entry = Entry.of(id);
        MobEffectBuilder builder = configure(new MobEffectBuilder(), config);
        DynamicRegisterMode mode = builder.resolveMode();
        var mobEffect = DynamicRegistries.mobEffect(
                entry.id(), mode, entry.owner(), builder.resolveCategory(), builder.color);
        NekoJS.LOGGER.info("DynamicRegistry: mob effect '{}' registered by {} (mode {})",
                entry.id(), entry.owner(), mode);
        return new DynamicEntryHandle(entry.id().toString(), mode, entry.owner(), mobEffect);
    }

    @Doc("Registers (or re-claims) an item with default options (64 stack size, common rarity).")
    @Param(name = "id", value = "item id like 'mymod:ruby'")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle item(String id) {
        return item(id, builder -> {
        });
    }

    @Doc("Registers (or re-claims) an item and binds its default components immediately.")
    @Param(name = "id", value = "item id like 'mymod:ruby'")
    @Param(name = "config", value = "builder callback: mode(...) / maxStackSize(...) / rarity(...) / fireResistant(...)")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle item(String id, Consumer<ItemBuilder> config) {
        Entry entry = Entry.of(id);
        ItemBuilder builder = configure(new ItemBuilder(), config);
        DynamicRegisterMode mode = builder.resolveMode();
        int stackSize = builder.stackSize;
        if (stackSize < 1 || stackSize > 99) {
            throw new IllegalArgumentException(
                    "Invalid stackSize " + stackSize + " for '" + entry.id() + "': must be between 1 and 99");
        }
        Rarity rarity = builder.resolveRarity();
        MinecraftServer server = requireRunningServer();
        var item = DynamicRegistries.item(entry.id(), mode, entry.owner(),
                server.registryAccess(), stackSize, rarity, builder.fireResistant);
        NekoJS.LOGGER.info("DynamicRegistry: item '{}' registered by {} (mode {}, stackSize {}, rarity {}, fireResistant {})",
                entry.id(), entry.owner(), mode, stackSize, rarity, builder.fireResistant);
        return new DynamicEntryHandle(entry.id().toString(), mode, entry.owner(), item);
    }

    private static <T> T configure(T builder, Consumer<T> config) {
        if (config == null) {
            throw new IllegalArgumentException(
                    "DynamicRegistry expects a builder callback, e.g. DynamicRegistry.item('mymod:ruby', "
                            + "builder => builder.maxStackSize(64).rarity('epic'))");
        }
        config.accept(builder);
        return builder;
    }

    /** Shared registration mode option; every dynamic builder accepts it. */
    @Doc("Base options shared by every DynamicRegistry builder.")
    public abstract static sealed class ModeBuilder
            permits ItemBuilder, SoundEventBuilder, MobEffectBuilder {

        private String mode = "world";

        ModeBuilder() {
        }

        DynamicRegisterMode resolveMode() {
            return DynamicRegisterMode.parse(mode);
        }

        void setMode(String mode) {
            this.mode = mode;
        }
    }

    /** Item options: stack size, rarity and fire resistance. */
    @Doc("Item options for DynamicRegistry.item.")
    public static final class ItemBuilder extends ModeBuilder {

        int stackSize = 64;
        String rarity = "common";
        boolean fireResistant;

        @Doc("Registration mode: 'world' (default) or 'reloadable'.")
        public ItemBuilder mode(String mode) {
            setMode(mode);
            return this;
        }

        @Doc("Maximum stack size (1..99); defaults to 64.")
        public ItemBuilder maxStackSize(int size) {
            this.stackSize = size;
            return this;
        }

        @Doc("Rarity: 'common' (default), 'uncommon', 'rare' or 'epic'.")
        public ItemBuilder rarity(String rarity) {
            this.rarity = rarity;
            return this;
        }

        @Doc("Marks the item fire resistant.")
        public ItemBuilder fireResistant() {
            return fireResistant(true);
        }

        @Doc("Sets whether the item is fire resistant (default false).")
        public ItemBuilder fireResistant(boolean value) {
            this.fireResistant = value;
            return this;
        }

        Rarity resolveRarity() {
            return switch (rarity == null ? "" : rarity.toLowerCase()) {
                case "common" -> Rarity.COMMON;
                case "uncommon" -> Rarity.UNCOMMON;
                case "rare" -> Rarity.RARE;
                case "epic" -> Rarity.EPIC;
                default -> throw new IllegalArgumentException(
                        "Unknown rarity '" + rarity + "': expected one of common, uncommon, rare, epic");
            };
        }
    }

    /** Sound event options: optional fixed audible range. */
    @Doc("Sound event options for DynamicRegistry.soundEvent.")
    public static final class SoundEventBuilder extends ModeBuilder {

        Float fixedRange;

        @Doc("Registration mode: 'world' (default) or 'reloadable'.")
        public SoundEventBuilder mode(String mode) {
            setMode(mode);
            return this;
        }

        @Doc("Fixed audible range in blocks; omitted means the sound definition decides.")
        public SoundEventBuilder fixedRange(float range) {
            this.fixedRange = range;
            return this;
        }
    }

    /** Mob effect options: HUD category and ARGB color. */
    @Doc("Mob effect options for DynamicRegistry.mobEffect.")
    public static final class MobEffectBuilder extends ModeBuilder {

        String category = "neutral";
        int color = 0xFFFFFF;

        @Doc("Registration mode: 'world' (default) or 'reloadable'.")
        public MobEffectBuilder mode(String mode) {
            setMode(mode);
            return this;
        }

        @Doc("Category: 'neutral' (default), 'beneficial' or 'harmful'.")
        public MobEffectBuilder category(String category) {
            this.category = category;
            return this;
        }

        @Doc("Effect color as an ARGB int, e.g. 0x8B0000.")
        public MobEffectBuilder color(int color) {
            this.color = color;
            return this;
        }

        MobEffectCategory resolveCategory() {
            return switch (category == null ? "" : category.toLowerCase()) {
                case "beneficial" -> MobEffectCategory.BENEFICIAL;
                case "harmful" -> MobEffectCategory.HARMFUL;
                case "neutral" -> MobEffectCategory.NEUTRAL;
                default -> throw new IllegalArgumentException(
                        "Unknown mob effect category '" + category
                                + "': expected 'beneficial', 'harmful' or 'neutral'");
            };
        }
    }

    // ---- gating + parsing helpers ----

    /** Central gate: a running server plus the engine.toml toggle, with actionable errors. */
    private static MinecraftServer requireRunningServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException(
                    "DynamicRegistry is only usable while a server is running: move the registration into a "
                            + "ServerEvents.started(...) callback in a server_scripts script, or re-run it with "
                            + "/nekojs reload server once the server is up");
        }
        SandboxConfig config = ClassFilter.INSTANCE.config();
        if (!config.dynamicRegistryEnabled()) {
            throw new IllegalStateException(
                    "DynamicRegistry is disabled: set [dynamicRegistry] enabled = true in "
                            + "nekojs/config/engine.toml and restart the game");
        }
        return server;
    }

    /** Parsed call arguments: validated id plus the owning script id. */
    private record Entry(Identifier id, String owner) {

        static Entry of(String rawId) {
            requireRunningServer();
            if (rawId == null || rawId.isBlank()) {
                throw new IllegalArgumentException("DynamicRegistry expects an id like 'mymod:boom'");
            }
            Identifier id;
            try {
                id = Identifier.parse(rawId.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid id '" + rawId + "': " + e.getMessage());
            }
            return new Entry(id, currentScriptId());
        }

        private static String currentScriptId() {
            Context context = Context.getCurrent();
            return context == null ? null : ScriptContextRegistry.currentScriptIdOf(context);
        }
    }
}
