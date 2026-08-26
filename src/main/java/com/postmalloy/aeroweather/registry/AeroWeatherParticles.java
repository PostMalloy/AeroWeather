package com.postmalloy.aeroweather.registry;

import com.postmalloy.aeroweather.AeroWeather;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeroWeatherParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, AeroWeather.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_STREAK =
            PARTICLE_TYPES.register("wind_streak", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_GUST =
            PARTICLE_TYPES.register("wind_gust", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_LOOP =
            PARTICLE_TYPES.register("wind_loop", () -> new SimpleParticleType(false));

    private AeroWeatherParticles() {
    }
}
