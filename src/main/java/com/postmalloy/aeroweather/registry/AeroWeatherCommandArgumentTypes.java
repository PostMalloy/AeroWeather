package com.postmalloy.aeroweather.registry;

import java.util.function.Supplier;

import com.postmalloy.aeroweather.AeroWeather;
import com.postmalloy.aeroweather.command.DirectionArgument;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers AeroWeather's custom Brigadier argument types so the command tree can sync to clients. */
public final class AeroWeatherCommandArgumentTypes {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, AeroWeather.MODID);

    public static final Supplier<SingletonArgumentInfo<DirectionArgument>> DIRECTION = COMMAND_ARGUMENT_TYPES.register("direction",
            () -> ArgumentTypeInfos.registerByClass(DirectionArgument.class, SingletonArgumentInfo.contextFree(DirectionArgument::direction)));

    private AeroWeatherCommandArgumentTypes() {
    }
}
