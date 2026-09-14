package com.postmalloy.aeroweather.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.postmalloy.aeroweather.client.ClientActiveWindmills;
import com.postmalloy.aeroweather.integration.aeronautics.SubLevelFrames;
import com.postmalloy.aeroweather.integration.create.CreateWindmillWind;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Makes Create windmill bearings respond to AeroWeather's wind — the only class
 * in the mod that touches Create at all.
 * <p>
 * <b>Deliberately free of Create types.</b> Create stays a {@code localRuntime}
 * dependency, never a compile one: the target is named by string, the one
 * Create method needed ({@code updateGeneratedRotation()}) has a Create-free
 * {@code ()V} descriptor, and position/facing come from vanilla
 * {@link BlockEntity} via the standard {@code (BlockEntity) (Object) this}
 * cast. Compiling against {@code WindmillBearingBlockEntity} directly would
 * drag catnip/registrate/ponder/flywheel onto the compile classpath for no
 * benefit. Do not "tidy" this into typed references.
 * <p>
 * When Create isn't installed this mixin is never registered at all —
 * {@code AeroWeatherMixinPlugin} withholds it, so the missing target class is
 * never even resolved.
 */
@Mixin(targets = "com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity", remap = false)
public abstract class WindmillBearingBlockEntityMixin {
    // Only members declared on WindmillBearingBlockEntity ITSELF may be shadowed. Mixin
    // resolves @Shadow fields against the target class alone, not its superclasses - an
    // earlier version shadowed `running` (declared on MechanicalBearingBlockEntity) and
    // crashed at class-transform time with "@Shadow field running was not located in the
    // target class". Both members below are declared directly on the target.
    @Shadow
    public abstract void updateGeneratedRotation();

    @Shadow
    public abstract float getGeneratedSpeed();

    /**
     * Recomputed every tick on both sides; read back by
     * {@link #aeroweather$applyWindToGeneratedSpeed}. It's quantized (see
     * {@code WindmillWindResponse.QUANTIZATION_STEP}), which is what keeps the
     * expensive push below rare.
     */
    @Unique
    private float aeroweather$windFactor = 1.0f;

    /**
     * The last generated speed actually pushed into Create's kinetic network, so a
     * continuously drifting wind only triggers a rebuild when the value Create
     * itself consumes genuinely moves. Comparing the speed rather than the factor
     * also means an unassembled bearing (generated speed pinned at 0) never pushes.
     */
    @Unique
    private float aeroweather$lastPushedSpeed;

    /**
     * Scales the sail-derived speed by the current wind factor.
     * <p>
     * This targets the {@code getAngleSpeedDirection()} call inside
     * {@code getGeneratedSpeed()} rather than that method's return value, and
     * the distinction matters: {@code getGeneratedSpeed()} returns a cached
     * {@code lastGeneratedSpeed} early when the contraption entity is detached,
     * and {@code updateGeneratedRotation()} fills that cache from
     * {@code getGeneratedSpeed()} itself — already scaled. Scaling at RETURN
     * would therefore compound the factor on every update while a windmill sat
     * detached. Modifying the direction term instead only ever touches the
     * live sail-count branch.
     */
    @ModifyExpressionValue(
            method = "getGeneratedSpeed",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/contraptions/bearing/WindmillBearingBlockEntity;getAngleSpeedDirection()F"))
    private float aeroweather$applyWindToGeneratedSpeed(float original) {
        return original * this.aeroweather$windFactor;
    }

    /**
     * HEAD rather than TAIL because {@code tick()} returns early in several
     * places. Pushing the new speed into the kinetic network is server-only and
     * gated on the generated speed actually changing — Create's
     * {@code updateGeneratedRotation()} detaches and reattaches the whole
     * network and resyncs the block entity, so calling it per tick would be
     * expensive on any sizeable build.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void aeroweather$updateWindFactor(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null) {
            return;
        }

        this.aeroweather$windFactor = CreateWindmillWind.speedMultiplier(level, self.getBlockPos(), self.getBlockState());

        // Reads back through the injector above, so this is the wind-scaled value Create
        // will actually generate - and it stays 0 for a bearing that isn't running, which
        // is what keeps unassembled windmills out of both branches below.
        float generatedSpeed = this.getGeneratedSpeed();

        if (level.isClientSide) {
            // Feeds the particle spawner's "only near wind-affected objects" option. Sable
            // contraptions have to be broadcast from the server for this; windmills tick
            // client-side already, so they can just report themselves. Reported at its world
            // position: on a Sable ship the bearing's own BlockPos is a far-away plot coordinate.
            if (generatedSpeed != 0.0f) {
                BlockPos pos = self.getBlockPos();
                ClientActiveWindmills.report(level.dimension().location(), pos,
                        SubLevelFrames.worldPositionOf(level, pos), level.getGameTime());
            }
            return;
        }

        if (generatedSpeed != this.aeroweather$lastPushedSpeed) {
            this.aeroweather$lastPushedSpeed = generatedSpeed;
            this.updateGeneratedRotation();
        }
    }
}
