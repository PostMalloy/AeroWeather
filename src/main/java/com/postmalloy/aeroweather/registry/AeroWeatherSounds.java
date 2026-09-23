package com.postmalloy.aeroweather.registry;

import com.postmalloy.aeroweather.AeroWeather;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The two looping wind layers. Both are ambience played at the listener rather
 * than at a position, so their range never matters — hence
 * {@code createVariableRangeEvent}.
 */
public final class AeroWeatherSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AeroWeather.MODID);

    /** {@code sounds/ambient/wind1.ogg} — the lighter layer, audible across most of the range. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_LIGHT = register("wind1");

    /** {@code sounds/ambient/wind2.ogg} — the heavier layer, laid over the light one in strong wind. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND_HEAVY = register("wind2");

    private AeroWeatherSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AeroWeather.MODID, name)));
    }
}
