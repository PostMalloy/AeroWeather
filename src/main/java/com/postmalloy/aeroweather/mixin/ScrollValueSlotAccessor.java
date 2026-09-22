package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

/**
 * Lets the wind bearing move an inherited value box to a different face position.
 * <p>
 * A {@code ScrollValueBehaviour} takes its {@link ValueBoxTransform} in its
 * constructor and keeps it in a package-private, non-final {@code slotPositioning}
 * field with only a getter. The wind bearing inherits Simulated's locking menu and
 * its transform along with it, and can't pass a different one without rebuilding the
 * behaviour — which isn't an option, since the swivel bearing keeps its own reference
 * to the behaviour it built and reads the locking mode back through it.
 * <p>
 * Withheld by {@code AeroWeatherMixinPlugin} when Simulated is absent: the wind
 * bearing is its only user, and Simulated is what drags Create in.
 */
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour",
        remap = false)
public interface ScrollValueSlotAccessor {
    @Accessor("slotPositioning")
    void aeroweather$setSlotPositioning(ValueBoxTransform slotPositioning);
}
