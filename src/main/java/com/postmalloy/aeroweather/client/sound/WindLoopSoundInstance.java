package com.postmalloy.aeroweather.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * One continuously looping wind layer whose volume {@link WindSoundManager}
 * drives from tick to tick.
 * <p>
 * Positioned <em>relative</em> to the listener at the origin with attenuation
 * off, so it sits with the player instead of somewhere in the world: wind is
 * everywhere around you, not a thing you can walk toward.
 * <p>
 * {@link #canStartSilent()} has to be true. {@code SoundEngine} refuses to start
 * an instance whose volume is 0 unless it says so, and these always start silent
 * — the whole point is that they fade up from nothing rather than snapping on.
 */
public class WindLoopSoundInstance extends AbstractTickableSoundInstance {
    public WindLoopSoundInstance(SoundEvent sound) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /**
     * Nothing to do per tick: the manager owns the volume and writes it directly.
     * The sound engine re-reads {@link #getVolume()} every tick for a tickable
     * instance, which is what makes that work.
     */
    @Override
    public void tick() {
    }

    /** Lets the manager end the loop; {@code stop()} is protected on the base class. */
    public void fadeOut() {
        this.stop();
    }
}
