package com.postmalloy.aeroweather.client.sound;

import org.jetbrains.annotations.Nullable;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherClientConfig;
import com.postmalloy.aeroweather.registry.AeroWeatherSounds;
import com.postmalloy.aeroweather.wind.LocalWind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Two looping wind layers whose volumes follow the wind where the player is
 * standing, easing rather than jumping.
 * <p>
 * The layers overlap on purpose. The light one fades in first and stays up; the
 * heavy one is laid <em>over</em> it once the wind gets strong, so strong wind
 * sounds like light wind plus something heavier rather than a different sound
 * switched on in its place. Each layer has its own start and full strength in
 * the config, and the defaults (10&rarr;50 and 50&rarr;100) are what make them
 * overlap across the top half of the range.
 * <p>
 * Volume eases toward its target by a fixed fraction per tick, the same "ease
 * toward a target, don't snap" idiom {@code WindState} uses to ease its weather band
 * and {@code AmbientWindDrift} for particles. Wind strength can jump — a gust,
 * a command, walking into a sheltered biome — and the sound should not.
 */
@EventBusSubscriber(modid = AeroWeather.MODID, value = Dist.CLIENT)
public final class WindSoundManager {
    /** Below this the loop is stopped outright rather than left running inaudibly, freeing the channel. */
    private static final float SILENCE_THRESHOLD = 0.001f;

    private static WindLoopSoundInstance light;
    private static WindLoopSoundInstance heavy;

    private WindSoundManager() {
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            // Paused: leave the instances alone. The sound engine pauses them with
            // everything else, and stopping here would restart the loops on unpause.
            return;
        }

        float strength = localStrength(level, player);
        boolean enabled = AeroWeatherClientConfig.WIND_SOUNDS_ENABLED.get();
        float master = enabled ? (float) AeroWeatherClientConfig.WIND_SOUND_VOLUME.getAsDouble() : 0.0f;

        light = update(light, AeroWeatherSounds.WIND_LIGHT.get(), master * rampedVolume(strength,
                AeroWeatherClientConfig.WIND_SOUND_LIGHT_START_STRENGTH.getAsDouble(),
                AeroWeatherClientConfig.WIND_SOUND_LIGHT_FULL_STRENGTH.getAsDouble()));
        heavy = update(heavy, AeroWeatherSounds.WIND_HEAVY.get(), master * rampedVolume(strength,
                AeroWeatherClientConfig.WIND_SOUND_HEAVY_START_STRENGTH.getAsDouble(),
                AeroWeatherClientConfig.WIND_SOUND_HEAVY_FULL_STRENGTH.getAsDouble()));
    }

    /**
     * Drops both loops on disconnect, so wind from the world you just left isn't
     * still blowing on the title screen or in the next one.
     */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        light = stop(light, soundManager);
        heavy = stop(heavy, soundManager);
    }

    /**
     * Wind at the player's own position, so the sound answers to the biome and
     * altitude they're actually in. Null before wind has synced for this
     * dimension, which reads as calm and fades the loops out.
     */
    private static float localStrength(ClientLevel level, LocalPlayer player) {
        LocalWind.Sample sample = LocalWind.at(level, player.blockPosition());
        return sample == null ? 0.0f : sample.adjustedStrength();
    }

    /** 0 below {@code start}, 1 at and above {@code full}, linear between. */
    private static float rampedVolume(float strength, double start, double full) {
        if (strength <= start) {
            return 0.0f;
        }
        if (full <= start) {
            return 1.0f;
        }
        return (float) Math.min(1.0, (strength - start) / (full - start));
    }

    private static WindLoopSoundInstance update(@Nullable WindLoopSoundInstance layer, SoundEvent event,
            float targetVolume) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();

        if (layer == null) {
            if (targetVolume <= SILENCE_THRESHOLD) {
                return null;
            }
            layer = new WindLoopSoundInstance(event);
            soundManager.play(layer);
        }

        float eased = ease(layer.getVolume(), targetVolume);
        if (eased <= SILENCE_THRESHOLD && targetVolume <= SILENCE_THRESHOLD) {
            return stop(layer, soundManager);
        }

        // The engine can drop a sound on its own - a resource reload, or the channel
        // being taken. Rebuild next tick rather than silently going quiet forever.
        if (!soundManager.isActive(layer)) {
            return null;
        }

        layer.setVolume(eased);
        return layer;
    }

    private static float ease(float current, float target) {
        float factor = (float) AeroWeatherClientConfig.WIND_SOUND_FADE_RATE.getAsDouble();
        float next = current + (target - current) * factor;
        // Land exactly on the target rather than approaching it forever, so a loop
        // that should be silent actually reaches the stop threshold.
        return Math.abs(target - next) < SILENCE_THRESHOLD ? target : next;
    }

    private static @Nullable WindLoopSoundInstance stop(@Nullable WindLoopSoundInstance layer,
            SoundManager soundManager) {
        if (layer != null) {
            layer.fadeOut();
            soundManager.stop(layer);
        }
        return null;
    }
}
