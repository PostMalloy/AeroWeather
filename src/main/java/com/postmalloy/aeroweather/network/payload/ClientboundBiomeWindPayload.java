package com.postmalloy.aeroweather.network.payload;

import java.util.HashMap;
import java.util.Map;

import com.postmalloy.aeroweather.AeroWeather;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's per-biome wind strengths and its flow-map seed, sent once per
 * join (see M14 in CLAUDE.md).
 * <p>
 * Unlike {@link ClientboundWindSyncPayload}, which carries state that changes
 * constantly, this is a fixed table sent rarely. It exists because both sides
 * compute per-position wind independently — the client needs the same numbers
 * the server used, or windmill visuals and weather particles would disagree with
 * the kinetics the server actually simulates. Deriving it separately on each
 * side from a common config would break the moment an admin edited the server's.
 * <p>
 * The seed is here because the client cannot derive it: the world seed isn't
 * sent to clients, and {@code BiomeManager}'s synced zoom seed has no accessor.
 */
public record ClientboundBiomeWindPayload(long flowSeed, Map<ResourceLocation, Float> factors) implements CustomPacketPayload {
    /** Generous, but bounded: a large modpack has a few hundred biomes, never tens of thousands. */
    private static final int MAX_BIOMES = 16384;

    public static final CustomPacketPayload.Type<ClientboundBiomeWindPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AeroWeather.MODID, "biome_wind"));

    public static final StreamCodec<ByteBuf, ClientboundBiomeWindPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ClientboundBiomeWindPayload::flowSeed,
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.FLOAT, MAX_BIOMES),
            ClientboundBiomeWindPayload::factors,
            ClientboundBiomeWindPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
