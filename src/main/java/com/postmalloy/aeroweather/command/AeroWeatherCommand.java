package com.postmalloy.aeroweather.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.config.AeroWeatherCommonConfig;
import com.postmalloy.aeroweather.network.WindSync;
import com.postmalloy.aeroweather.wind.WindDirection;
import com.postmalloy.aeroweather.wind.WindHeightScaling;
import com.postmalloy.aeroweather.wind.WindOverride;
import com.postmalloy.aeroweather.wind.WindSavedData;
import com.postmalloy.aeroweather.wind.WindState;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /aeroweather wind <direction> <strength> | reset | info
 * See CLAUDE.md's "Command reference" section. Operates on the command
 * source's current dimension — no dimension argument, kept simple per
 * scope. Requires operator permission (level 2).
 */
@EventBusSubscriber(modid = AeroWeather.MODID)
public final class AeroWeatherCommand {
    private AeroWeatherCommand() {
    }

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(build());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("aeroweather")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("wind")
                        .then(Commands.argument("direction", DirectionArgument.direction())
                                .then(Commands.argument("strength", IntegerArgumentType.integer(0, 100))
                                        .executes(AeroWeatherCommand::setWind)))
                        .then(Commands.literal("reset").executes(AeroWeatherCommand::resetWind))
                        .then(Commands.literal("info").executes(AeroWeatherCommand::showInfo)));
    }

    private static int setWind(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        float directionDeg = DirectionArgument.getDirection(context, "direction");
        int strength = IntegerArgumentType.getInteger(context, "strength");

        WindSavedData savedData = WindSavedData.get(level);
        savedData.wind().applyOverride(new WindOverride(directionDeg, strength));
        savedData.setDirty();
        WindSync.forceSync(level, savedData);

        WindDirection nearest = WindDirection.nearest(directionDeg);
        context.getSource().sendSuccess(() -> Component.translatable("commands.aeroweather.wind.set", nearest.displayName(), strength), true);
        return strength;
    }

    private static int resetWind(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();

        WindSavedData savedData = WindSavedData.get(level);
        savedData.wind().clearOverride();
        savedData.setDirty();
        WindSync.forceSync(level, savedData);

        context.getSource().sendSuccess(() -> Component.translatable("commands.aeroweather.wind.reset"), true);
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        WindState wind = WindSavedData.get(level).wind();

        WindDirection nearest = WindDirection.nearest(wind.directionDeg());
        String sourceKey = wind.isOverridden() ? "commands.aeroweather.wind.info.overridden" : "commands.aeroweather.wind.info.natural";
        int strengthRounded = Math.round(wind.strength());

        // getPosition() works for any command source (player, console, command block), not just players,
        // so this degrades gracefully rather than requiring the source to be a ServerPlayer.
        double y = context.getSource().getPosition().y;
        float adjustedStrength = WindHeightScaling.scale(wind.strength(), y, level.getSeaLevel(),
                AeroWeatherCommonConfig.HEIGHT_REFERENCE_ABOVE_SEA_LEVEL.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_EXPONENT.getAsDouble(),
                AeroWeatherCommonConfig.HEIGHT_MAX_MULTIPLIER.getAsDouble());
        int adjustedStrengthRounded = Math.round(adjustedStrength);

        context.getSource().sendSuccess(() -> Component.translatable("commands.aeroweather.wind.info",
                nearest.displayName(), Math.round(wind.directionDeg()), strengthRounded, Component.translatable(sourceKey),
                adjustedStrengthRounded), false);
        return strengthRounded;
    }
}
