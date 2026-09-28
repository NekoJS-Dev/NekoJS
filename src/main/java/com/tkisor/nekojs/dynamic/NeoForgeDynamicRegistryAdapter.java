//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.dynamic;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.core.config.SandboxConfig;
import com.tkisor.nekojs.core.dynamic.plan.DynamicAdapterRequest;
import com.tkisor.nekojs.core.dynamic.txn.DynamicRegistryAdapter;
import com.tkisor.nekojs.core.fs.ClassFilter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ticket 21 platform wiring: the 26.x NeoForge implementation of
 * {@link DynamicRegistryAdapter}. All real registry surgery goes through the existing
 * {@link DynamicRegistries} freeze-bypassing registration path (the three frozen candidate
 * types Item/SoundEvent/MobEffect, tickets 16/21); no second surgery mechanism is added.
 *
 * <p><b>Value reconstruction</b>: the request carries the normalized property readings
 * ({@code name=value}, same source and order as the builder readings). They are fully
 * parsed and validated in {@link #prepareActivation} (closed type set, closed mode set,
 * parseable id, well-formed readings); {@link #activate} then only consumes validated
 * shapes. A prepare rejection aborts the whole batch before any client traffic
 * (coordinator contract).
 *
 * <p><b>Rollback</b>: when {@code activate} violates its no-throw contract, the
 * coordinator demands {@link #rollbackActivation}. This implementation unregisters only
 * the entries the failed attempt <b>newly registered</b> (re-claimed entries keep serving
 * — they were legitimately registered by an earlier batch), through the controlled
 * unregister surgery in {@link DynamicRegistries}; a failing rollback widens the recorded
 * degradation detail instead of escaping.
 *
 * <p><b>Threading</b>: the coordinator/participant call this class on the transaction
 * owner thread (server/client main thread); this class performs no thread hops. The
 * provider supplier is chosen by the assembly (server = the current server's
 * registryAccess, client = the process-wide read-only projection of BuiltInRegistries).
 */
final class NeoForgeDynamicRegistryAdapter implements DynamicRegistryAdapter {

    private final String nodeLabel;
    private final Supplier<HolderLookup.Provider> registries;

    /** Entries newly registered by the current activate attempt (registry key → ids); the sole rollback input, cleared on success. */
    private final Map<String, List<Identifier>> newlyRegisteredByLabel = new HashMap<>();

    NeoForgeDynamicRegistryAdapter(String nodeLabel, Supplier<HolderLookup.Provider> registries) {
        this.nodeLabel = nodeLabel;
        this.registries = registries;
    }

    // ---- prepare: whole-batch validation; rejection aborts the batch with zero client traffic ----

    @Override
    public void prepareActivation(List<DynamicAdapterRequest> requests) {
        requireGateEnabled();
        for (DynamicAdapterRequest request : requests) {
            Identifier id = parseId(request);
            switch (request.registryKey()) {
                case "minecraft:item" -> {
                    maxStackSizeOf(request, id);
                    rarityOf(request, id);
                    fireResistantOf(request);
                }
                case "minecraft:sound_event" -> fixedRangeOf(request, id);
                case "minecraft:mob_effect" -> {
                    categoryOf(request, id);
                    colorOf(request, id);
                }
                default -> throw new IllegalArgumentException(
                        "dynamic activation is closed to item/sound_event/mob_effect on this node; got '"
                                + request.registryKey() + "' for " + id + " (owner " + request.ownerScriptId() + ")");
            }
            NekoJS.LOGGER.debug("DynamicRegistry activation prepare accepted {} ({}, {})",
                    id, request.mode(), request.ownerScriptId());
        }
    }

    // ---- activate: real surgery, consuming only shapes validated by prepare ----

    @Override
    public void activate(List<DynamicAdapterRequest> requests) {
        newlyRegisteredByLabel.clear();
        for (DynamicAdapterRequest request : requests) {
            Identifier id = Identifier.parse(request.id());
            boolean existedBefore = switch (request.registryKey()) {
                case "minecraft:item" -> BuiltInRegistries.ITEM.containsKey(id);
                case "minecraft:sound_event" -> BuiltInRegistries.SOUND_EVENT.containsKey(id);
                case "minecraft:mob_effect" -> BuiltInRegistries.MOB_EFFECT.containsKey(id);
                default -> throw new IllegalStateException(
                        "unsupported dynamic registry key '" + request.registryKey() + "' reached activation");
            };
            switch (request.registryKey()) {
                case "minecraft:item" -> DynamicRegistries.item(id, request.mode(), request.ownerScriptId(),
                        registries.get(), maxStackSizeOf(request, id), rarityOf(request, id),
                        fireResistantOf(request));
                case "minecraft:sound_event" -> DynamicRegistries.soundEvent(id, request.mode(),
                        request.ownerScriptId(), fixedRangeOf(request, id));
                case "minecraft:mob_effect" -> DynamicRegistries.mobEffect(id, request.mode(),
                        request.ownerScriptId(), categoryOf(request, id), colorOf(request, id));
                default -> throw new IllegalStateException(
                        "unsupported dynamic registry key '" + request.registryKey() + "' reached activation");
            }
            if (!existedBefore) {
                newlyRegisteredByLabel.computeIfAbsent(request.registryKey(), key -> new ArrayList<>()).add(id);
            }
        }
        newlyRegisteredByLabel.clear(); // success: nothing to roll back
    }

    // ---- rollback: restore the last successfully activated state (unregister only this attempt's new entries) ----

    @Override
    public void rollbackActivation(List<DynamicAdapterRequest> requests) {
        if (newlyRegisteredByLabel.isEmpty()) {
            return;
        }
        try {
            DynamicRegistries.rollbackEntries(newlyRegisteredByLabel);
        } catch (Throwable rollbackFailure) {
            NekoJS.LOGGER.error("DynamicRegistry activation rollback on {} failed to fully restore the"
                    + " previously activated state: {}", nodeLabel,
                    rollbackFailure.getMessage() == null ? rollbackFailure.getClass().getSimpleName()
                            : rollbackFailure.getMessage());
        } finally {
            newlyRegisteredByLabel.clear();
        }
    }

    // ---- reading resolution (shared by prepare and activate; malformed shapes throw with locating detail) ----

    private static void requireGateEnabled() {
        SandboxConfig config = ClassFilter.INSTANCE.config();
        if (config == null || !config.dynamicRegistryEnabled()) {
            throw new IllegalStateException(
                    "DynamicRegistry activation is disabled on this node: set [dynamicRegistry] enabled = true"
                            + " in nekojs/config/engine.toml and restart, or the batch is rejected before any"
                            + " client traffic");
        }
    }

    private static Identifier parseId(DynamicAdapterRequest request) {
        try {
            return Identifier.parse(request.id());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "dynamic activation request carries an unparseable id '" + request.id() + "': " + e.getMessage());
        }
    }

    private static Map<String, String> readingsOf(DynamicAdapterRequest request) {
        Map<String, String> readings = new HashMap<>();
        for (String reading : request.readings()) {
            int separator = reading.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException(
                        "malformed property reading '" + reading + "' for " + request.id()
                                + " (owner " + request.ownerScriptId() + "): expected name=value");
            }
            readings.put(reading.substring(0, separator), reading.substring(separator + 1));
        }
        return readings;
    }

    private static int maxStackSizeOf(DynamicAdapterRequest request, Identifier id) {
        String value = readingsOf(request).getOrDefault("maxStackSize", "64");
        int maxStackSize;
        try {
            maxStackSize = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "property 'maxStackSize' of '" + id + "' is not an int: '" + value + "'");
        }
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException(
                    "Invalid maxStackSize " + maxStackSize + " for '" + id + "': must be between 1 and 99");
        }
        return maxStackSize;
    }

    private static Rarity rarityOf(DynamicAdapterRequest request, Identifier id) {
        String rarity = readingsOf(request).getOrDefault("rarity", "common");
        return switch (rarity.toLowerCase(Locale.ROOT)) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> throw new IllegalArgumentException(
                    "Unknown rarity '" + rarity + "' for '" + id + "': expected one of common, uncommon, rare, epic");
        };
    }

    private static boolean fireResistantOf(DynamicAdapterRequest request) {
        return Boolean.parseBoolean(readingsOf(request).getOrDefault("fireResistant", "false"));
    }

    private static Float fixedRangeOf(DynamicAdapterRequest request, Identifier id) {
        String value = readingsOf(request).get("fixedRange");
        if (value == null || value.equals("null")) {
            return null; // DynamicRegistries.soundEvent semantics: null = the sound definition decides
        }
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) {
                throw new IllegalArgumentException(
                        "property 'fixedRange' of '" + id + "' is not finite: '" + value + "'");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "property 'fixedRange' of '" + id + "' is not a float: '" + value + "'");
        }
    }

    private static MobEffectCategory categoryOf(DynamicAdapterRequest request, Identifier id) {
        String category = readingsOf(request).getOrDefault("category", "neutral");
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "beneficial" -> MobEffectCategory.BENEFICIAL;
            case "harmful" -> MobEffectCategory.HARMFUL;
            case "neutral" -> MobEffectCategory.NEUTRAL;
            default -> throw new IllegalArgumentException(
                    "Unknown mob effect category '" + category + "' for '" + id
                            + "': expected 'beneficial', 'harmful' or 'neutral'");
        };
    }

    private static int colorOf(DynamicAdapterRequest request, Identifier id) {
        String value = readingsOf(request).getOrDefault("color", "16777215");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "property 'color' of '" + id + "' is not an int: '" + value + "'");
        }
    }
}
//?}
//?}
