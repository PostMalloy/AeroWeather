package com.postmalloy.aeroweather.item;

import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.network.WindSync;
import com.postmalloy.aeroweather.registry.AeroWeatherParticles;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindOverride;
import com.postmalloy.aeroweather.wind.WindSavedData;
import com.postmalloy.aeroweather.wind.WindState;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * The breeze maker: an iron sword in every respect but durability, that also
 * summons a breeze. Right-clicking the air spends one durability, blows a burst
 * of wind and gust particles around the player, pushes nearby mobs and other
 * players away, and sets the wind to blow the way they're facing at {@code breezeMakerStrength}
 * for {@code breezeMakerDurationSeconds} - or until the next use replaces it.
 * <p>
 * The breeze is a timed {@link WindOverride} (see
 * {@link WindState#applyTimedOverride}): natural wind freezes underneath it and
 * resumes when it lapses, just as after {@code /aeroweather wind reset}. An
 * operator's indefinite override outranks it, so while one is active the item
 * does nothing and costs no durability.
 */
public class BreezeMakerItem extends SwordItem {
    /**
     * {@link Tiers#IRON} with 60 uses. A custom tier is the only way to change the
     * durability: {@code TieredItem}'s constructor overwrites whatever the
     * properties set with {@code tier.getUses()}.
     */
    public static final Tier TIER = new SimpleTier(Tiers.IRON.getIncorrectBlocksForDrops(), 60, Tiers.IRON.getSpeed(),
            Tiers.IRON.getAttackDamageBonus(), Tiers.IRON.getEnchantmentValue(), Tiers.IRON::getRepairIngredient);

    /** Stops a held right-click, which re-fires every 4 ticks, from draining durability. */
    private static final int COOLDOWN_TICKS = 20;

    private static final int WIND_PARTICLES = 24;
    private static final int GUST_PARTICLES = 12;
    /** Blocks per tick: a shade faster than wind particles at full natural strength, so the burst reads as a gust. */
    private static final double BURST_SPEED = 0.45;
    private static final float BURST_SPREAD_RADIANS = (float) Math.toRadians(30.0);

    public BreezeMakerItem(Properties properties) {
        // The same attack damage and speed modifiers as vanilla's iron sword.
        super(TIER, properties.attributes(SwordItem.createAttributes(TIER, 3, -2.4F)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Only into open air: a click on a block that doesn't use it also ends up here.
        if (getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() != HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!summonBreeze(serverLevel, player)) {
                return InteractionResultHolder.fail(stack);
            }
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Starts the breeze and its burst; returns false, changing nothing, if an operator has pinned the wind. */
    private static boolean summonBreeze(ServerLevel level, Player player) {
        WindSavedData savedData = WindSavedData.get(level);
        WindState wind = savedData.wind();
        if (wind.isOverridden() && !wind.isTimedOverride()) {
            player.displayClientMessage(Component.translatable("item.aeroweather.breeze_maker.pinned"), true);
            return false;
        }

        // The wind blows the way the player faces, so it comes FROM straight behind them.
        Vec3 facing = Vec3.directionFromRotation(0.0f, player.getYRot());
        float fromBearing = WindDirection.bearingOf(facing.reverse());
        float strength = (float) AeroWeatherCommonConfig.BREEZE_MAKER_STRENGTH.getAsDouble();
        long durationTicks = AeroWeatherCommonConfig.BREEZE_MAKER_DURATION_SECONDS.get() * 20L;
        wind.applyTimedOverride(new WindOverride(fromBearing, strength), level.getGameTime() + durationTicks);
        savedData.setDirty();
        WindSync.forceSync(level, savedData);

        burst(level, player, facing);
        pushNearby(level, player);
        // Subtitled "Breeze whirs" (entity.breeze.idle_ground). BREEZE_WHIRL is a different sound, "Breeze whirls".
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS);
        return true;
    }

    /**
     * A ring of wind and gust particles round the player, all blowing the new way
     * with a little spread. Sent from the server so everyone nearby sees it: a
     * particle packet with a count of 0 spawns exactly one particle at the given
     * spot with {@code (dx, dy, dz) * speed} as its velocity, from which
     * {@code WindStreakParticle} takes its orientation and lifetime.
     */
    private static void burst(ServerLevel level, Player player, Vec3 facing) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < WIND_PARTICLES + GUST_PARTICLES; i++) {
            SimpleParticleType type = i < WIND_PARTICLES ? AeroWeatherParticles.WIND_STREAK.get() : AeroWeatherParticles.WIND_GUST.get();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 1.0 + random.nextDouble() * 2.0;
            double x = player.getX() + Math.cos(angle) * radius;
            double y = player.getY() + 0.25 + random.nextDouble() * 1.75;
            double z = player.getZ() + Math.sin(angle) * radius;
            Vec3 drift = facing.yRot((random.nextFloat() - 0.5f) * BURST_SPREAD_RADIANS);
            level.sendParticles(type, x, y, z, 0, drift.x, 0.0, drift.z, BURST_SPEED);
        }
    }

    /**
     * Blows every mob and every other player within {@code breezeMakerPushRadius}
     * straight away from the user. Uses vanilla knockback, so knockback resistance
     * and NeoForge's knockback event still apply.
     */
    private static void pushNearby(ServerLevel level, Player user) {
        double radius = AeroWeatherCommonConfig.BREEZE_MAKER_PUSH_RADIUS.getAsDouble();
        double strength = AeroWeatherCommonConfig.BREEZE_MAKER_PUSH_STRENGTH.getAsDouble();
        if (radius <= 0.0 || strength <= 0.0) {
            return;
        }
        double radiusSq = radius * radius;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(radius),
                target -> target != user && isPushable(target) && target.distanceToSqr(user) <= radiusSq)) {
            // knockback pushes *against* the (x, z) it's given, so the vector toward the user blows the target away.
            target.knockback(strength, user.getX() - target.getX(), user.getZ() - target.getZ());
            if (target instanceof Player) {
                // A player's client owns their movement, so the server only sends them this new
                // velocity when hurtMarked is set.
                target.hurtMarked = true;
            }
        }
    }

    /**
     * Mobs and players, but not armor stands, and - as vanilla explosions do -
     * not spectators or creative players in flight.
     */
    private static boolean isPushable(LivingEntity target) {
        if (target instanceof Player player) {
            return !player.isSpectator() && !(player.isCreative() && player.getAbilities().flying);
        }
        return target instanceof Mob;
    }
}
