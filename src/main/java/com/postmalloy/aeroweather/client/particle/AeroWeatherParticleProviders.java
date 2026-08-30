package com.postmalloy.aeroweather.client.particle;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.registry.AeroWeatherParticles;

import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class AeroWeatherParticleProviders {
    private AeroWeatherParticleProviders() {
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AeroWeatherParticles.WIND_STREAK.get(), WindStreakParticle.Provider::new);
        event.registerSpriteSet(AeroWeatherParticles.WIND_GUST.get(), WindStreakParticle.Provider::new);
        event.registerSpriteSet(AeroWeatherParticles.WIND_LOOP.get(), WindStreakParticle.Provider::new);

        // Override vanilla's own providers for these two particle types - see
        // WindDriftingCampfireSmokeParticle/WindDriftingCherryParticle for why that's safe.
        event.registerSpriteSet(ParticleTypes.CAMPFIRE_COSY_SMOKE, WindDriftingCampfireSmokeParticle.CosyProvider::new);
        event.registerSpriteSet(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, WindDriftingCampfireSmokeParticle.SignalProvider::new);
        event.registerSpriteSet(ParticleTypes.CHERRY_LEAVES, WindDriftingCherryParticle.Provider::new);
    }
}
