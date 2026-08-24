package com.postmalloy.aeroweather.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import com.postmalloy.aeroweather.wind.WindDirection;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * Parses a wind direction as either a cardinal keyword ("north", "ne") or
 * a raw compass bearing in degrees, resolving to the bearing in both
 * cases. See {@link WindDirection} for the keyword table.
 */
public class DirectionArgument implements ArgumentType<Float> {
    private static final Collection<String> EXAMPLES = Arrays.asList("north", "ne", "270");
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_DIRECTION =
            new DynamicCommandExceptionType(keyword -> Component.translatable("argument.aeroweather.direction.invalid", keyword));

    private DirectionArgument() {
    }

    public static DirectionArgument direction() {
        return new DirectionArgument();
    }

    public static float getDirection(CommandContext<?> context, String name) {
        return context.getArgument(name, Float.class);
    }

    @Override
    public Float parse(StringReader reader) throws CommandSyntaxException {
        char next = reader.canRead() ? reader.peek() : ' ';
        if (Character.isDigit(next) || next == '-' || next == '.') {
            return WindDirection.normalizeDegrees(reader.readFloat());
        }

        String keyword = reader.readUnquotedString();
        return WindDirection.byKeyword(keyword)
                .map(WindDirection::degrees)
                .orElseThrow(() -> ERROR_UNKNOWN_DIRECTION.create(keyword));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(WindDirection.values()).map(direction -> direction.name().toLowerCase(Locale.ROOT)), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
