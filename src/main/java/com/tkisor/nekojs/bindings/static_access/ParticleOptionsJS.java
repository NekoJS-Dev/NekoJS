package com.tkisor.nekojs.bindings.static_access;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.data.ValueConversionException;
import com.tkisor.nekojs.core.JsonObjectAdapter;

import graal.graalvm.polyglot.Value;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Factory helper for {@code ParticleOptions}, bound as the global {@code ParticleOptions} with its
 * members delegating to the {@link ParticleOptions} interface.
 *
 * <p>Particle without options: {@code ParticleOptions.of('minecraft:cloud')} returns the particle
 * type itself (ParticleType implements ParticleOptions). Particle with options:
 * {@code ParticleOptions.of('minecraft:dust', {color: [1,0,0], size: 1})} parses the options
 * through that particle type's Codec.
 */
public class ParticleOptionsJS {

    public ParticleOptions of(Identifier particleId) {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId)
                .orElseThrow(() -> new ValueConversionException(ParticleOptions.class, "registered particle id",
                        particleId, "particle not found: " + particleId));
        if (type instanceof ParticleOptions options) {
            return options;
        }
        throw new ValueConversionException(ParticleOptions.class, "particle with no required options",
                particleId, "particle '" + particleId + "' requires options; pass them as the second argument");
    }

    public ParticleOptions of(Identifier particleId, Value options) {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(particleId)
                .orElseThrow(() -> new ValueConversionException(ParticleOptions.class, "registered particle id",
                        particleId, "particle not found: " + particleId));
        JsonElement json = JsonObjectAdapter.convertValueToJson(options);
        // MapCodec#parse(DynamicOps, T) exists only in some DFU versions; decode(ops, MapLike) is the abstract
        // core method of MapCodec and is available in every version, so turn the JsonElement into a
        // MapLike through ops.getMap first
        var map = JsonOps.INSTANCE.getMap(json)
                .getOrThrow(failure -> new ValueConversionException(ParticleOptions.class,
                        "particle options object for '" + particleId + "'", options, failure));
        return type.codec().decode(JsonOps.INSTANCE, map)
                .getOrThrow(failure -> new ValueConversionException(ParticleOptions.class,
                        "particle options for '" + particleId + "'", options, failure));
    }
}
