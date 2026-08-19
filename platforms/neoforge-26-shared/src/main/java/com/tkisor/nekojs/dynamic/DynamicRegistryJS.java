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
import graal.graalvm.polyglot.Value;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Script-facing {@code DynamicRegistry} binding: runtime (post-server-start)
 * registration of content into vanilla registries, from SERVER scripts.
 *
 * <h2>JS API</h2>
 * <pre>
 * DynamicRegistry.soundEvent('mymod:boom', { mode: 'world' })
 * DynamicRegistry.mobEffect('mymod:wither_touch', { category: 'harmful', color: 0x8B0000 })
 * DynamicRegistry.item('mymod:ruby', { stackSize: 64, fireResistant: true, rarity: 'epic' })
 * </pre>
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

    /** See {@link #soundEvent(String, Value)}. */
    @Doc("Registers (or re-claims) a sound event with default options (mode 'world').")
    @Param(name = "id", value = "entry id like 'mymod:boom'")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle soundEvent(String id) {
        return soundEvent(id, null);
    }

    @Doc("Registers (or re-claims) a sound event.")
    @Param(name = "id", value = "entry id like 'mymod:boom'")
    @Param(name = "options", value = "{ mode?: 'world'|'reloadable', fixedRange?: number }")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle soundEvent(String id, Value options) {
        Options opts = Options.of(id, options);
        var soundEvent = DynamicRegistries.soundEvent(
                opts.id(), opts.mode(), opts.owner(), opts.floatOption("fixedRange"));
        NekoJS.LOGGER.info("DynamicRegistry: sound event '{}' registered by {} (mode {})",
                opts.id(), opts.owner(), opts.mode());
        return new DynamicEntryHandle(opts.id().toString(), opts.mode(), opts.owner(), soundEvent);
    }

    /** See {@link #mobEffect(String, Value)}. */
    @Doc("Registers (or re-claims) a mob effect with default options (mode 'world').")
    @Param(name = "id", value = "entry id like 'mymod:wither_touch'")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle mobEffect(String id) {
        return mobEffect(id, null);
    }

    @Doc("Registers (or re-claims) a mob effect.")
    @Param(name = "id", value = "entry id like 'mymod:wither_touch'")
    @Param(name = "options", value = "{ mode?, category?: 'beneficial'|'harmful'|'neutral', color?: number (ARGB) }")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle mobEffect(String id, Value options) {
        Options opts = Options.of(id, options);
        MobEffectCategory category = switch (opts.stringOption("category", "neutral").toLowerCase()) {
            case "beneficial" -> MobEffectCategory.BENEFICIAL;
            case "harmful" -> MobEffectCategory.HARMFUL;
            case "neutral" -> MobEffectCategory.NEUTRAL;
            default -> throw new IllegalArgumentException(
                    "Unknown mob effect category '" + opts.stringOption("category", "neutral")
                            + "': expected 'beneficial', 'harmful' or 'neutral'");
        };
        int color = (int) opts.intOption("color", 0xFFFFFF);
        var mobEffect = DynamicRegistries.mobEffect(opts.id(), opts.mode(), opts.owner(), category, color);
        NekoJS.LOGGER.info("DynamicRegistry: mob effect '{}' registered by {} (mode {})",
                opts.id(), opts.owner(), opts.mode());
        return new DynamicEntryHandle(opts.id().toString(), opts.mode(), opts.owner(), mobEffect);
    }

    @Doc("Registers (or re-claims) an item and binds its default components immediately.")
    @Param(name = "id", value = "item id like 'mymod:ruby'")
    @Param(name = "options", value = "{ mode?, stackSize?: 1..99, rarity?: 'common'|'uncommon'|'rare'|'epic', fireResistant?: boolean }")
    @Return("handle exposing id() / mode() / owner()")
    public DynamicEntryHandle item(String id, Value options) {
        Options opts = Options.of(id, options);
        int stackSize = (int) opts.intOption("stackSize", 64);
        if (stackSize < 1 || stackSize > 99) {
            throw new IllegalArgumentException(
                    "Invalid stackSize " + stackSize + " for '" + opts.id() + "': must be between 1 and 99");
        }
        Rarity rarity = parseRarity(opts.stringOption("rarity", "common"));
        boolean fireResistant = opts.boolOption("fireResistant", false);
        MinecraftServer server = requireRunningServer();
        var item = DynamicRegistries.item(opts.id(), opts.mode(), opts.owner(),
                server.registryAccess(), stackSize, rarity, fireResistant);
        NekoJS.LOGGER.info("DynamicRegistry: item '{}' registered by {} (mode {}, stackSize {}, rarity {}, fireResistant {})",
                opts.id(), opts.owner(), opts.mode(), stackSize, rarity, fireResistant);
        return new DynamicEntryHandle(opts.id().toString(), opts.mode(), opts.owner(), item);
    }

    // ---- gating + parsing helpers ----

    private static Rarity parseRarity(String name) {
        return switch (name.toLowerCase()) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> throw new IllegalArgumentException(
                    "Unknown rarity '" + name + "': expected one of common, uncommon, rare, epic");
        };
    }

    /** Central gate: a running server plus the engine.toml toggle, with actionable errors. */
    private static MinecraftServer requireRunningServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException(
                    "DynamicRegistry is only usable while a server is running: move the registration into a "
                            + "server_scripts script (it runs once a server is up), or re-run it with "
                            + "/nekojs reload server");
        }
        SandboxConfig config = ClassFilter.INSTANCE.config();
        if (!config.dynamicRegistryEnabled()) {
            throw new IllegalStateException(
                    "DynamicRegistry is disabled: set [dynamicRegistry] enabled = true in "
                            + "nekojs/config/engine.toml and restart the game");
        }
        return server;
    }

    /** Parsed call arguments: id + option view + resolved mode + owning script id. */
    private record Options(Identifier id, DynamicRegisterMode mode, String owner, Value options) {

        static Options of(String rawId, Value options) {
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
            DynamicRegisterMode mode = DynamicRegisterMode.parse(
                    options != null && options.hasMember("mode") && options.getMember("mode").isString()
                            ? options.getMember("mode").asString()
                            : "world");
            return new Options(id, mode, currentScriptId(), options);
        }

        boolean has(String name) {
            return options != null && options.hasMember(name);
        }

        String stringOption(String name, String fallback) {
            return has(name) && options.getMember(name).isString() ? options.getMember(name).asString() : fallback;
        }

        long intOption(String name, long fallback) {
            return has(name) && options.getMember(name).isNumber() ? options.getMember(name).asLong() : fallback;
        }

        boolean boolOption(String name, boolean fallback) {
            return has(name) && options.getMember(name).isBoolean() ? options.getMember(name).asBoolean() : fallback;
        }

        Float floatOption(String name) {
            return has(name) && options.getMember(name).isNumber() ? options.getMember(name).asFloat() : null;
        }

        private static String currentScriptId() {
            Context context = Context.getCurrent();
            return context == null ? null : ScriptContextRegistry.currentScriptIdOf(context);
        }
    }
}
