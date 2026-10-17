package com.postmalloy.aeroweather.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.postmalloy.aeroweather.integration.adchimneys.ChimneySmokeWind;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.phys.Vec3;

/**
 * Advanced Chimneys' smoke blows with AeroWeather's wind. See {@link ChimneySmokeWind}
 * for the velocity.
 * <p>
 * The smoke particle, {@code AbstractSmokeParticle.Instance}, carries a wind from
 * ForgeEndertech's own simulation, and its {@code tick()} reads it exactly twice, both in
 * one branch, taken when the puff has left the chimney and has open sky beside it:
 * {@code xd = wind.getMotion().x; zd = wind.getMotion().z;}. This injects right after the
 * second of those writes, which is the first write to {@code zd} in the method, and
 * overwrites both. Everything else about the smoke is untouched: how it rises, when it
 * counts as sheltered, its friction indoors, and its bounce off walls (which pauses the
 * wind for a few ticks via its own {@code ignoreWindDelay}, so it still does).
 * <p>
 * Extends the target's own superclass so the handler can reach {@code Particle}'s
 * protected {@code level}, position and velocity fields; the constructor is never run.
 * Names Advanced Chimneys only by string, like the other optional-mod mixins, and is
 * withheld by {@code AeroWeatherMixinPlugin} unless {@code adchimneys} is installed.
 * Verified against every NeoForge 1.21.1 release on Modrinth, 11.1.6.1 to 11.1.13.0:
 * the same two reads, and the wind write is the first {@code zd} write in each.
 * {@code require = 0}: their internals aren't API, and a change there should cost us
 * wind-blown smoke, not the game.
 */
@Mixin(targets = "com.endertech.minecraft.mods.adchimneys.particles.AbstractSmokeParticle$Instance", remap = false)
public abstract class AdvancedChimneysSmokeMixin extends TextureSheetParticle {
    private AdvancedChimneysSmokeMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "tick", require = 0, at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 0,
            target = "Lcom/endertech/minecraft/mods/adchimneys/particles/AbstractSmokeParticle$Instance;zd:D",
            shift = At.Shift.AFTER))
    private void aeroweather$blowWithWind(CallbackInfo ci) {
        Vec3 velocity = ChimneySmokeWind.velocity(this.level, this.x, this.y, this.z);
        if (velocity != null) {
            this.xd = velocity.x;
            this.zd = velocity.z;
        }
    }
}
