package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import dev.ryanhcode.sable.sublevel.SubLevel;

/**
 * Reaches the three swivel bearing internals the wind bearing needs, none of
 * which Create Simulated exposes: the servo's target angle is a private field
 * with only a getter (not even their ComputerCraft peripheral can write it), and
 * the two sub-level lookups are private methods.
 * <p>
 * Applied to Simulated's own class, so a {@code WindBearingBlockEntity} — which
 * extends it — can simply be cast to this interface. Withheld entirely by
 * {@code AeroWeatherMixinPlugin} when Simulated isn't installed.
 * <p>
 * These are internals, not API: a Simulated update that renames any of them
 * breaks this at class-transform time. That's an accepted cost of building on
 * their bearing rather than reimplementing assembly and cross-contraption
 * kinetics from scratch, and it can only ever break where Simulated is present.
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity", remap = false)
public interface SwivelBearingInternals {
    @Accessor("targetAngleDegrees")
    void aeroweather$setTargetAngleDegrees(double targetAngleDegrees);

    @Invoker("getAttachedSubLevel")
    SubLevel aeroweather$getAttachedSubLevel();

    @Invoker("getContainingSubLevel")
    SubLevel aeroweather$getContainingSubLevel();
}
