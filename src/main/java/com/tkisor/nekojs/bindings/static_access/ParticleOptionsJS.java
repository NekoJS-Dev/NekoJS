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
 * {@code ParticleOptions} 工厂 helper（绑定全局 {@code ParticleOptions}，成员委托
 * {@link ParticleOptions} 接口）。
 *
 * <p>无参粒子：{@code ParticleOptions.of('minecraft:cloud')} 直接返回粒子类型本身
 * （ParticleType 实现 ParticleOptions）。带参粒子：{@code ParticleOptions.of('minecraft:dust', {color: [1,0,0], size: 1})}——
 * options 走该粒子类型的 Codec 解析。
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
        return type.codec().parse(JsonOps.INSTANCE, json)
                .getOrThrow(failure -> new ValueConversionException(ParticleOptions.class,
                        "particle options for '" + particleId + "'", options, failure.message()));
    }
}
